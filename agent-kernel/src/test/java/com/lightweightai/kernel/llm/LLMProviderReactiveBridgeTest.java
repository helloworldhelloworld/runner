package com.lightweightai.kernel.llm;

import com.lightweightai.kernel.core.StreamEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LLMProvider.completeStreamReactive — default reactive bridge")
class LLMProviderReactiveBridgeTest {

    @Nested
    @DisplayName("text-only response")
    class TextOnly {

        @Test
        @DisplayName("bridges text deltas and completion to Flux<StreamEvent>")
        void bridgesTextAndCompletion() {
            LLMProvider provider = new FakeStreamingProvider(
                    List.of("Hello", " world"),
                    List.of(),
                    "end_turn"
            );

            List<StreamEvent> events = new ArrayList<>();
            StepVerifier.create(
                    provider.completeStreamReactive(List.of(), null)
                            .doOnNext(events::add)
            )
                    .thenConsumeWhile(e -> true)
                    .verifyComplete();

            assertTrue(events.size() >= 3, "Should have at least 2 deltas + 1 complete");

            long textDeltas = events.stream()
                    .filter(e -> e.getType() == StreamEvent.EventType.TEXT_DELTA)
                    .count();
            assertEquals(2, textDeltas, "Should have 2 text delta events");

            long completes = events.stream()
                    .filter(e -> e.getType() == StreamEvent.EventType.LLM_COMPLETE)
                    .count();
            assertEquals(1, completes, "Should have 1 LLM_COMPLETE event");
        }
    }

    @Nested
    @DisplayName("tool call response")
    class WithToolCalls {

        @Test
        @DisplayName("bridges tool call delta to Flux<StreamEvent>")
        void bridgesToolCallDelta() {
            ToolCall tc = new ToolCall("call_1", "echo", Map.of("text", "hi"));
            LLMProvider provider = new FakeStreamingProvider(
                    List.of(),
                    List.of(tc),
                    "tool_use"
            );

            List<StreamEvent> events = new ArrayList<>();
            StepVerifier.create(
                    provider.completeStreamReactive(List.of(), null)
                            .doOnNext(events::add)
            )
                    .thenConsumeWhile(e -> true)
                    .verifyComplete();

            long toolStarts = events.stream()
                    .filter(e -> e.getType() == StreamEvent.EventType.TOOL_CALL_START)
                    .count();
            assertEquals(1, toolStarts, "Should have 1 TOOL_CALL_START event");
        }
    }

    @Nested
    @DisplayName("error propagation")
    class ErrorPropagation {

        @Test
        @DisplayName("bridges onError to Flux error signal")
        void bridgesError() {
            LLMProvider provider = new ErrorStreamingProvider(new RuntimeException("API down"));

            StepVerifier.create(provider.completeStreamReactive(List.of(), null))
                    .expectError(RuntimeException.class)
                    .verify();
        }
    }

    /**
     * Fake streaming provider that delivers scripted deltas via the callback-based completeStream.
     * The default completeStreamReactive method in LLMProvider bridges these to Flux.
     */
    private static class FakeStreamingProvider implements LLMProvider {
        private final List<String> textDeltas;
        private final List<ToolCall> toolCalls;
        private final String stopReason;

        FakeStreamingProvider(List<String> textDeltas, List<ToolCall> toolCalls, String stopReason) {
            this.textDeltas = textDeltas;
            this.toolCalls = toolCalls;
            this.stopReason = stopReason;
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
        public CompletableFuture<LLMResponse> completeStream(
                List<ConversationMessage> messages, LLMOptions options, StreamEventHandler handler) {
            for (String delta : textDeltas) {
                handler.onTextDelta(delta);
            }
            for (ToolCall tc : toolCalls) {
                handler.onToolCallDelta(tc);
            }
            LLMResponse response = LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent(String.join("", textDeltas))
                            .build())
                    .toolCalls(toolCalls)
                    .stopReason(stopReason)
                    .build();
            handler.onComplete(response);
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public ModelCapability getModelCapability() { return null; }

        @Override
        public String getProviderName() { return "fake-streaming"; }
    }

    private static class ErrorStreamingProvider implements LLMProvider {
        private final Throwable error;

        ErrorStreamingProvider(Throwable error) {
            this.error = error;
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
        public CompletableFuture<LLMResponse> completeStream(
                List<ConversationMessage> messages, LLMOptions options, StreamEventHandler handler) {
            handler.onError(error);
            return CompletableFuture.failedFuture(error);
        }

        @Override
        public ModelCapability getModelCapability() { return null; }

        @Override
        public String getProviderName() { return "error-provider"; }
    }
}
