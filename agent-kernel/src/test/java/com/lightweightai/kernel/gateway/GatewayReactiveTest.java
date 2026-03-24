package com.lightweightai.kernel.gateway;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.core.postprocess.StreamPostProcessor;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gateway Reactive 流式路径 + 事件分发测试
 *
 * 覆盖关键路径：
 * - handleStreamReactive 返回完整事件流
 * - handleStream 事件分发到 GatewayStreamHandler
 * - PostProcessor 管道集成
 * - ChatHandler 默认 reactive 桥接
 * - 工具事件分发（TOOL_CALL_START, TOOL_PROGRESS, TOOL_RESULT, TOOL_ERROR）
 */
@DisplayName("Gateway - Reactive 流式路径")
class GatewayReactiveTest {

    @Test
    @DisplayName("handleStreamReactive 返回完整事件流")
    void shouldReturnReactiveEventStream() {
        ChatHandler handler = createReactiveChatHandler(List.of(
            StreamEvent.textDelta("Hello "),
            StreamEvent.textDelta("World"),
            StreamEvent.llmComplete(null)
        ));

        Gateway gw = Gateway.builder().chatHandler(handler).build();
        GatewayRequest request = GatewayRequest.builder()
            .sessionId("s1").message("Hi").build();

        StepVerifier.create(gw.handleStreamReactive(request))
            // First event is TRACE (user message)
            .assertNext(e -> assertEquals(StreamEvent.EventType.TRACE, e.getType()))
            .assertNext(e -> {
                assertEquals(StreamEvent.EventType.TEXT_DELTA, e.getType());
                assertEquals("Hello ", e.getTextDelta());
            })
            .assertNext(e -> assertEquals(StreamEvent.EventType.TEXT_DELTA, e.getType()))
            .assertNext(e -> assertEquals(StreamEvent.EventType.LLM_COMPLETE, e.getType()))
            .verifyComplete();
    }

