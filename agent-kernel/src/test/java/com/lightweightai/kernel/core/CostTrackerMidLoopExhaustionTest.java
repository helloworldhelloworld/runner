package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.context.CostTracker;
import com.lightweightai.kernel.llm.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CostTracker mid-loop budget exhaustion.
 *
 * Covers gaps:
 * - CostTracker budget exhaustion mid-loop (after iteration N but before N+1)
 * - Verifies the loop exits gracefully when budget runs out during tool-call iterations
 * - Verifies CostTracker.record() integration with ToolCallingLoop
 *   (the loop itself doesn't call record() — the LLM provider or caller does —
 *   but the budget check at the top of each iteration must work correctly)
 */
@DisplayName("CostTracker: mid-loop budget exhaustion")
class CostTrackerMidLoopExhaustionTest {

    @Test
    @DisplayName("Loop exits gracefully when budget runs out between tool iterations")
    void loopExitsWhenBudgetExhaustedMidLoop() {
        CostTracker tracker = new CostTracker(500);

        AtomicInteger providerCalls = new AtomicInteger(0);

        // Provider: first call returns tool_use, second call returns end_turn
        // But we'll exhaust budget between first and second call
        LLMProvider provider = new LLMProvider() {
            @Override
            public LLMResponse complete(List<ConversationMessage> msgs, LLMOptions opts) {
                throw new UnsupportedOperationException();
            }
            @Override
            public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> msgs, LLMOptions opts) {
                throw new UnsupportedOperationException();
            }
            @Override
            public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> msgs, LLMOptions opts, StreamEventHandler h) {
                throw new UnsupportedOperationException();
            }
            @Override
            public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> msgs, LLMOptions opts) {
                int call = providerCalls.incrementAndGet();
                if (call == 1) {
                    // First call: return a tool_use
                    ToolCall tc = new ToolCall("call_" + UUID.randomUUID(), "echo", Map.of("text", "hi"));
                    LLMResponse resp = LLMResponse.builder()
                            .message(ConversationMessage.builder()
                                    .role(ConversationMessage.MessageRole.ASSISTANT)
                                    .textContent("")
                                    .build())
                            .toolCalls(List.of(tc))
                            .stopReason("tool_use")
                            .build();
                    // Simulate that this call consumed tokens — push tracker over budget
                    tracker.record(300, 300); // total=600 > budget=500
                    return Flux.just(StreamEvent.llmComplete(resp));
                }
                // Should never reach here because budget is exhausted
                LLMResponse resp = LLMResponse.builder()
                        .message(ConversationMessage.builder()
                                .role(ConversationMessage.MessageRole.ASSISTANT)
                                .textContent("should not reach here")
                                .build())
                        .stopReason("end_turn")
                        .build();
                return Flux.just(StreamEvent.llmComplete(resp));
            }
            @Override
            public ModelCapability getModelCapability() { return null; }
            @Override
            public String getProviderName() { return "budget-test"; }
        };

        ToolRegistry registry = new ToolRegistry();
        registry.register(echoTool());
        ToolExecutor executor = new ToolExecutor(registry);

        ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(provider)
                .toolExecutor(executor)
                .maxIterations(5)
                .costTracker(tracker)
                .build();

        List<ConversationMessage> messages = List.of(
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("test budget")
                .build()
        );

        List<StreamEvent> events = loop.executeWithToolsReactive(messages, LLMOptions.builder().build())
                .collectList().block();

        assertNotNull(events);
        // The loop should have produced events from the first call (LLM_COMPLETE, TOOL_CALL_START, TOOL_RESULT)
        // but should NOT have made a second LLM call because budget is exhausted
        assertTrue(providerCalls.get() <= 1,
                "Provider should be called at most once; second call should be blocked by budget check. Actual: " + providerCalls.get());
        assertTrue(tracker.isOverBudget(), "Tracker should report over budget");
    }

    @Test
    @DisplayName("Loop completes normally when budget has room for all iterations")
    void loopCompletesWhenBudgetSufficient() {
        CostTracker tracker = new CostTracker(10000);

        AtomicInteger providerCalls = new AtomicInteger(0);

        LLMProvider provider = new LLMProvider() {
            @Override
            public LLMResponse complete(List<ConversationMessage> msgs, LLMOptions opts) {
                throw new UnsupportedOperationException();
            }
            @Override
            public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> msgs, LLMOptions opts) {
                throw new UnsupportedOperationException();
            }
            @Override
            public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> msgs, LLMOptions opts, StreamEventHandler h) {
                throw new UnsupportedOperationException();
            }
            @Override
            public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> msgs, LLMOptions opts) {
                int call = providerCalls.incrementAndGet();
                if (call == 1) {
                    ToolCall tc = new ToolCall("call_1", "echo", Map.of("text", "hi"));
                    LLMResponse resp = LLMResponse.builder()
                            .message(ConversationMessage.builder()
                                    .role(ConversationMessage.MessageRole.ASSISTANT)
                                    .textContent("")
                                    .build())
                            .toolCalls(List.of(tc))
                            .stopReason("tool_use")
                            .build();
                    tracker.record(100, 50); // small consumption, within budget
                    return Flux.just(StreamEvent.llmComplete(resp));
                }
                // Second call: end_turn
                LLMResponse resp = LLMResponse.builder()
                        .message(ConversationMessage.builder()
                                .role(ConversationMessage.MessageRole.ASSISTANT)
                                .textContent("final answer")
                                .build())
                        .stopReason("end_turn")
                        .build();
                tracker.record(80, 40);
                return Flux.just(StreamEvent.llmComplete(resp));
            }
            @Override
            public ModelCapability getModelCapability() { return null; }
            @Override
            public String getProviderName() { return "budget-ok"; }
        };

        ToolRegistry registry = new ToolRegistry();
        registry.register(echoTool());
        ToolExecutor executor = new ToolExecutor(registry);

        ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(provider)
                .toolExecutor(executor)
                .maxIterations(5)
                .costTracker(tracker)
                .build();

        List<ConversationMessage> messages = List.of(
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("test within budget")
                .build()
        );

        List<StreamEvent> events = loop.executeWithToolsReactive(messages, LLMOptions.builder().build())
                .collectList().block();

        assertNotNull(events);
        assertEquals(2, providerCalls.get(),
                "Provider should be called twice (tool_use + end_turn)");
        assertFalse(tracker.isOverBudget(), "Budget should still have room");

        // Verify the final LLM_COMPLETE has the expected response
        boolean hasFinalComplete = events.stream()
                .anyMatch(e -> e.getType() == StreamEvent.EventType.LLM_COMPLETE
                        && e.getResponse() != null
                        && "end_turn".equals(e.getResponse().getStopReason()));
        assertTrue(hasFinalComplete, "Should have a final LLM_COMPLETE with end_turn");
    }

    // ==================== Helpers ====================

    private static Tool echoTool() {
        return new Tool() {
            @Override public String getName() { return "echo"; }
            @Override public String getDescription() { return "Echoes the input"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("echo: " + args.getOrDefault("text", ""));
            }
        };
    }
}
