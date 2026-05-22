package com.lightweightai.kernel.gateway;

import com.lightweightai.kernel.core.StreamEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChatHandler - 聊天处理接口")
class ChatHandlerTest {

    @Test
    @DisplayName("默认 chatStreamReactive 桥接 callback-based chatStream")
    void defaultReactiveBridgesCallback() {
        ChatHandler handler = new ChatHandler() {
            @Override
            public GatewayResponse chat(GatewayRequest request) {
                return GatewayResponse.builder().text("sync").build();
            }

            @Override
            public CompletableFuture<GatewayResponse> chatStream(GatewayRequest request,
                                                                   StreamCallback callback) {
                callback.onDelta("Hello ", Map.of("confidence", 0.9));
                callback.onDelta("World", Map.of());
                callback.onComplete(GatewayResponse.builder().text("Hello World").build());
                return CompletableFuture.completedFuture(
                        GatewayResponse.builder().text("Hello World").build());
            }
        };

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("s1")
                .message("hi")
                .build();

        Flux<StreamEvent> flux = handler.chatStreamReactive(request);

        StepVerifier.create(flux)
                .assertNext(e -> {
                    assertEquals(StreamEvent.EventType.TEXT_DELTA, e.getType());
                    assertEquals("Hello ", e.getTextDelta());
                })
                .assertNext(e -> {
                    assertEquals(StreamEvent.EventType.TEXT_DELTA, e.getType());
                    assertEquals("World", e.getTextDelta());
                })
                .assertNext(e -> assertEquals(StreamEvent.EventType.LLM_COMPLETE, e.getType()))
                .verifyComplete();
    }

    @Test
    @DisplayName("chatStreamReactive 在 callback.onError 时传播异常")
    void reactivePropagtesError() {
        ChatHandler handler = new ChatHandler() {
            @Override
            public GatewayResponse chat(GatewayRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<GatewayResponse> chatStream(GatewayRequest request,
                                                                   StreamCallback callback) {
                callback.onDelta("partial", Map.of());
                callback.onError(new RuntimeException("connection lost"));
                return new CompletableFuture<>();
            }
        };

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("s2")
                .message("fail")
                .build();

        Flux<StreamEvent> flux = handler.chatStreamReactive(request);

        StepVerifier.create(flux)
                .assertNext(e -> assertEquals(StreamEvent.EventType.TEXT_DELTA, e.getType()))
                .expectErrorMatches(t -> t.getMessage().contains("connection lost"))
                .verify();
    }
}
