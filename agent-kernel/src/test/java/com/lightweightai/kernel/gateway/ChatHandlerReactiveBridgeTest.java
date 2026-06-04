package com.lightweightai.kernel.gateway;

import com.lightweightai.kernel.core.StreamEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChatHandler - chatStreamReactive 默认桥接实现")
class ChatHandlerReactiveBridgeTest {

    @Test
    @DisplayName("callback 产生的 delta 被桥接为 TEXT_DELTA 事件")
    void bridgesDeltasToTextDeltaEvents() {
        ChatHandler handler = new ChatHandler() {
            @Override
            public GatewayResponse chat(GatewayRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<GatewayResponse> chatStream(GatewayRequest request, StreamCallback callback) {
                callback.onDelta("Hello", Map.of());
                callback.onDelta(" World", Map.of());
                GatewayResponse resp = GatewayResponse.builder()
                        .text("Hello World")
                        .requestId(request.getRequestId())
                        .build();
                callback.onComplete(resp);
                return CompletableFuture.completedFuture(resp);
            }
        };

        GatewayRequest request = GatewayRequest.builder().message("test").build();
        Flux<StreamEvent> flux = handler.chatStreamReactive(request);

        StepVerifier.create(flux)
                .assertNext(event -> {
                    assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
                    assertEquals("Hello", event.getTextDelta());
                })
                .assertNext(event -> {
                    assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
                    assertEquals(" World", event.getTextDelta());
                })
                .assertNext(event -> {
                    assertEquals(StreamEvent.EventType.LLM_COMPLETE, event.getType());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("callback.onError 被桥接为 Flux error signal")
    void bridgesErrorToFluxError() {
        RuntimeException cause = new RuntimeException("LLM failed");

        ChatHandler handler = new ChatHandler() {
            @Override
            public GatewayResponse chat(GatewayRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<GatewayResponse> chatStream(GatewayRequest request, StreamCallback callback) {
                callback.onDelta("partial", Map.of());
                callback.onError(cause);
                return CompletableFuture.failedFuture(cause);
            }
        };

        GatewayRequest request = GatewayRequest.builder().message("test").build();

        StepVerifier.create(handler.chatStreamReactive(request))
                .assertNext(event -> {
                    assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
                    assertEquals("partial", event.getTextDelta());
                })
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    @DisplayName("onDelta 携带 metadata 被正确传递到 StreamEvent")
    void metadataPassedThroughToEvent() {
        ChatHandler handler = new ChatHandler() {
            @Override
            public GatewayResponse chat(GatewayRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<GatewayResponse> chatStream(GatewayRequest request, StreamCallback callback) {
                callback.onDelta("data", Map.of("confidence", 0.95));
                GatewayResponse resp = GatewayResponse.builder().text("data").build();
                callback.onComplete(resp);
                return CompletableFuture.completedFuture(resp);
            }
        };

        GatewayRequest request = GatewayRequest.builder().message("test").build();

        StepVerifier.create(handler.chatStreamReactive(request))
                .assertNext(event -> {
                    assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
                    Map<String, Object> meta = event.getMetadata();
                    assertNotNull(meta);
                    assertEquals(0.95, meta.get("confidence"));
                })
                .assertNext(event -> assertEquals(StreamEvent.EventType.LLM_COMPLETE, event.getType()))
                .verifyComplete();
    }

    @Test
    @DisplayName("无 delta 直接 complete 只产生 LLM_COMPLETE 事件")
    void emptyStreamProducesOnlyCompleteEvent() {
        ChatHandler handler = new ChatHandler() {
            @Override
            public GatewayResponse chat(GatewayRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<GatewayResponse> chatStream(GatewayRequest request, StreamCallback callback) {
                GatewayResponse resp = GatewayResponse.builder().text("").build();
                callback.onComplete(resp);
                return CompletableFuture.completedFuture(resp);
            }
        };

        GatewayRequest request = GatewayRequest.builder().message("test").build();

        StepVerifier.create(handler.chatStreamReactive(request))
                .assertNext(event -> assertEquals(StreamEvent.EventType.LLM_COMPLETE, event.getType()))
                .verifyComplete();
    }
}
