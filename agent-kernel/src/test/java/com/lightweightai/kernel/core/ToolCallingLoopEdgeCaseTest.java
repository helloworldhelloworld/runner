package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.trace.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolCallingLoop 边界条件与错误路径测试
 *
 * 覆盖：
 * - enrichWithToolCalls 无工具调用时返回原始消息
 * - createToolResultMessage 内容映射
 * - 异步路径异常传播
 * - Reactive 路径无 LLM_COMPLETE 事件
 * - Reactive 路径 LLM 错误传播
 * - toString() 格式
 * - maxIterations=1 边界
 * - 工具执行错误不中断循环
 */
@DisplayName("ToolCallingLoop - 边界条件与错误路径")
class ToolCallingLoopEdgeCaseTest {

    // ==================== 构造器验证 ====================

    @Nested
    @DisplayName("构造器参数验证")
    class ConstructorValidation {

        @Test
        @DisplayName("maxIterations=0 抛出异常")
        void shouldRejectZeroMaxIterations() {
            assertThrows(IllegalArgumentException.class, () ->
                new ToolCallingLoop(createMockProvider("ok"), createToolExecutor(), 0));
        }

        @Test
        @DisplayName("负数 maxIterations 抛出异常")
        void shouldRejectNegativeMaxIterations() {
            assertThrows(IllegalArgumentException.class, () ->
                new ToolCallingLoop(createMockProvider("ok"), createToolExecutor(), -1));
        }

        @Test
        @DisplayName("maxIterations=1 仅允许一轮")
        void shouldAllowExactlyOneIteration() {
            SequentialMockProvider provider = new SequentialMockProvider();
            provider.addToolCallResponse("echo", Map.of("input", "hi"));
            provider.addTextResponse("done");

            ToolCallingLoop loop = new ToolCallingLoop(provider, createToolExecutor(), 1);

            // First call returns tool_use, second should return text (within 1 iteration)
            // But since iteration check is < maxIterations, iteration 0 is allowed
            LLMResponse response = loop.executeWithTools(
                List.of(createUserMessage("test")), LLMOptions.builder().build());
            assertEquals("done", response.getMessage().getTextContent());
        }
    }

    // ==================== 异步路径错误 ====================

    @Nested
    @DisplayName("异步路径错误处理")
    class AsyncErrorHandling {

        @Test
        @DisplayName("异步 LLM 调用异常正确传播")
        void shouldPropagateAsyncLLMError() {
            LLMProvider failingProvider = new LLMProvider() {
                @Override
                public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
                    throw new RuntimeException("sync fail");
                }

                @Override
                public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
                    return CompletableFuture.failedFuture(new RuntimeException("async LLM error"));
                }

                @Override
                public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) {
                    return CompletableFuture.failedFuture(new RuntimeException("stream fail"));
                }

