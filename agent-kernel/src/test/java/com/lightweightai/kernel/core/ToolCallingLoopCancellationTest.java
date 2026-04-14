package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.context.CostTracker;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.plugin.FunctionResult;
import com.lightweightai.kernel.plugin.PluginFunction;
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
 * ToolCallingLoop 补充测试 — CancellationToken/CostTracker/边界路径
 *
 * 覆盖之前遗漏的关键路径：
 * - 同步路径 CancellationToken 中途取消
 * - CostTracker 超预算优雅退出（reactive）
 * - CostTracker 与 CancellationToken 同时设置
 * - 同步路径带 executionContext 执行
 * - Tracer.NOOP 默认行为
 */
@DisplayName("ToolCallingLoop - CancellationToken + CostTracker 补充测试")
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

    // ==================== Sync CancellationToken ====================

    @Test
    @DisplayName("同步路径：CancellationToken 已取消时直接返回 [cancelled]")
    void syncPathReturnsCancelledWhenTokenAlreadyCancelled() {
        MockSyncProvider provider = new MockSyncProvider();
        provider.addResponse(createToolCallResponse("echo", Map.of("input", "test")));
        provider.addResponse(createTextResponse("should not reach"));

        CancellationToken token = new CancellationToken();
        token.cancel(); // 预先取消

        ToolCallingLoop loop = ToolCallingLoop.builder()
            .provider(provider)
            .toolExecutor(toolExecutor)
            .maxIterations(5)
            .cancellationToken(token)
            .build();

        LLMResponse result = loop.executeWithTools(createUserMessages("test"), LLMOptions.builder().build());

        assertEquals("[cancelled]", result.getMessage().getTextContent());
        assertEquals("cancelled", result.getStopReason());
        assertEquals(0, provider.getCallCount(), "LLM should not be called when pre-cancelled");
    }

    @Test
    @DisplayName("同步路径：CancellationToken 在第一轮工具调用后取消，第二轮不执行")
    void syncPathCancelsMidIteration() {
        AtomicInteger toolCallCount = new AtomicInteger(0);
        CancellationToken token = new CancellationToken();

        // 注册一个会在执行时取消 token 的工具
        toolRegistry.register(new Tool() {
            public String getName() { return "cancel_trigger"; }
            public String getDescription() { return "Triggers cancellation"; }
            public ToolSchema getSchema() { return ToolSchema.empty(); }
            public ToolResult execute(Map<String, Object> args) {
                toolCallCount.incrementAndGet();
                token.cancel(); // 工具执行时取消
                return ToolResult.success("done");
            }
        });

        MockSyncProvider provider = new MockSyncProvider();
        provider.addResponse(createToolCallResponse("cancel_trigger", Map.of()));
        provider.addResponse(createToolCallResponse("echo", Map.of("input", "second"))); // 不应执行
        provider.addResponse(createTextResponse("should not reach"));

        ToolCallingLoop loop = ToolCallingLoop.builder()
            .provider(provider)
            .toolExecutor(toolExecutor)
            .maxIterations(10)
            .cancellationToken(token)
            .build();

        LLMResponse result = loop.executeWithTools(createUserMessages("test"), LLMOptions.builder().build());

        assertEquals("[cancelled]", result.getMessage().getTextContent());
        assertEquals(1, toolCallCount.get(), "Only first tool call should execute");
    }

    @Test
    @DisplayName("同步路径：无 CancellationToken 正常完成（null token）")
    void syncPathWorksWithoutToken() {
        MockSyncProvider provider = new MockSyncProvider();
        provider.addResponse(createTextResponse("Hello"));

        ToolCallingLoop loop = ToolCallingLoop.builder()
            .provider(provider)
            .toolExecutor(toolExecutor)
            .maxIterations(5)
            .build(); // 不设置 cancellationToken

        LLMResponse result = loop.executeWithTools(createUserMessages("hi"), LLMOptions.builder().build());
        assertEquals("Hello", result.getMessage().getTextContent());
    }

    // ==================== Reactive CostTracker ====================

    @Test
    @DisplayName("Reactive：CostTracker 超预算时返回空 Flux")
    void reactiveCostTrackerOverBudgetReturnsEmpty() {
        ReactiveMockProvider provider = new ReactiveMockProvider();
        provider.addStreamResponse(List.of(
            StreamEvent.textDelta("should not appear"),
            StreamEvent.llmComplete(createTextResponse("should not appear"))
        ));

        CostTracker tracker = new CostTracker(1000);
        tracker.record(800, 300); // 1100 > 1000 → 超预算

        ToolCallingLoop loop = ToolCallingLoop.builder()
            .provider(provider)
            .toolExecutor(toolExecutor)
            .maxIterations(5)
            .costTracker(tracker)
            .build();

        StepVerifier.create(loop.executeWithToolsReactive(createUserMessages("test"), LLMOptions.builder().build()))
            .verifyComplete(); // 空 Flux
    }

    @Test
    @DisplayName("Reactive：CostTracker 未超预算时正常执行")
    void reactiveCostTrackerUnderBudgetExecutesNormally() {
        ReactiveMockProvider provider = new ReactiveMockProvider();
        provider.addStreamResponse(List.of(
            StreamEvent.textDelta("Hello"),
            StreamEvent.llmComplete(createTextResponse("Hello"))
        ));

        CostTracker tracker = new CostTracker(10000);
        tracker.record(100, 50); // 150 < 10000 → 未超

        ToolCallingLoop loop = ToolCallingLoop.builder()
            .provider(provider)
            .toolExecutor(toolExecutor)
            .maxIterations(5)
            .costTracker(tracker)
            .build();

        StepVerifier.create(loop.executeWithToolsReactive(createUserMessages("test"), LLMOptions.builder().build()))
            .assertNext(e -> assertEquals(StreamEvent.EventType.TEXT_DELTA, e.getType()))
            .assertNext(e -> assertEquals(StreamEvent.EventType.LLM_COMPLETE, e.getType()))
            .verifyComplete();
    }

    @Test
    @DisplayName("Reactive：CostTracker 无限预算（0）永远不退出")
    void reactiveCostTrackerUnlimitedBudget() {
        ReactiveMockProvider provider = new ReactiveMockProvider();
        provider.addStreamResponse(List.of(
            StreamEvent.textDelta("ok"),
            StreamEvent.llmComplete(createTextResponse("ok"))
        ));

        CostTracker tracker = new CostTracker(0); // unlimited
        tracker.record(999999, 999999);

        ToolCallingLoop loop = ToolCallingLoop.builder()
            .provider(provider)
            .toolExecutor(toolExecutor)
            .maxIterations(5)
            .costTracker(tracker)
            .build();

        StepVerifier.create(loop.executeWithToolsReactive(createUserMessages("test"), LLMOptions.builder().build()))
            .assertNext(e -> assertEquals(StreamEvent.EventType.TEXT_DELTA, e.getType()))
            .assertNext(e -> assertEquals(StreamEvent.EventType.LLM_COMPLETE, e.getType()))
            .verifyComplete();
    }

    // ==================== CancellationToken + CostTracker 组合 ====================

    @Test
    @DisplayName("Reactive：CancellationToken 优先于 CostTracker 检查")
    void reactiveCancellationTakesPriorityOverCost() {
        ReactiveMockProvider provider = new ReactiveMockProvider();
        provider.addStreamResponse(List.of(
            StreamEvent.textDelta("shouldn't appear"),
            StreamEvent.llmComplete(createTextResponse("shouldn't appear"))
        ));

        CancellationToken token = new CancellationToken();
        token.cancel();
        CostTracker tracker = new CostTracker(10000); // under budget

        ToolCallingLoop loop = ToolCallingLoop.builder()
            .provider(provider)
            .toolExecutor(toolExecutor)
            .maxIterations(5)
            .cancellationToken(token)
            .costTracker(tracker)
            .build();

        StepVerifier.create(loop.executeWithToolsReactive(createUserMessages("test"), LLMOptions.builder().build()))
            .verifyComplete(); // 空，因为 cancelled
    }

    // ==================== 同步路径 executionContext ====================

    @Test
    @DisplayName("同步路径：带 executionContext 调用 ToolExecutor")
    void syncWithExecutionContext() {
        MockSyncProvider provider = new MockSyncProvider();
        provider.addResponse(createToolCallResponse("echo", Map.of("input", "ctx_test")));
        provider.addResponse(createTextResponse("Done with context"));

        ToolExecutionContext ctx = ToolExecutionContext.serverOnly();
        ToolCallingLoop loop = new ToolCallingLoop(provider, toolExecutor, 10, ctx);

        LLMResponse result = loop.executeWithTools(createUserMessages("test"), LLMOptions.builder().build());
        assertEquals("Done with context", result.getMessage().getTextContent());
    }

    // ==================== 构造器边界 ====================

    @Test
    @DisplayName("Tracer 为 null 时使用 NOOP 默认值")
    void tracerDefaultsToNoop() {
        ToolCallingLoop loop = ToolCallingLoop.builder()
            .provider(new ReactiveMockProvider())
            .toolExecutor(toolExecutor)
            .tracer(null)
            .build();

        assertNotNull(loop);
        assertEquals(10, loop.getMaxIterations());
    }

    // ==================== 辅助方法 ====================

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
