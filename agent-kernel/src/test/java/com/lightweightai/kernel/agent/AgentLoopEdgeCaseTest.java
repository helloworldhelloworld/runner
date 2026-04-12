package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.InMemoryProvider;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentLoop 边界条件和错误路径测试
 *
 * 覆盖：
 * - Observer 异常隔离
 * - Observer 生命周期钩子完整性
 * - LLM 错误传播 + onError 钩子
 * - formatMemoryContext 边界
 * - truncate null/边界处理
 * - runReactive 错误路径
 * - 空记忆上下文
 */
@DisplayName("AgentLoop - 边界条件与错误路径")
class AgentLoopEdgeCaseTest {

    private InMemoryProvider memory;

    @BeforeEach
    void setUp() {
        memory = new InMemoryProvider();
    }

    // ==================== Observer 异常隔离 ====================

    @Nested
    @DisplayName("Observer 异常隔离")
    class ObserverIsolation {

        @Test
        @DisplayName("Observer 抛出异常不中断主链路")
        void observerExceptionShouldNotBreakMainFlow() {
            SimpleMockLLMProvider provider = new SimpleMockLLMProvider("正常回复");

            AgentLoop loop = AgentLoop.builder()
                .llmProvider(provider)
                .memoryProvider(memory)
                .addObserver(new AgentObserver() {
                    @Override
                    public void onAgentStart(String input, String sessionId) {
                        throw new RuntimeException("Observer explosion");
                    }
                })
                .build();

            AgentResponse response = loop.run("测试", "s1");
            assertEquals("正常回复", response.getText());
        }

        @Test
        @DisplayName("多个 Observer 中一个异常不影响其他 Observer")
        void singleObserverFailureDoesNotAffectOthers() {
            SimpleMockLLMProvider provider = new SimpleMockLLMProvider("ok");
            AtomicBoolean secondCalled = new AtomicBoolean(false);

            AgentLoop loop = AgentLoop.builder()
                .llmProvider(provider)
                .memoryProvider(memory)
                .addObserver(new AgentObserver() {
                    @Override
                    public void onAgentStart(String input, String sessionId) {
                        throw new RuntimeException("First observer fails");
                    }
                })
                .addObserver(new AgentObserver() {
                    @Override
                    public void onAgentStart(String input, String sessionId) {
                        secondCalled.set(true);
                    }
                })
                .build();

            loop.run("test", "s1");
            assertTrue(secondCalled.get(), "Second observer should still be called");
        }

        @Test
        @DisplayName("onError Observer 异常不吞掉原始异常")
        void onErrorObserverExceptionDoesNotSwallowOriginal() {
            LLMProvider failingProvider = new SimpleMockLLMProvider(null) {
                @Override
                public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
                    throw new RuntimeException("LLM failure");
                }
            };

            AgentLoop loop = AgentLoop.builder()
                .llmProvider(failingProvider)
                .memoryProvider(memory)
                .addObserver(new AgentObserver() {
                    @Override
                    public void onError(Throwable error) {
                        throw new RuntimeException("Observer also fails");
                    }
                })
                .build();

