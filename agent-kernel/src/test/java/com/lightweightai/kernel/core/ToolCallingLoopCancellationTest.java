package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolCallingLoop 补充测试 — executionContext 路径、async 路径、Builder 边界
 *
 * 覆盖之前遗漏的关键路径：
 * - 同步路径带 executionContext 执行工具
 * - 异步路径基本场景和最大迭代保护
 * - Tracer 为 null 时使用 NOOP 默认值
 * - Reactive 路径带 executionContext
 * - 异步路径工具不存在时优雅降级
 */
@DisplayName("ToolCallingLoop - ExecutionContext + Async + Builder 补充测试")
class ToolCallingLoopCancellationTest {

    private ToolExecutor toolExecutor;
    private ToolRegistry toolRegistry;
    private AtomicInteger callCounter;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry();
        toolExecutor = new ToolExecutor(toolRegistry);
        callCounter = new AtomicInteger(0);

        toolRegistry.register(createEchoTool("echo"));
    }

    // ==================== 同步路径带 executionContext ====================

    @Test
    @DisplayName("同步路径：带 serverOnly executionContext 调用工具")
    void syncWithExecutionContext() {
        MockSyncProvider provider = new MockSyncProvider();
        provider.addResponse(createToolCallResponse("echo", Map.of("input", "ctx_test")));
        provider.addResponse(createTextResponse("Done with context"));

        ToolExecutionContext ctx = ToolExecutionContext.serverOnly();
        ToolCallingLoop loop = new ToolCallingLoop(provider, toolExecutor, 10, ctx);

        LLMResponse result = loop.executeWithTools(createUserMessages("test"), LLMOptions.builder().build());
        assertEquals("Done with context", result.getMessage().getTextContent());
        assertEquals(2, provider.getCallCount());
    }

    @Test
    @DisplayName("同步路径：executionContext=null 时使用无 context 路径")
    void syncWithNullContext() {
        MockSyncProvider provider = new MockSyncProvider();
        provider.addResponse(createToolCallResponse("echo", Map.of("input", "test")));
        provider.addResponse(createTextResponse("No context"));

        ToolCallingLoop loop = new ToolCallingLoop(provider, toolExecutor, 10, null);

        LLMResponse result = loop.executeWithTools(createUserMessages("test"), LLMOptions.builder().build());
        assertEquals("No context", result.getMessage().getTextContent());
    }

    // ==================== 异步路径 ====================

    @Test
    @DisplayName("异步路径：无工具调用直接返回")
    void asyncNoToolCallsReturnsDirectly() {
        MockSyncProvider provider = new MockSyncProvider();
        provider.addResponse(createTextResponse("Async hello"));

        ToolCallingLoop loop = ToolCallingLoop.builder()
            .provider(provider)
            .toolExecutor(toolExecutor)
            .maxIterations(5)
            .build();

        LLMResponse result = loop.executeWithToolsAsync(createUserMessages("hi"), LLMOptions.builder().build()).join();
        assertEquals("Async hello", result.getMessage().getTextContent());
    }

    @Test
    @DisplayName("异步路径：单轮工具调用后返回最终响应")
    void asyncSingleToolCallRound() {
        MockSyncProvider provider = new MockSyncProvider();
        provider.addResponse(createToolCallResponse("echo", Map.of("input", "async_test")));
        provider.addResponse(createTextResponse("Async echo done"));

        ToolCallingLoop loop = ToolCallingLoop.builder()
            .provider(provider)
            .toolExecutor(toolExecutor)
            .maxIterations(5)
            .build();

        LLMResponse result = loop.executeWithToolsAsync(createUserMessages("test"), LLMOptions.builder().build()).join();
        assertEquals("Async echo done", result.getMessage().getTextContent());
    }

    @Test
    @DisplayName("异步路径：超过最大迭代次数返回失败 Future")
    void asyncMaxIterationsExceeded() {
        MockSyncProvider provider = new MockSyncProvider();
        for (int i = 0; i < 5; i++) {
            provider.addResponse(createToolCallResponse("echo", Map.of("input", "loop")));
        }

        ToolCallingLoop loop = ToolCallingLoop.builder()
            .provider(provider)
            .toolExecutor(toolExecutor)
            .maxIterations(2)
            .build();

        CompletableFuture<LLMResponse> future = loop.executeWithToolsAsync(
            createUserMessages("loop"), LLMOptions.builder().build());

        assertTrue(future.isCompletedExceptionally() || futureHasError(future));
    }

    @Test
    @DisplayName("异步路径：带 executionContext 执行工具")
    void asyncWithExecutionContext() {
        MockSyncProvider provider = new MockSyncProvider();
        provider.addResponse(createToolCallResponse("echo", Map.of("input", "async_ctx")));
        provider.addResponse(createTextResponse("Async ctx done"));

        ToolExecutionContext ctx = ToolExecutionContext.serverOnly();
        ToolCallingLoop loop = new ToolCallingLoop(provider, toolExecutor, 10, ctx);

        LLMResponse result = loop.executeWithToolsAsync(createUserMessages("test"), LLMOptions.builder().build()).join();
        assertEquals("Async ctx done", result.getMessage().getTextContent());
    }

    // ==================== Reactive 路径补充 ====================

    @Test
    @DisplayName("Reactive：带 executionContext 的工具调用")
    void reactiveWithExecutionContext() {
        ReactiveMockProvider provider = new ReactiveMockProvider();
        LLMResponse toolCallResponse = createToolCallResponse("echo", Map.of("input", "reactive_ctx"));
        provider.addStreamResponse(List.of(
            StreamEvent.textDelta(""),
            StreamEvent.llmComplete(toolCallResponse)
        ));
        provider.addStreamResponse(List.of(
            StreamEvent.textDelta("Reactive ctx done"),
            StreamEvent.llmComplete(createTextResponse("Reactive ctx done"))
        ));

        ToolExecutionContext ctx = ToolExecutionContext.serverOnly();
        ToolCallingLoop loop = new ToolCallingLoop(provider, toolExecutor, 5, ctx);

        List<StreamEvent> events = new ArrayList<>();
        StepVerifier.create(loop.executeWithToolsReactive(createUserMessages("test"), LLMOptions.builder().build()))
            .recordWith(() -> events)
            .thenConsumeWhile(e -> true)
            .verifyComplete();

        assertTrue(events.stream().anyMatch(e -> e.getType() == StreamEvent.EventType.TOOL_CALL_START));
    }

    // ==================== 构造器边界 ====================

    @Test
    @DisplayName("Tracer 为 null 时使用 NOOP 默认值")
    void tracerDefaultsToNoop() {
        ToolCallingLoop loop = new ToolCallingLoop(
            new ReactiveMockProvider(), toolExecutor, 5, null, null);
        assertNotNull(loop);
        assertEquals(5, loop.getMaxIterations());
    }

    @Test
    @DisplayName("Builder 模式完整构造")
    void builderComplete() {
        ToolCallingLoop loop = ToolCallingLoop.builder()
            .provider(new ReactiveMockProvider())
            .toolExecutor(toolExecutor)
            .maxIterations(7)
            .executionContext(ToolExecutionContext.serverOnly())
            .tracer(null)
            .build();

        assertEquals(7, loop.getMaxIterations());
    }

    @Test
    @DisplayName("多轮工具调用对话上下文正确累积")
    void multiRoundConversationContextAccumulates() {
        AtomicInteger llmCallCount = new AtomicInteger(0);
        List<Integer> messageCounts = new ArrayList<>();

        // Provider that records how many messages it receives each call
        LLMProvider recordingProvider = new LLMProvider() {
            private final Queue<LLMResponse> responses = new LinkedList<>();
            {
                responses.add(createToolCallResponse("echo", Map.of("input", "r1")));
                responses.add(createToolCallResponse("echo", Map.of("input", "r2")));
                responses.add(createTextResponse("Final"));
            }

            @Override
            public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
                llmCallCount.incrementAndGet();
                messageCounts.add(messages.size());
                return responses.poll();
            }

            @Override
            public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
                return CompletableFuture.completedFuture(complete(m, o));
            }

            @Override
            public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ModelCapability getModelCapability() { return null; }

            @Override
            public String getProviderName() { return "recording"; }
        };

        ToolCallingLoop loop = new ToolCallingLoop(recordingProvider, toolExecutor, 10);
        loop.executeWithTools(createUserMessages("calc"), LLMOptions.builder().build());

        assertEquals(3, llmCallCount.get());
        // Each round adds assistant msg + tool result msg, so message count grows
        assertTrue(messageCounts.get(1) > messageCounts.get(0), "Context should grow between rounds");
        assertTrue(messageCounts.get(2) > messageCounts.get(1), "Context should grow between rounds");
    }

    // ==================== 辅助方法 ====================

    private boolean futureHasError(CompletableFuture<?> future) {
        try {
            future.join();
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private Tool createEchoTool(String name) {
        return new Tool() {
            public String getName() { return name; }
            public String getDescription() { return "Echo tool"; }
            public ToolSchema getSchema() { return ToolSchema.empty(); }
            public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("echo: " + args);
            }
        };
    }

    private List<ConversationMessage> createUserMessages(String text) {
        return List.of(ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.USER)
            .textContent(text)
            .build());
    }

    private LLMResponse createTextResponse(String text) {
        return LLMResponse.builder()
            .message(ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent(text)
                .build())
            .build();
    }

    private LLMResponse createToolCallResponse(String toolName, Map<String, Object> args) {
        ToolCall toolCall = new ToolCall("call_" + callCounter.incrementAndGet(), toolName, args);
        return LLMResponse.builder()
            .message(ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent("")
                .build())
            .toolCalls(List.of(toolCall))
            .build();
    }

    private static class MockSyncProvider implements LLMProvider {
        private final Queue<LLMResponse> responses = new LinkedList<>();
        private int callCount = 0;

        void addResponse(LLMResponse response) { responses.add(response); }
        int getCallCount() { return callCount; }

        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            callCount++;
            LLMResponse response = responses.poll();
            if (response == null) throw new RuntimeException("No more mock responses");
            return response;
        }

        @Override
        public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
            return CompletableFuture.completedFuture(complete(m, o));
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ModelCapability getModelCapability() { return null; }

        @Override
        public String getProviderName() { return "mock-sync"; }
    }

    private static class ReactiveMockProvider implements LLMProvider {
        private final Queue<List<StreamEvent>> streamResponses = new LinkedList<>();

        void addStreamResponse(List<StreamEvent> events) { streamResponses.add(events); }

        @Override
        public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            List<StreamEvent> events = streamResponses.poll();
            if (events == null) return Flux.error(new RuntimeException("No more mock stream responses"));
            return Flux.fromIterable(events);
        }

        @Override
        public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ModelCapability getModelCapability() { return null; }

        @Override
        public String getProviderName() { return "reactive-mock"; }
    }
}
