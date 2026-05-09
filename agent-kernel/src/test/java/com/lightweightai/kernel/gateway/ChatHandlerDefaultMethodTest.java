package com.lightweightai.kernel.gateway;

import com.lightweightai.kernel.core.StreamEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChatHandler - default chatStreamReactive bridge")
class ChatHandlerDefaultMethodTest {

    @Nested
    @DisplayName("chatStreamReactive bridges callback-based chatStream")
    class ReactiveBridge {

        @Test
        @DisplayName("text deltas emit as TEXT_DELTA StreamEvents")
        void textDeltasEmitted() {
            ChatHandler handler = new ChatHandler() {
                @Override
                public GatewayResponse chat(GatewayRequest request) {
                    return GatewayResponse.builder().text("full response").build();
                }

                @Override
                public CompletableFuture<GatewayResponse> chatStream(GatewayRequest request, StreamCallback callback) {
                    callback.onDelta("Hello ", null);
                    callback.onDelta("world", Map.of("emotion", "happy"));
                    callback.onComplete(GatewayResponse.builder().text("Hello world").build());
                    return CompletableFuture.completedFuture(null);
                }
            };

            GatewayRequest request = GatewayRequest.builder().message("Hi").build();

            StepVerifier.create(handler.chatStreamReactive(request))
                .assertNext(event -> {
                    assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
                    assertEquals("Hello ", event.getTextDelta());
                })
                .assertNext(event -> {
                    assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
                    assertEquals("world", event.getTextDelta());
                })
                .assertNext(event -> {
                    assertEquals(StreamEvent.EventType.LLM_COMPLETE, event.getType());
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("onError propagates as Flux error signal")
        void errorPropagated() {
            ChatHandler handler = new ChatHandler() {
                @Override
                public GatewayResponse chat(GatewayRequest request) {
                    return null;
                }

                @Override
                public CompletableFuture<GatewayResponse> chatStream(GatewayRequest request, StreamCallback callback) {
                    callback.onDelta("partial ", null);
                    callback.onError(new RuntimeException("LLM timeout"));
                    return CompletableFuture.completedFuture(null);
                }
            };

            GatewayRequest request = GatewayRequest.builder().message("Hi").build();

            StepVerifier.create(handler.chatStreamReactive(request))
                .assertNext(event -> {
                    assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
                    assertEquals("partial ", event.getTextDelta());
                })
                .expectErrorMatches(e -> e instanceof RuntimeException
                    && e.getMessage().equals("LLM timeout"))
                .verify();
        }

        @Test
        @DisplayName("empty response emits only LLM_COMPLETE")
        void emptyResponse() {
            ChatHandler handler = new ChatHandler() {
                @Override
                public GatewayResponse chat(GatewayRequest request) {
                    return null;
                }

                @Override
                public CompletableFuture<GatewayResponse> chatStream(GatewayRequest request, StreamCallback callback) {
                    callback.onComplete(GatewayResponse.builder().text("").build());
                    return CompletableFuture.completedFuture(null);
                }
            };

            GatewayRequest request = GatewayRequest.builder().message("Hi").build();

            StepVerifier.create(handler.chatStreamReactive(request))
                .assertNext(event ->
                    assertEquals(StreamEvent.EventType.LLM_COMPLETE, event.getType()))
                .verifyComplete();
        }
    }
}
