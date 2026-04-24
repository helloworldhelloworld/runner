package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.context.CostTracker;
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
 * Acceptance: ToolCallingLoop 在每轮迭代前检查 CostTracker.isOverBudget()，
 * 超预算时优雅退出（Flux 正常 complete，不 error）。
 *
 * 验证链路: CostTracker.record() → isOverBudget() → ToolCallingLoop 停止迭代
 *
 * 同时验证 sync 路径中 CancellationToken 的优雅退出行为（返回 [cancelled]）。
 */
@DisplayName("Acceptance: ToolCallingLoop 超预算时优雅退出")
class ToolCallingLoopCostTrackerAcceptanceTest {

    private ToolRegistry toolRegistry;
    private ToolExecutor toolExecutor;
    private AtomicInteger callCounter;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry();
        toolRegistry.register(echoTool("echo"));
        toolExecutor = new ToolExecutor(toolRegistry);
        callCounter = new AtomicInteger(0);
    }

    @Test
    @DisplayName("reactive: 超预算后下一轮迭代不执行，Flux 正常 complete")
    void reactiveLoopExitsGracefullyWhenOverBudget() {
        CostTracker tracker = new CostTracker(1000);

        ReactiveMockProvider provider = new ReactiveMockProvider();
        // Round 1: tool call
        provider.addStreamResponse(List.of(
                StreamEvent.llmComplete(createToolCallResponse("echo", Map.of("input", "r1")))
        ));
        // Round 2: would be another tool call, but budget will be exceeded
        provider.addStreamResponse(List.of(
                StreamEvent.llmComplete(createToolCallResponse("echo", Map.of("input", "r2")))
        ));
        // Round 3: final (should never reach)
        provider.addStreamResponse(List.of(
                StreamEvent.textDelta("final"),
                StreamEvent.llmComplete(createTextResponse("final"))
        ));

        ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(provider)
                .toolExecutor(toolExecutor)
                .maxIterations(10)
                .costTracker(tracker)
                .build();

        // Simulate budget blowout after first tool execution
        List<StreamEvent> events = loop.executeWithToolsReactive(
                        createUserMessages("test"),
                        LLMOptions.builder().build())
                .doOnNext(e -> {
                    if (e.getType() == StreamEvent.EventType.TOOL_RESULT) {
                        tracker.record(600, 500); // total 1100 > 1000
                    }
                })
                .collectList()
                .block();

        assertNotNull(events);
        assertTrue(tracker.isOverBudget(), "Tracker should be over budget after recording");

        // Should NOT have events from round 2
        long toolCallStarts = events.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.TOOL_CALL_START)
                .count();
        assertEquals(1, toolCallStarts,
                "Only 1 tool call should execute; second round should be skipped due to budget");

        // Should NOT have "final" text
        boolean hasFinal = events.stream()
                .anyMatch(e -> e.getType() == StreamEvent.EventType.TEXT_DELTA
                        && "final".equals(e.getTextDelta()));
        assertFalse(hasFinal, "Final round should not execute when over budget");
    }

    @Test
    @DisplayName("reactive: 未超预算时所有轮次正常执行")
    void reactiveLoopContinuesWhenUnderBudget() {
        CostTracker tracker = new CostTracker(100000); // large budget

        ReactiveMockProvider provider = new ReactiveMockProvider();
        provider.addStreamResponse(List.of(
                StreamEvent.llmComplete(createToolCallResponse("echo", Map.of("input", "r1")))
        ));
        provider.addStreamResponse(List.of(
                StreamEvent.textDelta("done"),
                StreamEvent.llmComplete(createTextResponse("done"))
        ));

        ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(provider)
                .toolExecutor(toolExecutor)
                .maxIterations(10)
                .costTracker(tracker)
                .build();

        List<StreamEvent> events = loop.executeWithToolsReactive(
                        createUserMessages("test"),
                        LLMOptions.builder().build())
                .doOnNext(e -> {
                    if (e.getType() == StreamEvent.EventType.TOOL_RESULT) {
                        tracker.record(100, 50); // within budget
                    }
                })
                .collectList()
                .block();

        assertNotNull(events);
        assertFalse(tracker.isOverBudget());

        boolean hasDone = events.stream()
                .anyMatch(e -> e.getType() == StreamEvent.EventType.TEXT_DELTA
                        && "done".equals(e.getTextDelta()));
        assertTrue(hasDone, "Should complete all rounds when under budget");
    }

    @Test
    @DisplayName("sync: CancellationToken 在工具循环前触发时返回 [cancelled]")
    void syncCancellationReturnsProperResponse() {
        CancellationToken token = new CancellationToken();
        token.cancel(); // pre-cancelled

        LLMProvider mockProvider = new LLMProvider() {
            @Override
            public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
                fail("LLM should not be called when already cancelled");
                return null;
            }
            @Override
            public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
                return CompletableFuture.failedFuture(new AssertionError("should not call"));
            }
            @Override
            public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> messages, LLMOptions options, StreamEventHandler handler) {
                return CompletableFuture.failedFuture(new AssertionError("should not call"));
            }
            @Override public ModelCapability getModelCapability() { return null; }
            @Override public String getProviderName() { return "mock"; }
        };

        ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(mockProvider)
                .toolExecutor(toolExecutor)
                .maxIterations(5)
                .cancellationToken(token)
                .build();

        LLMResponse response = loop.executeWithTools(createUserMessages("test"), LLMOptions.builder().build());

        assertNotNull(response);
        assertEquals("cancelled", response.getStopReason(),
                "Cancelled loop should return stopReason='cancelled'");
        assertEquals("[cancelled]", response.getMessage().getTextContent(),
                "Cancelled loop should return [cancelled] text");
    }

    @Test
    @DisplayName("sync: CancellationToken 在工具执行后触发时，下一轮返回 [cancelled]")
    void syncCancellationAfterToolCall() {
        CancellationToken token = new CancellationToken();

        AtomicInteger llmCallCount = new AtomicInteger(0);

        LLMProvider mockProvider = new LLMProvider() {
            @Override
            public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
                int call = llmCallCount.incrementAndGet();
                if (call == 1) {
                    return createToolCallResponseSync("echo", Map.of("input", "test"));
                }
                fail("Second LLM call should not happen; cancellation should prevent it");
                return null;
            }
            @Override
            public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
                return CompletableFuture.completedFuture(complete(messages, options));
            }
            @Override
            public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> messages, LLMOptions options, StreamEventHandler handler) {
                throw new UnsupportedOperationException();
            }
            @Override public ModelCapability getModelCapability() { return null; }
            @Override public String getProviderName() { return "mock"; }
        };

        // Register a tool that cancels the token when executed
        ToolRegistry cancellingRegistry = new ToolRegistry();
        cancellingRegistry.register(new Tool() {
            @Override public String getName() { return "echo"; }
            @Override public String getDescription() { return "echo"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                token.cancel();
                return ToolResult.success("echoed");
            }
        });

        ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(mockProvider)
                .toolExecutor(new ToolExecutor(cancellingRegistry))
                .maxIterations(10)
                .cancellationToken(token)
                .build();

        LLMResponse response = loop.executeWithTools(createUserMessages("test"), LLMOptions.builder().build());

        assertEquals(1, llmCallCount.get(), "Only first LLM call should happen");
        assertEquals("cancelled", response.getStopReason());
    }

    // ==================== Helpers ====================

    private Tool echoTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return "Echo"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
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
                .stopReason("end_turn")
                .build();
    }

    private LLMResponse createToolCallResponse(String toolName, Map<String, Object> args) {
        return LLMResponse.builder()
                .message(ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.ASSISTANT)
                        .textContent("")
                        .build())
                .toolCalls(List.of(new ToolCall("call_" + callCounter.incrementAndGet(), toolName, args)))
                .build();
    }

    private LLMResponse createToolCallResponseSync(String toolName, Map<String, Object> args) {
        return LLMResponse.builder()
                .message(ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.ASSISTANT)
                        .textContent("")
                        .build())
                .toolCalls(List.of(new ToolCall("call_sync_1", toolName, args)))
                .stopReason("tool_use")
                .build();
    }

    private static class ReactiveMockProvider implements LLMProvider {
        private final Queue<List<StreamEvent>> streamResponses = new LinkedList<>();

        void addStreamResponse(List<StreamEvent> events) {
            streamResponses.add(events);
        }

        @Override
        public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> messages, LLMOptions options) {
            List<StreamEvent> events = streamResponses.poll();
            if (events == null) {
                return Flux.error(new RuntimeException("No more mock stream responses"));
            }
            return Flux.fromIterable(events);
        }

        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            throw new UnsupportedOperationException("Use reactive path");
        }
        @Override
        public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
            throw new UnsupportedOperationException();
        }
        @Override
        public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> messages, LLMOptions options, StreamEventHandler handler) {
            throw new UnsupportedOperationException();
        }
        @Override public ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return "reactive-mock"; }
    }
}
