package com.lightweightai.kernel.gateway;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.postprocess.StreamPostProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Transmission chain test: Gateway → PostProcessorPipeline → output stream
 *
 * Verifies that post-processors registered via Gateway.builder().addPostProcessor()
 * actually transform the event stream. This catches wiring bugs where processors
 * are registered but never applied.
 */
@DisplayName("Gateway post-processor pipeline wiring")
class GatewayPostProcessorPipelineTest {

    @Test
    @DisplayName("单个后处理器转换 TEXT_DELTA 内容")
    void singlePostProcessorTransformsTextDelta() {
        StreamPostProcessor uppercaser = new StreamPostProcessor() {
            @Override
            public String getName() { return "uppercaser"; }

            @Override
            public int getOrder() { return 10; }

            @Override
            public Flux<StreamEvent> apply(Flux<StreamEvent> source) {
                return source.map(event -> {
                    if (event.getType() == StreamEvent.EventType.TEXT_DELTA && event.getTextDelta() != null) {
                        return StreamEvent.textDelta(event.getTextDelta().toUpperCase());
                    }
                    return event;
                });
            }
        };

        Gateway gateway = Gateway.builder()
                .chatHandler(echoHandler("hello world"))
                .sessionManager(noopSession())
                .addPostProcessor(uppercaser)
                .build();

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("test-session")
                .message("test")
                .build();

        List<StreamEvent> events = gateway.handleStreamReactive(request).collectList().block();
        assertNotNull(events);

        List<String> textDeltas = events.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.TEXT_DELTA)
                .map(StreamEvent::getTextDelta)
                .toList();

        assertFalse(textDeltas.isEmpty(), "should have TEXT_DELTA events");
        assertTrue(textDeltas.stream().allMatch(t -> t.equals(t.toUpperCase())),
                "all text deltas should be uppercased by post-processor, got: " + textDeltas);
    }

    @Test
    @DisplayName("多个后处理器按 order 排序执行")
    void multiplePostProcessorsExecuteInOrderByPriority() {
        List<String> executionOrder = new ArrayList<>();

        StreamPostProcessor first = orderTrackingProcessor("first", 10, executionOrder);
        StreamPostProcessor second = orderTrackingProcessor("second", 20, executionOrder);
        StreamPostProcessor third = orderTrackingProcessor("third", 30, executionOrder);

        // Register in wrong order to verify sorting
        Gateway gateway = Gateway.builder()
                .chatHandler(echoHandler("x"))
                .sessionManager(noopSession())
                .addPostProcessor(third)
                .addPostProcessor(first)
                .addPostProcessor(second)
                .build();

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("order-test")
                .message("test")
                .build();

        gateway.handleStreamReactive(request).collectList().block();

        assertEquals(List.of("first", "second", "third"), executionOrder,
                "processors must execute in order of priority (ascending)");
    }

    @Test
    @DisplayName("后处理器可注入新事件 (POST_PROCESS_DATA)")
    void postProcessorCanInjectEvents() {
        StreamPostProcessor cardInjector = new StreamPostProcessor() {
            @Override
            public String getName() { return "card-injector"; }

            @Override
            public Flux<StreamEvent> apply(Flux<StreamEvent> source) {
                return source.concatWith(
                        Flux.just(StreamEvent.postProcessData("card", Map.of("title", "Weather")))
                );
            }
        };

        Gateway gateway = Gateway.builder()
                .chatHandler(echoHandler("sunny"))
                .sessionManager(noopSession())
                .addPostProcessor(cardInjector)
                .build();

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("inject-test")
                .message("weather?")
                .build();

        List<StreamEvent> events = gateway.handleStreamReactive(request).collectList().block();
        assertNotNull(events);

        List<StreamEvent> postProcessEvents = events.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.POST_PROCESS_DATA)
                .toList();

        assertEquals(1, postProcessEvents.size());
        assertEquals("card", postProcessEvents.get(0).getCategory());
        assertEquals("Weather", postProcessEvents.get(0).getData().get("title"));
    }

    @Test
    @DisplayName("无后处理器时事件流不变")
    void noPostProcessorsPassesEventsThrough() {
        Gateway gateway = Gateway.builder()
                .chatHandler(echoHandler("hello"))
                .sessionManager(noopSession())
                .build();

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("no-pp")
                .message("hi")
                .build();

        List<StreamEvent> events = gateway.handleStreamReactive(request).collectList().block();
        assertNotNull(events);

        boolean hasTextDelta = events.stream()
                .anyMatch(e -> e.getType() == StreamEvent.EventType.TEXT_DELTA
                        && "hello".equals(e.getTextDelta()));
        assertTrue(hasTextDelta, "should have original TEXT_DELTA event");
    }

    // ==================== Helpers ====================

    private static ChatHandler echoHandler(String response) {
        return new ChatHandler() {
            @Override
            public GatewayResponse chat(GatewayRequest request) {
                return GatewayResponse.builder()
                        .requestId(request.getRequestId())
                        .sessionId(request.getSessionId())
                        .text(response)
                        .build();
            }

            @Override
            public CompletableFuture<GatewayResponse> chatStream(GatewayRequest request, StreamCallback callback) {
                callback.onDelta(response, Map.of());
                GatewayResponse resp = GatewayResponse.builder()
                        .requestId(request.getRequestId())
                        .sessionId(request.getSessionId())
                        .text(response)
                        .build();
                callback.onComplete(resp);
                return CompletableFuture.completedFuture(resp);
            }

            @Override
            public Flux<StreamEvent> chatStreamReactive(GatewayRequest request) {
                return Flux.just(
                        StreamEvent.textDelta(response),
                        StreamEvent.llmComplete(null)
                );
            }
        };
    }

    private static SessionManager noopSession() {
        return new SessionManager() {
            @Override
            public java.util.List<Map<String, String>> getSessionHistory(String sessionId) {
                return List.of();
            }
            @Override
            public Map<String, Object> getSessionSummary(String sessionId) {
                return Map.of();
            }
            @Override
            public void clearSession(String sessionId) {}
        };
    }

    private static StreamPostProcessor orderTrackingProcessor(String name, int order, List<String> tracker) {
        return new StreamPostProcessor() {
            @Override
            public String getName() { return name; }

            @Override
            public int getOrder() { return order; }

            @Override
            public Flux<StreamEvent> apply(Flux<StreamEvent> source) {
                tracker.add(name);
                return source;
            }
        };
    }
}