    @Test
    @DisplayName("handleStream 分发 TEXT_DELTA 事件")
    void shouldDispatchTextDeltaEvents() throws Exception {
        ChatHandler handler = createReactiveChatHandler(List.of(
            StreamEvent.textDelta("A"),
            StreamEvent.textDelta("B"),
            StreamEvent.llmComplete(null)
        ));

        Gateway gw = Gateway.builder().chatHandler(handler).build();
        GatewayRequest request = GatewayRequest.builder()
            .sessionId("s1").message("test").build();

        CountDownLatch latch = new CountDownLatch(1);
        List<String> deltas = new ArrayList<>();
        AtomicReference<GatewayResponse> response = new AtomicReference<>();

        gw.handleStream(request, new GatewayStreamHandler() {
            @Override
            public void onDelta(String delta) {
                // default - not used when metadata variant is called
            }
            @Override
            public void onDelta(String delta, Map<String, Object> metadata) {
                deltas.add(delta);
            }
            @Override
            public void onComplete(GatewayResponse resp) {
                response.set(resp);
                latch.countDown();
            }
            @Override
            public void onError(Throwable error) {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("A", "B"), deltas);
        assertEquals("AB", response.get().getText());
    }

    @Test
    @DisplayName("handleStream 分发工具事件")
    void shouldDispatchToolEvents() throws Exception {
        ToolCall toolCall = new ToolCall("tc1", "weather", Map.of());
        ToolResultChunk progressChunk = ToolResultChunk.progress("weather", "Fetching...", 50, 100);
        ToolResultChunk resultChunk = ToolResultChunk.complete("weather", ToolResult.success("Sunny"));
        ToolResultChunk errorChunk = ToolResultChunk.error("weather", "Timeout");

        ChatHandler handler = createReactiveChatHandler(List.of(
            StreamEvent.toolCallStart(toolCall),
            StreamEvent.toolProgress(progressChunk),
            StreamEvent.toolResult(resultChunk),
            StreamEvent.toolError(errorChunk),
            StreamEvent.llmComplete(null)
        ));

        Gateway gw = Gateway.builder().chatHandler(handler).build();
        GatewayRequest request = GatewayRequest.builder()
            .sessionId("s1").message("weather").build();

        CountDownLatch latch = new CountDownLatch(1);
        List<String> eventLog = new ArrayList<>();

        gw.handleStream(request, new GatewayStreamHandler() {
            @Override public void onDelta(String delta) {}
            @Override public void onToolCallStart(ToolCall tc) { eventLog.add("START:" + tc.getName()); }
            @Override public void onToolProgress(ToolResultChunk chunk) { eventLog.add("PROGRESS"); }
            @Override public void onToolResult(ToolResultChunk chunk) { eventLog.add("RESULT"); }
            @Override public void onToolError(ToolResultChunk chunk) { eventLog.add("ERROR"); }
            @Override public void onComplete(GatewayResponse resp) { latch.countDown(); }
            @Override public void onError(Throwable error) { latch.countDown(); }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("START:weather", "PROGRESS", "RESULT", "ERROR"), eventLog);
    }

    @Test
    @DisplayName("PostProcessor 管道在 reactive 流中生效")
    void shouldApplyPostProcessorPipeline() {
        ChatHandler handler = createReactiveChatHandler(List.of(
            StreamEvent.textDelta("hello"),
            StreamEvent.llmComplete(null)
        ));

        // 添加一个将文本转大写的 post-processor
        StreamPostProcessor upperCaseProcessor = new StreamPostProcessor() {
            @Override
            public Flux<StreamEvent> apply(Flux<StreamEvent> input) {
                return input.map(event -> {
                    if (event.getType() == StreamEvent.EventType.TEXT_DELTA && event.getTextDelta() != null) {
                        return StreamEvent.textDelta(event.getTextDelta().toUpperCase());
                    }
                    return event;
                });
            }
            @Override public String getName() { return "uppercase"; }
            @Override public int getOrder() { return 0; }
        };

        Gateway gw = Gateway.builder()
            .chatHandler(handler)
            .addPostProcessor(upperCaseProcessor)
            .build();

        GatewayRequest request = GatewayRequest.builder()
            .sessionId("s1").message("test").build();

        StepVerifier.create(gw.handleStreamReactive(request))
            .assertNext(e -> assertEquals(StreamEvent.EventType.TRACE, e.getType())) // trace event
            .assertNext(e -> {
                assertEquals(StreamEvent.EventType.TEXT_DELTA, e.getType());
                assertEquals("HELLO", e.getTextDelta());
            })
            .assertNext(e -> assertEquals(StreamEvent.EventType.LLM_COMPLETE, e.getType()))
            .verifyComplete();
    }

    @Test
    @DisplayName("ChatHandler 默认 chatStreamReactive 桥接 chatStream")
    void shouldBridgeChatStreamToReactive() {
        // 使用只实现 chat + chatStream 的 handler，依赖默认 chatStreamReactive
        ChatHandler handler = new ChatHandler() {
            @Override
            public GatewayResponse chat(GatewayRequest request) {
                return GatewayResponse.builder()
                    .text("sync").sessionId(request.getSessionId()).build();
            }

            @Override
            public CompletableFuture<GatewayResponse> chatStream(GatewayRequest request, StreamCallback callback) {
                callback.onDelta("streamed", Map.of("key", "val"));
                GatewayResponse resp = GatewayResponse.builder()
                    .text("streamed").sessionId(request.getSessionId()).build();
                callback.onComplete(resp);
                return CompletableFuture.completedFuture(resp);
            }
        };

        Gateway gw = Gateway.builder().chatHandler(handler).build();
        GatewayRequest request = GatewayRequest.builder()
            .sessionId("s1").message("test").build();

        StepVerifier.create(gw.handleStreamReactive(request))
            .assertNext(e -> assertEquals(StreamEvent.EventType.TRACE, e.getType()))
            .assertNext(e -> {
                assertEquals(StreamEvent.EventType.TEXT_DELTA, e.getType());
                assertEquals("streamed", e.getTextDelta());
                assertEquals("val", e.getMetadata().get("key"));
            })
            .assertNext(e -> assertEquals(StreamEvent.EventType.LLM_COMPLETE, e.getType()))
            .verifyComplete();
    }

    @Test
    @DisplayName("handleStream 分发 POST_PROCESS_DATA 事件")
    void shouldDispatchPostProcessDataEvent() throws Exception {
        ChatHandler handler = createReactiveChatHandler(List.of(
            StreamEvent.postProcessData("card", Map.of("title", "Info")),
            StreamEvent.llmComplete(null)
        ));

        Gateway gw = Gateway.builder().chatHandler(handler).build();
        GatewayRequest request = GatewayRequest.builder()
            .sessionId("s1").message("test").build();

        CountDownLatch latch = new CountDownLatch(1);
        List<String> categories = new ArrayList<>();

        gw.handleStream(request, new GatewayStreamHandler() {
            @Override public void onDelta(String delta) {}
            @Override
            public void onPostProcessData(String category, Map<String, Object> data) {
                categories.add(category);
            }
            @Override public void onComplete(GatewayResponse resp) { latch.countDown(); }
            @Override public void onError(Throwable error) { latch.countDown(); }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("card"), categories);
    }

    @Test
    @DisplayName("handleStream 分发 ERROR 事件")
    void shouldDispatchErrorEvent() throws Exception {
        ChatHandler handler = createReactiveChatHandler(List.of(
            StreamEvent.error(new RuntimeException("stream error")),
            StreamEvent.llmComplete(null)
        ));

        Gateway gw = Gateway.builder().chatHandler(handler).build();
        GatewayRequest request = GatewayRequest.builder()
            .sessionId("s1").message("test").build();

        CountDownLatch latch = new CountDownLatch(1);
        List<Throwable> errors = new ArrayList<>();

        gw.handleStream(request, new GatewayStreamHandler() {
            @Override public void onDelta(String delta) {}
            @Override public void onError(Throwable error) {
                errors.add(error);
            }
            @Override public void onComplete(GatewayResponse resp) { latch.countDown(); }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertFalse(errors.isEmpty());
    }

    @Test
    @DisplayName("null delta 和空 delta 被忽略")
    void shouldIgnoreNullAndEmptyDeltas() throws Exception {
        ChatHandler handler = createReactiveChatHandler(List.of(
            StreamEvent.textDelta(null),
            StreamEvent.textDelta(""),
            StreamEvent.textDelta("real"),
            StreamEvent.llmComplete(null)
        ));

        Gateway gw = Gateway.builder().chatHandler(handler).build();
        GatewayRequest request = GatewayRequest.builder()
            .sessionId("s1").message("test").build();

        CountDownLatch latch = new CountDownLatch(1);
        List<String> deltas = new ArrayList<>();

        gw.handleStream(request, new GatewayStreamHandler() {
            @Override public void onDelta(String delta) {}
            @Override public void onDelta(String delta, Map<String, Object> metadata) {
                deltas.add(delta);
            }
            @Override public void onComplete(GatewayResponse resp) { latch.countDown(); }
            @Override public void onError(Throwable error) { latch.countDown(); }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("real"), deltas);
    }

    // ==================== 辅助方法 ====================

    private ChatHandler createReactiveChatHandler(List<StreamEvent> events) {
        return new ChatHandler() {
            @Override
            public GatewayResponse chat(GatewayRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<GatewayResponse> chatStream(GatewayRequest request, StreamCallback callback) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Flux<StreamEvent> chatStreamReactive(GatewayRequest request) {
                return Flux.fromIterable(events);
            }
        };
    }
}
