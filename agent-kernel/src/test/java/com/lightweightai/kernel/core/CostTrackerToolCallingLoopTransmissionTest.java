package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.context.CostTracker;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ModelCapability;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Transmission test: CostTracker → ToolCallingLoop.
 *
 * Verifies that when CostTracker reports over-budget, the reactive
 * ToolCallingLoop stops emitting events (exits gracefully).
 *
 * Also verifies the sync path respects CancellationToken.
 */
@DisplayName("CostTracker ↔ ToolCallingLoop Transmission")
class CostTrackerToolCallingLoopTransmissionTest {

    @Test
    @DisplayName("Reactive loop exits when CostTracker is over budget before first iteration")
    void reactiveLoopExitsWhenOverBudget() {
        CostTracker tracker = new CostTracker(100);
        tracker.record(200, 0); // already over budget

        ToolRegistry registry = new ToolRegistry();
        ToolExecutor executor = new ToolExecutor(registry);

        // Provider should never be called if budget is exceeded
        LLMProvider neverCalled = new NeverCalledProvider();

        ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(neverCalled)
                .toolExecutor(executor)
                .maxIterations(5)
                .costTracker(tracker)
                .build();

        List<ConversationMessage> messages = List.of(
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("test")
                .build()
        );

        StepVerifier.create(loop.executeWithToolsReactive(messages, LLMOptions.builder().build()))
                .verifyComplete(); // should complete immediately with no events
    }

    @Test
    @DisplayName("Reactive loop exits when CancellationToken is cancelled before first iteration")
    void reactiveLoopExitsWhenCancelled() {
        CancellationToken token = new CancellationToken();
        token.cancel();

        ToolRegistry registry = new ToolRegistry();
        ToolExecutor executor = new ToolExecutor(registry);
        LLMProvider neverCalled = new NeverCalledProvider();

        ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(neverCalled)
                .toolExecutor(executor)
                .maxIterations(5)
                .cancellationToken(token)
                .build();

        List<ConversationMessage> messages = List.of(
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("test")
                .build()
        );

        StepVerifier.create(loop.executeWithToolsReactive(messages, LLMOptions.builder().build()))
                .verifyComplete();
    }

    @Test
    @DisplayName("Sync loop returns cancelled response when token is cancelled")
    void syncLoopReturnsCancelledWhenTokenCancelled() {
        CancellationToken token = new CancellationToken();
        token.cancel();

        ToolRegistry registry = new ToolRegistry();
        ToolExecutor executor = new ToolExecutor(registry);
        LLMProvider neverCalled = new NeverCalledProvider();

        ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(neverCalled)
                .toolExecutor(executor)
                .maxIterations(5)
                .cancellationToken(token)
                .build();

        LLMResponse response = loop.executeWithTools(
            List.of(ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("test")
                .build()),
            LLMOptions.builder().build());

        assertEquals("cancelled", response.getStopReason());
        assertEquals("[cancelled]", response.getMessage().getTextContent());
    }

    @Test
    @DisplayName("Reactive loop completes normally when budget has remaining tokens")
    void reactiveLoopProceedsWhenUnderBudget() {
        CostTracker tracker = new CostTracker(10000);
        tracker.record(100, 50); // well under budget

        AtomicInteger providerCalls = new AtomicInteger(0);
        LLMProvider provider = new SimpleEndTurnProvider(providerCalls);

        ToolRegistry registry = new ToolRegistry();
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
                .textContent("test")
                .build()
        );

        StepVerifier.create(loop.executeWithToolsReactive(messages, LLMOptions.builder().build()))
                .expectNextMatches(e -> e.getType() == StreamEvent.EventType.LLM_COMPLETE)
                .verifyComplete();

        assertEquals(1, providerCalls.get(), "provider should be called once");
    }

    @Test
    @DisplayName("Unlimited budget (0) never blocks the loop")
    void unlimitedBudgetNeverBlocks() {
        CostTracker tracker = new CostTracker(0);
        tracker.record(999999, 999999); // huge consumption, but budget=0 means unlimited

        AtomicInteger providerCalls = new AtomicInteger(0);
        LLMProvider provider = new SimpleEndTurnProvider(providerCalls);

        ToolRegistry registry = new ToolRegistry();
        ToolExecutor executor = new ToolExecutor(registry);

        ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(provider)
                .toolExecutor(executor)
                .maxIterations(5)
                .costTracker(tracker)
                .build();

        StepVerifier.create(loop.executeWithToolsReactive(
                List.of(ConversationMessage.builder()
                    .role(ConversationMessage.MessageRole.USER)
                    .textContent("test")
                    .build()),
                LLMOptions.builder().build()))
            .expectNextCount(1)
            .verifyComplete();

        assertEquals(1, providerCalls.get());
    }

    // ==================== Helpers ====================

    private static class NeverCalledProvider implements LLMProvider {
        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            throw new AssertionError("Provider should not be called");
        }

        @Override
        public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
            return CompletableFuture.failedFuture(new AssertionError("Provider should not be called"));
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> messages, LLMOptions options, StreamEventHandler handler) {
            return CompletableFuture.failedFuture(new AssertionError("Provider should not be called"));
        }

        @Override
        public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> messages, LLMOptions options) {
            return Flux.error(new AssertionError("Provider should not be called"));
        }

        @Override
        public ModelCapability getModelCapability() { return null; }
        @Override
        public String getProviderName() { return "never-called"; }
    }

    private static class SimpleEndTurnProvider implements LLMProvider {
        private final AtomicInteger callCount;

        SimpleEndTurnProvider(AtomicInteger callCount) {
            this.callCount = callCount;
        }

        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            callCount.incrementAndGet();
            return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent("done")
                            .build())
                    .stopReason("end_turn")
                    .build();
        }

        @Override
        public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
            return CompletableFuture.completedFuture(complete(messages, options));
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> messages, LLMOptions options, StreamEventHandler handler) {
            LLMResponse r = complete(messages, options);
            handler.onComplete(r);
            return CompletableFuture.completedFuture(r);
        }

        @Override
        public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> messages, LLMOptions options) {
            callCount.incrementAndGet();
            LLMResponse r = LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent("done")
                            .build())
                    .stopReason("end_turn")
                    .build();
            return Flux.just(StreamEvent.llmComplete(r));
        }

        @Override
        public ModelCapability getModelCapability() { return null; }
        @Override
        public String getProviderName() { return "simple-end-turn"; }
    }
}
