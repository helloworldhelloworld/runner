package com.lightweightai.kernel.gateway;

import com.lightweightai.kernel.core.StreamEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChatHandler.chatStreamReactive — default reactive bridge from callback-based chatStream")
class ChatHandlerReactiveBridgeTest {

    @Nested
    @DisplayName("successful stream")
    class Success {

        @Test
        @DisplayName("bridges deltas and completion to Flux<StreamEvent>")
        void bridgesSuccessfully() {
            ChatHandler handler = new FakeChatHandler(
                    List.of("Hello", " there"),
                    null
            );

            GatewayRequest request = GatewayRequest.builder()
                    .sessionId("test-session")
                    .message("hi")
                    .build();

            List<StreamEvent> events = new ArrayList<>();
            StepVerifier.create(
                    handler.chatStreamReactive(request)
                            .doOnNext(events::add)
            )
                    .thenConsumeWhile(e -> true)
                    .verifyComplete();

            long textDeltas = events.stream()
                    .filter(e -> e.getType() == StreamEvent.EventType.TEXT_DELTA)
                    .count();
            assertEquals(2, textDeltas);

            long completes = events.stream()
                    .filter(e -> e.getType() == StreamEvent.EventType.LLM_COMPLETE)
                    .count();
            assertEquals(1, completes);
        }
    }

    @Nested
    @DisplayName("error propagation")
    class ErrorPropagation {

        @Test
        @DisplayName("bridges onError to Flux error signal")
        void bridgesError() {
            ChatHandler handler = new FakeChatHandler(
                    List.of(),
                    new RuntimeException("backend failed")
            );

            GatewayRequest request = GatewayRequest.builder()
                    .sessionId("test-session")
                    .message("hi")
                    .build();

            StepVerifier.create(handler.chatStreamReactive(request))
                    .expectError(RuntimeException.class)
                    .verify();
        }
    }

    private static class FakeChatHandler implements ChatHandler {
        private final List<String> deltas;
        private final Throwable error;

        FakeChatHandler(List<String> deltas, Throwable error) {
            this.deltas = deltas;
            this.error = error;
        }

        @Override
        public GatewayResponse chat(GatewayRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<GatewayResponse> chatStream(GatewayRequest request, StreamCallback callback) {
            if (error != null) {
                callback.onError(error);
                return CompletableFuture.failedFuture(error);
            }
            for (String delta : deltas) {
                callback.onDelta(delta, Map.of());
            }
            GatewayResponse response = GatewayResponse.builder()
                    .text("full reply")
                    .sessionId(request.getSessionId())
                    .build();
            callback.onComplete(response);
            return CompletableFuture.completedFuture(response);
        }
    }
}