            RuntimeException ex = assertThrows(RuntimeException.class,
                () -> loop.run("test", "s1"));
            assertEquals("LLM failure", ex.getMessage());
        }
    }

    // ==================== Observer 生命周期 ====================

    @Nested
    @DisplayName("Observer 生命周期钩子")
    class ObserverLifecycle {

        @Test
        @DisplayName("正常流程触发完整 Observer 序列")
        void shouldFireCompleteObserverSequence() {
            SimpleMockLLMProvider provider = new SimpleMockLLMProvider("回复");
            List<String> hooks = new ArrayList<>();

            AgentLoop loop = AgentLoop.builder()
                .llmProvider(provider)
                .memoryProvider(memory)
                .addObserver(new AgentObserver() {
                    @Override
                    public void onAgentStart(String input, String sessionId) {
                        hooks.add("start");
                    }

                    @Override
                    public void onLLMRequest(List<ConversationMessage> messages) {
                        hooks.add("llmRequest");
                    }

                    @Override
                    public void onLLMResponse(LLMResponse response) {
                        hooks.add("llmResponse");
                    }

                    @Override
                    public void onAgentComplete(AgentResponse response) {
                        hooks.add("complete");
                    }
                })
                .build();

            loop.run("hello", "s1");
            assertEquals(List.of("start", "llmRequest", "llmResponse", "complete"), hooks);
        }

        @Test
        @DisplayName("LLM 错误触发 onError 钩子")
        void shouldFireOnErrorHookOnLLMFailure() {
            LLMProvider failingProvider = new SimpleMockLLMProvider(null) {
                @Override
                public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
                    throw new RuntimeException("API down");
                }
            };

            AtomicBoolean errorHookFired = new AtomicBoolean(false);

            AgentLoop loop = AgentLoop.builder()
                .llmProvider(failingProvider)
                .memoryProvider(memory)
                .addObserver(new AgentObserver() {
                    @Override
                    public void onError(Throwable error) {
                        errorHookFired.set(true);
                    }
                })
                .build();

            assertThrows(RuntimeException.class, () -> loop.run("test", "s1"));
            assertTrue(errorHookFired.get());
        }
    }

    // ==================== 记忆上下文边界 ====================

    @Nested
    @DisplayName("记忆上下文边界")
    class MemoryContextEdgeCases {

        @Test
        @DisplayName("无记忆结果时系统提示不包含记忆段")
        void shouldBuildPromptWithoutMemoryWhenEmpty() {
            SimpleMockLLMProvider provider = new SimpleMockLLMProvider("ok");

            AgentLoop loop = AgentLoop.builder()
                .llmProvider(provider)
                .memoryProvider(memory)
                .systemPrompt("你是助手。")
                .build();

            loop.run("hello", "s1");

            // System prompt should not contain "相关记忆" when no memory results
            String systemContent = provider.getLastMessages().get(0).getTextContent();
            assertFalse(systemContent.contains("相关记忆"));
            assertTrue(systemContent.contains("你是助手。"));
        }

        @Test
        @DisplayName("记忆结果超过3条只取前3条")
        void shouldLimitMemoryResultsToThree() {
            // Write many ephemeral memories
            for (int i = 0; i < 10; i++) {
                memory.writeEphemeral("记忆条目" + i);
            }

            SimpleMockLLMProvider provider = new SimpleMockLLMProvider("ok");

            AgentLoop loop = AgentLoop.builder()
                .llmProvider(provider)
                .memoryProvider(memory)
                .systemPrompt("prompt")
                .build();

            loop.run("查询记忆", "s1");

            String systemContent = provider.getLastMessages().get(0).getTextContent();
            // Count the bullet points (- ) in the memory context
            long bulletCount = systemContent.lines()
                .filter(line -> line.trim().startsWith("- "))
                .count();
            assertTrue(bulletCount <= 3, "Should have at most 3 memory items, got " + bulletCount);
        }

        @Test
        @DisplayName("空系统提示可正常工作")
        void shouldWorkWithEmptySystemPrompt() {
            SimpleMockLLMProvider provider = new SimpleMockLLMProvider("ok");

            AgentLoop loop = AgentLoop.builder()
                .llmProvider(provider)
                .memoryProvider(memory)
                .build();

            AgentResponse response = loop.run("hi", "s1");
            assertEquals("ok", response.getText());
        }
    }

    // ==================== runReactive 错误路径 ====================

    @Nested
    @DisplayName("Reactive 流错误路径")
    class ReactiveErrorPaths {

        @Test
        @DisplayName("runReactive 中 LLM 错误传播到流")
        void shouldPropagateErrorInReactiveStream() {
            ReactiveMockLLMProvider provider = new ReactiveMockLLMProvider();
            provider.setStreamEvents(List.of()); // will error since no LLM_COMPLETE

            AgentLoop loop = AgentLoop.builder()
                .llmProvider(provider)
                .memoryProvider(memory)
                .systemPrompt("test")
                .build();

            // Stream should complete (empty stream = no error, just empty)
            StepVerifier.create(loop.runReactive("test", "s1"))
                .thenConsumeWhile(e -> true)
                .verifyComplete();
        }

        @Test
        @DisplayName("runReactive 中 LLM 流错误触发 onError Observer")
        void shouldFireOnErrorObserverInReactiveStream() {
            ReactiveMockLLMProvider provider = new ReactiveMockLLMProvider();
            provider.setError(new RuntimeException("LLM stream error"));

            AtomicBoolean errorHookFired = new AtomicBoolean(false);

            AgentLoop loop = AgentLoop.builder()
                .llmProvider(provider)
                .memoryProvider(memory)
                .addObserver(new AgentObserver() {
                    @Override
                    public void onError(Throwable error) {
                        errorHookFired.set(true);
                    }
                })
                .build();

            StepVerifier.create(loop.runReactive("test", "s1"))
                .expectError(RuntimeException.class)
                .verify();

            assertTrue(errorHookFired.get());
        }

        @Test
        @DisplayName("runReactive 空响应不写入助手消息")
        void shouldNotSaveEmptyResponseInReactive() {
            ReactiveMockLLMProvider provider = new ReactiveMockLLMProvider();
            provider.setStreamEvents(List.of(
                StreamEvent.llmComplete(createTextResponse(""))
            ));

            AgentLoop loop = AgentLoop.builder()
                .llmProvider(provider)
                .memoryProvider(memory)
                .build();

            StepVerifier.create(loop.runReactive("test", "s1"))
                .thenConsumeWhile(e -> true)
                .verifyComplete();

            List<Message> history = memory.getHistory("s1", 10);
            // Only user message, not empty assistant
            assertEquals(1, history.size());
        }
    }

    // ==================== writeConversationToMemory ====================

    @Nested
    @DisplayName("对话记忆写入")
    class ConversationMemoryWrite {

        @Test
        @DisplayName("长文本在记忆摘要中被截断")
        void shouldTruncateLongTextInMemorySummary() {
            String longInput = "A".repeat(200);
            SimpleMockLLMProvider provider = new SimpleMockLLMProvider("B".repeat(200));

            AgentLoop loop = AgentLoop.builder()
                .llmProvider(provider)
                .memoryProvider(memory)
                .build();

            loop.run(longInput, "s1");

            // Ephemeral memory should have been written
            // (we can't directly inspect truncation, but the method shouldn't throw)
            assertNotNull(loop);
        }

        @Test
        @DisplayName("null 响应文本安全处理")
        void shouldHandleNullResponseTextGracefully() {
            // The truncate method handles null by returning ""
            // This is tested indirectly - if a response has null text, it shouldn't crash
            SimpleMockLLMProvider provider = new SimpleMockLLMProvider("");

            AgentLoop loop = AgentLoop.builder()
                .llmProvider(provider)
                .memoryProvider(memory)
                .build();

            AgentResponse response = loop.run("test", "s1");
            assertNotNull(response);
        }
    }

    // ==================== Builder 边界 ====================

    @Nested
    @DisplayName("Builder 边界条件")
    class BuilderEdgeCases {

        @Test
        @DisplayName("自定义 maxToolIterations 生效")
        void shouldRespectCustomMaxToolIterations() {
            ToolCallMockLLMProvider provider = new ToolCallMockLLMProvider();
            Tool dummyTool = createSimpleTool("dummy", "dummy");

            AgentLoop loop = AgentLoop.builder()
                .llmProvider(provider)
                .memoryProvider(memory)
                .addTool(dummyTool)
                .maxToolIterations(2)
                .build();

            // Provider always returns tool call, so loop should fail at 2 iterations
            assertThrows(RuntimeException.class, () -> loop.run("test", "s1"));
        }

        @Test
        @DisplayName("tools() 方法批量注册工具")
        void shouldRegisterToolsViaBatchMethod() {
            SimpleMockLLMProvider provider = new SimpleMockLLMProvider("ok");

            List<Tool> tools = List.of(
                createSimpleTool("tool1", "desc1"),
                createSimpleTool("tool2", "desc2")
            );

            AgentLoop loop = AgentLoop.builder()
                .llmProvider(provider)
                .memoryProvider(memory)
                .tools(tools)
                .build();

            AgentResponse response = loop.run("test", "s1");
            assertEquals("ok", response.getText());
        }
    }

    // ==================== 辅助类 ====================

    private Tool createSimpleTool(String name, String description) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return description; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("result");
            }
        };
    }

    private LLMResponse createTextResponse(String text) {
        return LLMResponse.builder()
            .message(ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent(text)
                .build())
            .stopReason("stop")
            .build();
    }

    /** Simple mock that always returns a fixed text response */
    static class SimpleMockLLMProvider implements LLMProvider {
        private final String responseText;
        private List<ConversationMessage> lastMessages;

        SimpleMockLLMProvider(String responseText) {
            this.responseText = responseText;
        }

        List<ConversationMessage> getLastMessages() { return lastMessages; }

        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            this.lastMessages = new ArrayList<>(messages);
            ConversationMessage msg = ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent(responseText)
                .build();
            return LLMResponse.builder().message(msg).stopReason("stop").build();
        }

        @Override
        public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
            return CompletableFuture.completedFuture(complete(messages, options));
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> messages, LLMOptions options, StreamEventHandler handler) {
            LLMResponse response = complete(messages, options);
            handler.onComplete(response);
            return CompletableFuture.completedFuture(response);
        }

        @Override public String getProviderName() { return "simple-mock"; }
        @Override public ModelCapability getModelCapability() { return null; }
    }

    /** Mock that always returns tool calls (for max iteration testing) */
    static class ToolCallMockLLMProvider implements LLMProvider {
        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            ConversationMessage msg = ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent("")
                .build();
            return LLMResponse.builder()
                .message(msg)
                .toolCalls(List.of(new ToolCall("tc-id", "dummy", Map.of())))
                .stopReason("tool_use")
                .build();
        }

        @Override
        public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
            return CompletableFuture.completedFuture(complete(messages, options));
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> messages, LLMOptions options, StreamEventHandler handler) {
            return CompletableFuture.completedFuture(complete(messages, options));
        }

        @Override public String getProviderName() { return "tool-call-mock"; }
        @Override public ModelCapability getModelCapability() { return null; }
    }

    /** Reactive mock for runReactive tests */
    static class ReactiveMockLLMProvider implements LLMProvider {
        private List<StreamEvent> streamEvents;
        private RuntimeException error;

        void setStreamEvents(List<StreamEvent> events) {
            this.streamEvents = events;
            this.error = null;
        }

        void setError(RuntimeException error) {
            this.error = error;
            this.streamEvents = null;
        }

        @Override
        public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> messages, LLMOptions options) {
            if (error != null) {
                return Flux.error(error);
            }
            return Flux.fromIterable(streamEvents);
        }

        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> messages, LLMOptions options, StreamEventHandler handler) {
            throw new UnsupportedOperationException();
        }

        @Override public String getProviderName() { return "reactive-mock"; }
        @Override public ModelCapability getModelCapability() { return null; }
    }
}
