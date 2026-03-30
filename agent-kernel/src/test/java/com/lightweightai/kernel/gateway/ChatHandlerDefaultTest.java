package com.lightweightai.kernel.gateway;

import com.lightweightai.kernel.core.StreamEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChatHandler - default chatStreamReactive 桥接")
class ChatHandlerDefaultTest {

    @Test
    @DisplayName("默认实现桥接 callback 到 Flux: onDelta + onComplete")
    void bridgesCallbackToFlux() {
        ChatHandler handler = new ChatHandler() {
            @Override
            public GatewayResponse chat(GatewayRequest request) {
                return null;
            }

            @Override
            public CompletableFuture<GatewayResponse> chatStream(GatewayRequest request, StreamCallback callback) {
                callback.onDelta("hello ", null);
                callback.onDelta("world", Map.of("emotion", "happy"));
                callback.onComplete(GatewayResponse.builder()
                        .requestId("r1").sessionId("s1").text("hello world").build());
                return CompletableFuture.completedFuture(null);
            }
        };

        GatewayRequest request = GatewayRequest.builder()
                .message("hi")
                .build();

        StepVerifier.create(handler.chatStreamReactive(request))
                .assertNext(event -> {
                    assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
                    assertEquals("hello ", event.getTextDelta());
                })
                .assertNext(event -> {
                    assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
                    assertEquals("world", event.getTextDelta());
                    assertEquals("happy", event.getMetadata().get("emotion"));
                })
                .assertNext(event -> {
                    assertEquals(StreamEvent.EventType.LLM_COMPLETE, event.getType());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("默认实现桥接 callback 错误到 Flux error")
    void bridgesErrorToFlux() {
        ChatHandler handler = new ChatHandler() {
            @Override
            public GatewayResponse chat(GatewayRequest request) {
                return null;
            }

            @Override
            public CompletableFuture<GatewayResponse> chatStream(GatewayRequest request, StreamCallback callback) {
                callback.onDelta("partial", null);
                callback.onError(new RuntimeException("connection lost"));
                return CompletableFuture.completedFuture(null);
            }
        };

        GatewayRequest request = GatewayRequest.builder()
                .message("test")
                .build();

        StepVerifier.create(handler.chatStreamReactive(request))
                .assertNext(event -> {
                    assertEquals(StreamEvent.EventType.TEXT_DELTA, event.getType());
                    assertEquals("partial", event.getTextDelta());
                })
                .expectErrorMatches(e -> e instanceof RuntimeException
                        && e.getMessage().equals("connection lost"))
                .verify();
    }
}
