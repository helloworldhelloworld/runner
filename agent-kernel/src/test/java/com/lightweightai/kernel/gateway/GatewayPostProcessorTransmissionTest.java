package com.lightweightai.kernel.gateway;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.postprocess.StreamPostProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 传输链验证：Gateway → PostProcessorPipeline → 输出流
 *
 * 验证 Gateway.handleStreamReactive 正确应用后处理器管道，
 * 后处理器注入的事件必须出现在最终输出中。
 */
@DisplayName("Acceptance: Gateway 后处理器管道传输验证")
class GatewayPostProcessorTransmissionTest {

    @Test
    @DisplayName("后处理器注入的 POST_PROCESS_DATA 事件出现在输出流中")
    void postProcessorInjectsEventsIntoOutputStream() {
        StreamPostProcessor cardProcessor = new StreamPostProcessor() {
            @Override public String getName() { return "test-card"; }
            @Override public int getOrder() { return 10; }
            @Override public Flux<StreamEvent> apply(Flux<StreamEvent> source) {
                return source.concatWith(
                        Flux.just(StreamEvent.postProcessData("card", Map.of("title", "Weather"))));
            }
        };

        Gateway gateway = Gateway.builder()
                .chatHandler(simpleChatHandler("Hello"))
                .addPostProcessor(cardProcessor)
                .build();

        List<StreamEvent> events = gateway.handleStreamReactive(
                GatewayRequest.builder().message("weather?").sessionId("s1").build()
        ).collectList().block();

        assertNotNull(events);

        List<StreamEvent> postProcessEvents = events.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.POST_PROCESS_DATA)
                .toList();
        assertEquals(1, postProcessEvents.size());
        assertEquals("card", postProcessEvents.get(0).getCategory());
        assertEquals("Weather", postProcessEvents.get(0).getData().get("title"));
    }

    @Test
    @DisplayName("多个后处理器按 order 排序执行")
    void multiplePostProcessorsExecuteInOrder() {
        StreamPostProcessor first = new StreamPostProcessor() {
            @Override public String getName() { return "first"; }
            @Override public int getOrder() { return 10; }
            @Override public Flux<StreamEvent> apply(Flux<StreamEvent> source) {
                return source.concatWith(
                        Flux.just(StreamEvent.postProcessData("step", Map.of("order", "1"))));
            }
        };

        StreamPostProcessor second = new StreamPostProcessor() {
            @Override public String getName() { return "second"; }
            @Override public int getOrder() { return 20; }
            @Override public Flux<StreamEvent> apply(Flux<StreamEvent> source) {
                return source.concatWith(
                        Flux.just(StreamEvent.postProcessData("step", Map.of("order", "2"))));
            }
        };

        Gateway gateway = Gateway.builder()
                .chatHandler(simpleChatHandler("ok"))
                .addPostProcessor(second)
                .addPostProcessor(first)
                .build();

        List<StreamEvent> events = gateway.handleStreamReactive(
                GatewayRequest.builder().message("hi").sessionId("s1").build()
        ).collectList().block();

        assertNotNull(events);
        List<StreamEvent> steps = events.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.POST_PROCESS_DATA
                        && "step".equals(e.getCategory()))
                .toList();

        assertEquals(2, steps.size());
        assertEquals("1", steps.get(0).getData().get("order"));
        assertEquals("2", steps.get(1).getData().get("order"));
    }

    @Test
    @DisplayName("无后处理器时 Gateway 正常输出 chatHandler 事件")
    void noPostProcessorsPassthrough() {
        Gateway gateway = Gateway.builder()
                .chatHandler(simpleChatHandler("reply"))
                .build();

        List<StreamEvent> events = gateway.handleStreamReactive(
                GatewayRequest.builder().message("hi").sessionId("s1").build()
        ).collectList().block();

        assertNotNull(events);
        assertTrue(events.stream().anyMatch(e -> e.getType() == StreamEvent.EventType.TEXT_DELTA));
        assertNull(gateway.getPostProcessorPipeline());
    }

    private static ChatHandler simpleChatHandler(String reply) {
        return new ChatHandler() {
            @Override public GatewayResponse chat(GatewayRequest request) {
                return GatewayResponse.builder()
                        .requestId(request.getRequestId())
                        .sessionId(request.getSessionId())
                        .text(reply)
                        .build();
            }
            @Override public CompletableFuture<GatewayResponse> chatStream(
                    GatewayRequest request, StreamCallback callback) {
                return CompletableFuture.completedFuture(chat(request));
            }
            @Override public Flux<StreamEvent> chatStreamReactive(GatewayRequest request) {
                return Flux.just(StreamEvent.textDelta(reply));
            }
        };
    }
}