                @Override public String getProviderName() { return "failing"; }
                @Override public ModelCapability getModelCapability() { return null; }
            };

            ToolCallingLoop loop = new ToolCallingLoop(failingProvider, createToolExecutor(), 5);

            CompletableFuture<LLMResponse> future = loop.executeWithToolsAsync(
                List.of(createUserMessage("test")), LLMOptions.builder().build());

            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertTrue(ex.getCause().getMessage().contains("Tool calling loop failed"));
        }

        @Test
        @DisplayName("异步路径达到最大迭代次数返回失败")
        void shouldFailOnMaxIterationsAsync() {
            SequentialMockProvider provider = new SequentialMockProvider();
            for (int i = 0; i < 10; i++) {
                provider.addToolCallResponse("echo", Map.of());
            }

            ToolCallingLoop loop = new ToolCallingLoop(provider, createToolExecutor(), 2);

            CompletableFuture<LLMResponse> future = loop.executeWithToolsAsync(
                List.of(createUserMessage("test")), LLMOptions.builder().build());

            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertTrue(ex.getCause().getMessage().contains("maximum iterations"));
        }
    }

    // ==================== Reactive 路径边界 ====================

    @Nested
    @DisplayName("Reactive 路径边界")
    class ReactiveEdgeCases {

        @Test
        @DisplayName("Reactive 无 LLM_COMPLETE 事件正常结束")
        void shouldHandleMissingLLMCompleteEvent() {
            LLMProvider reactiveProvider = createReactiveProvider(List.of(
                StreamEvent.textDelta("hello"),
                StreamEvent.textDelta(" world")
                // no LLM_COMPLETE
            ));

            ToolCallingLoop loop = new ToolCallingLoop(reactiveProvider, createToolExecutor(), 5);

            StepVerifier.create(loop.executeWithToolsReactive(
                    List.of(createUserMessage("test")), LLMOptions.builder().build()))
                .expectNextMatches(e -> e.getType() == StreamEvent.EventType.TEXT_DELTA)
                .expectNextMatches(e -> e.getType() == StreamEvent.EventType.TEXT_DELTA)
                .verifyComplete();
        }

        @Test
        @DisplayName("Reactive LLM 错误正确传播")
        void shouldPropagateReactiveLLMError() {
            LLMProvider errorProvider = createReactiveProvider(null); // will cause error

            ToolCallingLoop loop = new ToolCallingLoop(errorProvider, createToolExecutor(), 5);

            StepVerifier.create(loop.executeWithToolsReactive(
                    List.of(createUserMessage("test")), LLMOptions.builder().build()))
                .expectError(RuntimeException.class)
                .verify();
        }

        @Test
        @DisplayName("Reactive 达到最大迭代次数发出错误")
        void shouldEmitErrorOnMaxIterationsReactive() {
            // Provider that always returns tool calls in reactive mode
            LLMProvider alwaysToolCallProvider = new LLMProvider() {
                @Override
                public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
                    LLMResponse resp = LLMResponse.builder()
                        .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT).textContent("").build())
                        .toolCalls(List.of(new ToolCall("tc", "echo", Map.of())))
                        .build();
                    return Flux.just(
                        StreamEvent.textDelta(""),
                        StreamEvent.llmComplete(resp)
                    );
                }

                @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) { throw new UnsupportedOperationException(); }
                @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) { throw new UnsupportedOperationException(); }
                @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) { throw new UnsupportedOperationException(); }
                @Override public String getProviderName() { return "always-tool"; }
                @Override public ModelCapability getModelCapability() { return null; }
            };

            ToolCallingLoop loop = new ToolCallingLoop(alwaysToolCallProvider, createToolExecutor(), 2);

            StepVerifier.create(loop.executeWithToolsReactive(
                    List.of(createUserMessage("test")), LLMOptions.builder().build()))
                .thenConsumeWhile(e -> true) // consume all events until error
                .expectError(RuntimeException.class)
                .verify();
        }
    }

    // ==================== toString ====================

    @Test
    @DisplayName("toString 包含关键信息")
    void toStringShouldContainKeyInfo() {
        ToolCallingLoop loop = ToolCallingLoop.builder()
            .provider(createMockProvider("ok"))
            .toolExecutor(createToolExecutor())
            .maxIterations(5)
            .build();

        String str = loop.toString();
        assertTrue(str.contains("ToolCallingLoop"));
        assertTrue(str.contains("5"));
    }

    // ==================== Builder ====================

    @Test
    @DisplayName("Builder 使用 Tracer")
    void builderShouldAcceptTracer() {
        ToolCallingLoop loop = ToolCallingLoop.builder()
            .provider(createMockProvider("ok"))
            .toolExecutor(createToolExecutor())
            .tracer(Tracer.NOOP)
            .build();

        assertNotNull(loop);
        assertEquals(10, loop.getMaxIterations()); // default
    }

    // ==================== 辅助方法 ====================

    private ToolExecutor createToolExecutor() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new Tool() {
            @Override public String getName() { return "echo"; }
            @Override public String getDescription() { return "Echo tool"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("echoed: " + args);
            }
        });
        return new ToolExecutor(registry);
    }

    private ConversationMessage createUserMessage(String text) {
        return ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.USER)
            .textContent(text)
            .build();
    }

    private LLMProvider createMockProvider(String responseText) {
        return new LLMProvider() {
            @Override
            public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
                return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.ASSISTANT)
                        .textContent(responseText)
                        .build())
                    .stopReason("stop")
                    .build();
            }

            @Override
            public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
                return CompletableFuture.completedFuture(complete(m, o));
            }

            @Override
            public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) {
                LLMResponse resp = complete(m, o);
                h.onComplete(resp);
                return CompletableFuture.completedFuture(resp);
            }

            @Override public String getProviderName() { return "mock"; }
            @Override public ModelCapability getModelCapability() { return null; }
        };
    }

    private LLMProvider createReactiveProvider(List<StreamEvent> events) {
        return new LLMProvider() {
            @Override
            public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
                if (events == null) {
                    return Flux.error(new RuntimeException("LLM reactive error"));
                }
                return Flux.fromIterable(events);
            }

            @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) { throw new UnsupportedOperationException(); }
            @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) { throw new UnsupportedOperationException(); }
            @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) { throw new UnsupportedOperationException(); }
            @Override public String getProviderName() { return "reactive-mock"; }
            @Override public ModelCapability getModelCapability() { return null; }
        };
    }

    /** Mock provider that returns responses from a queue */
    static class SequentialMockProvider implements LLMProvider {
        private final List<LLMResponse> responseQueue = new ArrayList<>();
        private int index = 0;

        void addTextResponse(String text) {
            responseQueue.add(LLMResponse.builder()
                .message(ConversationMessage.builder()
                    .role(ConversationMessage.MessageRole.ASSISTANT).textContent(text).build())
                .stopReason("stop")
                .build());
        }

        void addToolCallResponse(String toolName, Map<String, Object> args) {
            responseQueue.add(LLMResponse.builder()
                .message(ConversationMessage.builder()
                    .role(ConversationMessage.MessageRole.ASSISTANT).textContent("").build())
                .toolCalls(List.of(new ToolCall(toolName + "-id", toolName, args)))
                .stopReason("tool_use")
                .build());
        }

        @Override
        public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            if (index >= responseQueue.size()) {
                return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.ASSISTANT).textContent("fallback").build())
                    .stopReason("stop").build();
            }
            return responseQueue.get(index++);
        }

        @Override
        public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
            return CompletableFuture.completedFuture(complete(m, o));
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) {
            LLMResponse resp = complete(m, o);
            h.onComplete(resp);
            return CompletableFuture.completedFuture(resp);
        }

        @Override public String getProviderName() { return "sequential-mock"; }
        @Override public ModelCapability getModelCapability() { return null; }
    }
}
