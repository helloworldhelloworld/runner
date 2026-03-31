package com.lightweightai.kernel.gateway;

import com.lightweightai.kernel.core.StreamEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChatHandler - 业务处理接口与 Reactive 桥接")
class ChatHandlerTest {

    // ==================== Default chatStreamReactive ====================

    @Test
    @DisplayName("chatStreamReactive 桥接 chatStream 回调为 Flux")
    void shouldBridgeCallbackToFlux() {
        // Given: a ChatHandler that emits text deltas via callback
        ChatHandler handler = new ChatHandler() {
            @Override
            public GatewayResponse chat(GatewayRequest request) {
                return GatewayResponse.builder().text("sync").build();
            }

            @Override
            public CompletableFuture<GatewayResponse> chatStream(
                    GatewayRequest request, StreamCallback callback) {
                CompletableFuture<GatewayResponse> future = new CompletableFuture<>();
                // Simulate async streaming
                new Thread(() -> {
                    callback.onDelta("Hello ", null);
                    callback.onDelta("World", Map.of("emotion", "neutral"));
                    GatewayResponse response = GatewayResponse.builder()
                        .text("Hello World")
                        .build();
                    callback.onComplete(response);
                    future.complete(response);
                }).start();
                return future;
            }
        };

        GatewayRequest request = GatewayRequest.builder()
            .message("test")
            .build();

        // When: using default reactive bridge
        Flux<StreamEvent> flux = handler.chatStreamReactive(request);

        // Then: should receive text deltas and LLM_COMPLETE
        StepVerifier.create(flux)
            .assertNext(event -> {
                assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
                assertEquals("Hello ", event.getTextDelta());
            })
            .assertNext(event -> {
                assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
                assertEquals("World", event.getTextDelta());
            })
            .assertNext(event -> {
                assertEquals(StreamEvent.EventType.LLM_COMPLETE, event.getType());
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("chatStreamReactive 桥接错误传播")
    void shouldBridgeErrorToFlux() {
        ChatHandler handler = new ChatHandler() {
            @Override
            public GatewayResponse chat(GatewayRequest request) {
                return null;
            }

            @Override
            public CompletableFuture<GatewayResponse> chatStream(
                    GatewayRequest request, StreamCallback callback) {
                CompletableFuture<GatewayResponse> future = new CompletableFuture<>();
                new Thread(() -> {
                    callback.onDelta("partial", null);
                    callback.onError(new RuntimeException("LLM timeout"));
                    future.completeExceptionally(new RuntimeException("LLM timeout"));
                }).start();
                return future;
            }
        };

        GatewayRequest request = GatewayRequest.builder()
            .message("test")
            .build();

        Flux<StreamEvent> flux = handler.chatStreamReactive(request);

        StepVerifier.create(flux)
            .assertNext(event -> {
                assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
                assertEquals("partial", event.getTextDelta());
            })
            .expectErrorMatches(e -> e.getMessage().equals("LLM timeout"))
            .verify();
    }
}
