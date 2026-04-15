package com.lightweightai.kernel.gateway;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.core.postprocess.StreamPostProcessor;
import com.lightweightai.kernel.core.postprocess.StreamPostProcessorPipeline;
import com.lightweightai.kernel.llm.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gateway - 事件分发完整性 & 后处理器管道测试
 *
 * 覆盖关键路径：
 * - dispatchEvent 对所有事件类型的分发
 * - 工具事件回调 (TOOL_CALL_START, TOOL_PROGRESS, TOOL_LOG, TOOL_RESULT, TOOL_ERROR)
 * - POST_PROCESS_DATA 事件分发
 * - ERROR 事件分发
 * - TRACE 事件不分发到 handler（被静默消费）
 * - 后处理器管道集成
 * - null 安全 (null delta, null chunk, null data)
 */
@DisplayName("Gateway - 事件分发与后处理器管道")
class GatewayEventDispatchTest {

    // ==================== 事件分发完整性 ====================

    @Nested
    @DisplayName("事件分发完整性")
    class EventDispatchTests {

        @Test
        @DisplayName("TEXT_DELTA 事件正确分发")
        void shouldDispatchTextDelta() throws Exception {
            ReactiveChatHandler handler = new ReactiveChatHandler(List.of(
                StreamEvent.textDelta("Hello"),
                StreamEvent.textDelta(" World"),
                StreamEvent.llmComplete(null)
            ));

            Gateway gateway = Gateway.builder().chatHandler(handler).build();
            GatewayRequest request = createRequest("test");

            CountDownLatch latch = new CountDownLatch(1);
            StringBuilder received = new StringBuilder();

            gateway.handleStream(request, new GatewayStreamHandler() {
                @Override
                public void onDelta(String delta) {
                    received.append(delta);
                }
                @Override
                public void onComplete(GatewayResponse response) {
                    latch.countDown();
                }
                @Override
                public void onError(Throwable error) {
                    latch.countDown();
                }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals("Hello World", received.toString());
        }

        @Test
        @DisplayName("TEXT_DELTA 带 metadata 分发")
        void shouldDispatchTextDeltaWithMetadata() throws Exception {
            ReactiveChatHandler handler = new ReactiveChatHandler(List.of(
                StreamEvent.textDelta("Hi", Map.of("emotion", "happy")),
                StreamEvent.llmComplete(null)
            ));

            Gateway gateway = Gateway.builder().chatHandler(handler).build();
            GatewayRequest request = createRequest("test");

            CountDownLatch latch = new CountDownLatch(1);
            List<Map<String, Object>> capturedMeta = new ArrayList<>();

            gateway.handleStream(request, new GatewayStreamHandler() {
                @Override
                public void onDelta(String delta) {
                    fail("Should call onDelta with metadata, not without");
                }
                @Override
                public void onDelta(String delta, Map<String, Object> metadata) {
                    capturedMeta.add(metadata);
                }
                @Override
                public void onComplete(GatewayResponse response) { latch.countDown(); }
                @Override
                public void onError(Throwable error) { latch.countDown(); }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(1, capturedMeta.size());
            assertEquals("happy", capturedMeta.get(0).get("emotion"));
        }

        @Test
        @DisplayName("TOOL_CALL_START 事件分发")
        void shouldDispatchToolCallStart() throws Exception {
            ToolCall toolCall = new ToolCall("tc1", "weather", Map.of("city", "BJ"));
            ReactiveChatHandler handler = new ReactiveChatHandler(List.of(
                StreamEvent.toolCallStart(toolCall),
                StreamEvent.llmComplete(null)
            ));

            Gateway gateway = Gateway.builder().chatHandler(handler).build();
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<ToolCall> captured = new AtomicReference<>();

            gateway.handleStream(createRequest("test"), new TestStreamHandler(latch) {
                @Override
                public void onToolCallStart(ToolCall tc) {
                    captured.set(tc);
                }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertNotNull(captured.get());
            assertEquals("weather", captured.get().getName());
        }

        @Test
        @DisplayName("TOOL_PROGRESS 事件分发")
        void shouldDispatchToolProgress() throws Exception {
            ToolResultChunk progressChunk = ToolResultChunk.progress("weather", "Loading", 50, 100);
            ReactiveChatHandler handler = new ReactiveChatHandler(List.of(
                StreamEvent.toolProgress(progressChunk),
                StreamEvent.llmComplete(null)
            ));

            Gateway gateway = Gateway.builder().chatHandler(handler).build();
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<ToolResultChunk> captured = new AtomicReference<>();

            gateway.handleStream(createRequest("test"), new TestStreamHandler(latch) {
                @Override
                public void onToolProgress(ToolResultChunk chunk) {
                    captured.set(chunk);
                }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertNotNull(captured.get());
            assertEquals(50, captured.get().getProgress());
        }

        @Test
        @DisplayName("TOOL_LOG 事件分发")
        void shouldDispatchToolLog() throws Exception {
            ToolResultChunk logChunk = ToolResultChunk.log("weather", "INFO", "Fetching data");
            ReactiveChatHandler handler = new ReactiveChatHandler(List.of(
                StreamEvent.toolLog(logChunk),
                StreamEvent.llmComplete(null)
            ));

            Gateway gateway = Gateway.builder().chatHandler(handler).build();
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<ToolResultChunk> captured = new AtomicReference<>();

            gateway.handleStream(createRequest("test"), new TestStreamHandler(latch) {
                @Override
                public void onToolLog(ToolResultChunk chunk) {
                    captured.set(chunk);
                }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertNotNull(captured.get());
            assertTrue(captured.get().getMessage().contains("Fetching data"));
        }

        @Test
        @DisplayName("TOOL_RESULT 事件分发")
        void shouldDispatchToolResult() throws Exception {
            ToolResultChunk resultChunk = ToolResultChunk.complete("weather",
                ToolResult.success("Sunny, 25C"));
            ReactiveChatHandler handler = new ReactiveChatHandler(List.of(
                StreamEvent.toolResult(resultChunk),
                StreamEvent.llmComplete(null)
            ));

            Gateway gateway = Gateway.builder().chatHandler(handler).build();
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<ToolResultChunk> captured = new AtomicReference<>();

            gateway.handleStream(createRequest("test"), new TestStreamHandler(latch) {
                @Override
                public void onToolResult(ToolResultChunk chunk) {
                    captured.set(chunk);
                }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertNotNull(captured.get());
            assertEquals(ToolResultChunk.ChunkType.COMPLETE, captured.get().getType());
        }

        @Test
        @DisplayName("TOOL_ERROR 事件分发")
        void shouldDispatchToolError() throws Exception {
            ToolResultChunk errorChunk = ToolResultChunk.error("weather", "API timeout");
            ReactiveChatHandler handler = new ReactiveChatHandler(List.of(
                StreamEvent.toolError(errorChunk),
                StreamEvent.llmComplete(null)
            ));

            Gateway gateway = Gateway.builder().chatHandler(handler).build();
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<ToolResultChunk> captured = new AtomicReference<>();

            gateway.handleStream(createRequest("test"), new TestStreamHandler(latch) {
                @Override
                public void onToolError(ToolResultChunk chunk) {
                    captured.set(chunk);
                }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertNotNull(captured.get());
            assertTrue(captured.get().getMessage().contains("API timeout"));
        }

        @Test
        @DisplayName("POST_PROCESS_DATA 事件分发")
        void shouldDispatchPostProcessData() throws Exception {
            Map<String, Object> data = Map.of("cardType", "weather", "city", "BJ");
            ReactiveChatHandler handler = new ReactiveChatHandler(List.of(
                StreamEvent.postProcessData("card", data),
                StreamEvent.llmComplete(null)
            ));

            Gateway gateway = Gateway.builder().chatHandler(handler).build();
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> capturedCategory = new AtomicReference<>();
            AtomicReference<Map<String, Object>> capturedData = new AtomicReference<>();

            gateway.handleStream(createRequest("test"), new TestStreamHandler(latch) {
                @Override
                public void onPostProcessData(String category, Map<String, Object> d) {
                    capturedCategory.set(category);
                    capturedData.set(d);
                }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals("card", capturedCategory.get());
            assertEquals("weather", capturedData.get().get("cardType"));
        }

        @Test
        @DisplayName("ERROR 事件分发到 handler.onError")
        void shouldDispatchErrorEvent() throws Exception {
            ReactiveChatHandler handler = new ReactiveChatHandler(List.of(
                StreamEvent.error(new RuntimeException("Stream error")),
                StreamEvent.llmComplete(null)
            ));

            Gateway gateway = Gateway.builder().chatHandler(handler).build();
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Throwable> capturedError = new AtomicReference<>();

            gateway.handleStream(createRequest("test"), new TestStreamHandler(latch) {
                @Override
                public void onError(Throwable error) {
                    capturedError.set(error);
                    // Don't count down here - onComplete will do it
                }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertNotNull(capturedError.get());
            assertTrue(capturedError.get().getMessage().contains("Stream error"));
        }

        @Test
        @DisplayName("null TEXT_DELTA 不分发")
        void shouldNotDispatchNullDelta() throws Exception {
            ReactiveChatHandler handler = new ReactiveChatHandler(List.of(
                StreamEvent.textDelta(null),
                StreamEvent.textDelta(""),
                StreamEvent.textDelta("valid"),
                StreamEvent.llmComplete(null)
            ));

            Gateway gateway = Gateway.builder().chatHandler(handler).build();
            CountDownLatch latch = new CountDownLatch(1);
            List<String> received = Collections.synchronizedList(new ArrayList<>());

            gateway.handleStream(createRequest("test"), new TestStreamHandler(latch) {
                @Override
                public void onDelta(String delta, Map<String, Object> metadata) {
                    received.add(delta);
                }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(1, received.size());
            assertEquals("valid", received.get(0));
        }

        @Test
        @DisplayName("null ToolCall 不分发")
        void shouldNotDispatchNullToolCall() throws Exception {
            ReactiveChatHandler handler = new ReactiveChatHandler(List.of(
                StreamEvent.toolCallStart(null),
                StreamEvent.llmComplete(null)
            ));

            Gateway gateway = Gateway.builder().chatHandler(handler).build();
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<ToolCall> captured = new AtomicReference<>();

            gateway.handleStream(createRequest("test"), new TestStreamHandler(latch) {
                @Override
                public void onToolCallStart(ToolCall tc) {
                    captured.set(tc);
                }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertNull(captured.get(), "Null toolCall should not be dispatched");
        }
    }

    // ==================== Reactive 流式调用 ====================

    @Nested
    @DisplayName("handleStreamReactive 测试")
    class ReactiveStreamTests {

        @Test
        @DisplayName("handleStreamReactive 返回完整事件流")
        void shouldReturnFullEventStream() {
            ReactiveChatHandler handler = new ReactiveChatHandler(List.of(
                StreamEvent.textDelta("Hello"),
                StreamEvent.llmComplete(null)
            ));

            Gateway gateway = Gateway.builder().chatHandler(handler).build();

            StepVerifier.create(gateway.handleStreamReactive(createRequest("test")))
                // First event is always a TRACE (user.message) injected by Gateway
                .assertNext(e -> assertEquals(StreamEvent.EventType.TRACE, e.getType()))
                .assertNext(e -> assertEquals(StreamEvent.EventType.TEXT_DELTA, e.getType()))
                .assertNext(e -> assertEquals(StreamEvent.EventType.LLM_COMPLETE, e.getType()))
                .verifyComplete();
        }

        @Test
        @DisplayName("handleStreamReactive 注入 user.message trace 事件")
        void shouldInjectUserMessageTrace() {
            ReactiveChatHandler handler = new ReactiveChatHandler(List.of(
                StreamEvent.llmComplete(null)
            ));

            Gateway gateway = Gateway.builder().chatHandler(handler).build();

            StepVerifier.create(gateway.handleStreamReactive(createRequest("hello")))
                .assertNext(e -> {
                    assertEquals(StreamEvent.EventType.TRACE, e.getType());
                    assertEquals("user.message", e.getTracePhase());
                    assertEquals("hello", e.getTraceMessage());
                })
                .assertNext(e -> assertEquals(StreamEvent.EventType.LLM_COMPLETE, e.getType()))
                .verifyComplete();
        }
    }

    // ==================== 后处理器管道集成 ====================

    @Nested
    @DisplayName("后处理器管道集成")
    class PostProcessorPipelineTests {

        @Test
        @DisplayName("后处理器可以注入额外事件")
        void postProcessorShouldInjectEvents() {
            ReactiveChatHandler handler = new ReactiveChatHandler(List.of(
                StreamEvent.textDelta("response"),
                StreamEvent.llmComplete(null)
            ));

            // 创建一个后处理器：在流末尾追加 POST_PROCESS_DATA 事件
            StreamPostProcessor appendingProcessor = new StreamPostProcessor() {
                @Override
                public String getName() { return "appending"; }
                @Override
                public Flux<StreamEvent> apply(Flux<StreamEvent> source) {
                    return Flux.concat(
                        source,
                        Flux.just(StreamEvent.postProcessData("injected",
                            Map.of("key", "value")))
                    );
                }
            };

            Gateway gateway = Gateway.builder()
                .chatHandler(handler)
                .addPostProcessor(appendingProcessor)
                .build();

            List<StreamEvent> events = new ArrayList<>();
            StepVerifier.create(gateway.handleStreamReactive(createRequest("test")))
                .recordWith(() -> events)
                .thenConsumeWhile(e -> true)
                .verifyComplete();

            // 应包含后处理器注入的事件
            boolean hasInjected = events.stream()
                .anyMatch(e -> e.getType() == StreamEvent.EventType.POST_PROCESS_DATA
                    && "injected".equals(e.getCategory()));
            assertTrue(hasInjected, "Should have injected POST_PROCESS_DATA event");
        }

        @Test
        @DisplayName("多个后处理器按 order 排序执行")
        void postProcessorsShouldBeOrderSorted() {
            ReactiveChatHandler handler = new ReactiveChatHandler(List.of(
                StreamEvent.textDelta("text"),
                StreamEvent.llmComplete(null)
            ));

            List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());

            StreamPostProcessor first = new StreamPostProcessor() {
                @Override
                public String getName() { return "first"; }
                @Override
                public int getOrder() { return 10; }
                @Override
                public Flux<StreamEvent> apply(Flux<StreamEvent> source) {
                    return source.doOnSubscribe(s -> executionOrder.add("first"));
                }
            };

            StreamPostProcessor second = new StreamPostProcessor() {
                @Override
                public String getName() { return "second"; }
                @Override
                public int getOrder() { return 20; }
                @Override
                public Flux<StreamEvent> apply(Flux<StreamEvent> source) {
                    return source.doOnSubscribe(s -> executionOrder.add("second"));
                }
            };

            // 注意：故意以逆序添加
            Gateway gateway = Gateway.builder()
                .chatHandler(handler)
                .addPostProcessor(second)
                .addPostProcessor(first)
                .build();

            StepVerifier.create(gateway.handleStreamReactive(createRequest("test")))
                .thenConsumeWhile(e -> true)
                .verifyComplete();

            // first (order=10) 应先于 second (order=20)
            assertEquals(List.of("first", "second"), executionOrder);
        }

        @Test
        @DisplayName("无后处理器时正常工作")
        void shouldWorkWithoutPostProcessors() {
            ReactiveChatHandler handler = new ReactiveChatHandler(List.of(
                StreamEvent.textDelta("ok"),
                StreamEvent.llmComplete(null)
            ));

            Gateway gateway = Gateway.builder().chatHandler(handler).build();
            assertNull(gateway.getPostProcessorPipeline());

            StepVerifier.create(gateway.handleStreamReactive(createRequest("test")))
                .assertNext(e -> assertEquals(StreamEvent.EventType.TRACE, e.getType()))
                .assertNext(e -> assertEquals("ok", e.getTextDelta()))
                .assertNext(e -> assertEquals(StreamEvent.EventType.LLM_COMPLETE, e.getType()))
                .verifyComplete();
        }

        @Test
        @DisplayName("通过 postProcessors(pipeline) 设置管道")
        void shouldSetPipelineDirectly() {
            ReactiveChatHandler handler = new ReactiveChatHandler(List.of(
                StreamEvent.llmComplete(null)
            ));

            StreamPostProcessorPipeline pipeline = StreamPostProcessorPipeline.builder()
                .add(new StreamPostProcessor() {
                    @Override
                    public String getName() { return "direct"; }
                    @Override
                    public Flux<StreamEvent> apply(Flux<StreamEvent> source) { return source; }
                })
                .build();

            Gateway gateway = Gateway.builder()
                .chatHandler(handler)
                .postProcessors(pipeline)
                .build();

            assertNotNull(gateway.getPostProcessorPipeline());
        }
    }

    // ==================== 辅助方法 ====================

    private GatewayRequest createRequest(String message) {
        return GatewayRequest.builder()
            .sessionId("session-1")
            .message(message)
            .build();
    }

    // ==================== Mock 实现 ====================

    /**
     * ChatHandler 直接返回 reactive stream
     */
    private static class ReactiveChatHandler implements ChatHandler {
        private final List<StreamEvent> events;

        ReactiveChatHandler(List<StreamEvent> events) {
            this.events = events;
        }

        @Override
        public GatewayResponse chat(GatewayRequest request) {
            return GatewayResponse.builder()
                .requestId(request.getRequestId())
                .sessionId(request.getSessionId())
                .text("sync")
                .build();
        }

        @Override
        public CompletableFuture<GatewayResponse> chatStream(GatewayRequest request, StreamCallback callback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Flux<StreamEvent> chatStreamReactive(GatewayRequest request) {
            return Flux.fromIterable(events);
        }
    }

    /**
     * 基础 StreamHandler，仅处理 onComplete
     */
    private abstract static class TestStreamHandler implements GatewayStreamHandler {
        private final CountDownLatch latch;

        TestStreamHandler(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void onDelta(String delta) {}

        @Override
        public void onDelta(String delta, Map<String, Object> metadata) {}

        @Override
        public void onComplete(GatewayResponse response) {
            latch.countDown();
        }

        @Override
        public void onError(Throwable error) {
            latch.countDown();
        }
    }
}
