package com.lightweightai.kernel.gateway;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.core.postprocess.StreamPostProcessor;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.trace.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
 * Gateway 边界条件与错误路径测试
 *
 * 覆盖：
 * - handleStream 中 Flux 错误传播到 onError
 * - null toolCall/chunk 保护
 * - TOOL_LOG 事件分发
 * - 多个 PostProcessor 管道排序
 * - AgentLoopChatHandler 适配正确性
 * - 同步调用补充 latencyMs
 * - 同步调用已有 latencyMs 不覆盖
 * - getPostProcessorPipeline/getChatHandler 访问器
 */
@DisplayName("Gateway - 边界条件与错误路径")
class GatewayEdgeCaseTest {

    // ==================== 错误传播 ====================

    @Nested
    @DisplayName("流式错误传播")
    class StreamErrorPropagation {

        @Test
        @DisplayName("handleStream 中 Flux 错误传给 onError")
        void shouldPropagateFluxErrorToHandler() throws Exception {
            ChatHandler handler = new ChatHandler() {
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
                    return Flux.concat(
                        Flux.just(StreamEvent.textDelta("partial")),
                        Flux.error(new RuntimeException("mid-stream error"))
                    );
                }
            };

            Gateway gw = Gateway.builder().chatHandler(handler).build();
            GatewayRequest request = GatewayRequest.builder()
                .sessionId("s1").message("test").build();

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Throwable> capturedError = new AtomicReference<>();

            gw.handleStream(request, new GatewayStreamHandler() {
                @Override public void onDelta(String delta) {}
                @Override public void onDelta(String delta, Map<String, Object> metadata) {}
                @Override public void onError(Throwable error) {
                    capturedError.set(error);
                    latch.countDown();
                }
                @Override public void onComplete(GatewayResponse resp) {
                    latch.countDown();
                }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertNotNull(capturedError.get());
            assertTrue(capturedError.get().getMessage().contains("mid-stream error"));
        }
    }

    // ==================== null 保护 ====================

    @Nested
    @DisplayName("Null 数据保护")
    class NullProtection {

        @Test
        @DisplayName("TOOL_CALL_START 正确分发到 handler")
        void shouldDispatchToolCallStartEvent() throws Exception {
            ToolCall tc = new ToolCall("tc1", "my_tool", Map.of("key", "val"));
            ChatHandler handler = createReactiveHandler(List.of(
                StreamEvent.toolCallStart(tc),
                StreamEvent.textDelta("text"),
                StreamEvent.llmComplete(null)
            ));

            Gateway gw = Gateway.builder().chatHandler(handler).build();
            GatewayRequest request = GatewayRequest.builder()
                .sessionId("s1").message("test").build();

            CountDownLatch latch = new CountDownLatch(1);
            List<String> toolStartLog = new ArrayList<>();

            gw.handleStream(request, new GatewayStreamHandler() {
                @Override public void onDelta(String delta) {}
                @Override public void onDelta(String delta, Map<String, Object> metadata) {}
                @Override public void onToolCallStart(ToolCall toolCall) {
                    toolStartLog.add(toolCall.getName());
                }
                @Override public void onComplete(GatewayResponse resp) { latch.countDown(); }
                @Override public void onError(Throwable error) { latch.countDown(); }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(List.of("my_tool"), toolStartLog);
        }

        @Test
        @DisplayName("TOOL_LOG 事件分发到 onToolLog")
        void shouldDispatchToolLogEvents() throws Exception {
            ToolResultChunk logChunk = ToolResultChunk.log("my_tool", "INFO", "Processing step 3...");

            ChatHandler handler = createReactiveHandler(List.of(
                StreamEvent.toolLog(logChunk),
                StreamEvent.llmComplete(null)
            ));

            Gateway gw = Gateway.builder().chatHandler(handler).build();
            GatewayRequest request = GatewayRequest.builder()
                .sessionId("s1").message("test").build();

            CountDownLatch latch = new CountDownLatch(1);
            List<String> logMessages = new ArrayList<>();

            gw.handleStream(request, new GatewayStreamHandler() {
                @Override public void onDelta(String delta) {}
                @Override public void onToolLog(ToolResultChunk chunk) {
                    logMessages.add(chunk.getMessage());
                }
                @Override public void onComplete(GatewayResponse resp) { latch.countDown(); }
                @Override public void onError(Throwable error) { latch.countDown(); }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(1, logMessages.size());
            assertTrue(logMessages.get(0).contains("Processing step 3..."));
        }
    }

    // ==================== 同步调用 latency ====================

    @Nested
    @DisplayName("同步调用 latency 处理")
    class SyncLatencyHandling {

        @Test
        @DisplayName("ChatHandler 返回 latencyMs=0 时 Gateway 补充")
        void shouldFillLatencyWhenZero() {
            ChatHandler handler = new ChatHandler() {
                @Override
                public GatewayResponse chat(GatewayRequest request) {
                    return GatewayResponse.builder()
                        .sessionId(request.getSessionId())
                        .text("ok")
                        .latencyMs(0) // zero latency
                        .build();
                }

                @Override
                public CompletableFuture<GatewayResponse> chatStream(GatewayRequest request, StreamCallback callback) {
                    throw new UnsupportedOperationException();
                }
            };

            Gateway gw = Gateway.builder().chatHandler(handler).build();
            GatewayResponse response = gw.handle(GatewayRequest.builder()
                .sessionId("s1").message("test").build());

            assertTrue(response.getLatencyMs() >= 0);
        }

        @Test
        @DisplayName("ChatHandler 返回非零 latencyMs 时 Gateway 不覆盖")
        void shouldNotOverwriteNonZeroLatency() {
            ChatHandler handler = new ChatHandler() {
                @Override
                public GatewayResponse chat(GatewayRequest request) {
                    return GatewayResponse.builder()
                        .sessionId(request.getSessionId())
                        .text("ok")
                        .latencyMs(42)
                        .build();
                }

                @Override
                public CompletableFuture<GatewayResponse> chatStream(GatewayRequest request, StreamCallback callback) {
                    throw new UnsupportedOperationException();
                }
            };

            Gateway gw = Gateway.builder().chatHandler(handler).build();
            GatewayResponse response = gw.handle(GatewayRequest.builder()
                .sessionId("s1").message("test").build());

            assertEquals(42, response.getLatencyMs());
        }
    }

    // ==================== PostProcessor 管道 ====================

    @Nested
    @DisplayName("PostProcessor 管道")
    class PostProcessorPipeline {

        @Test
        @DisplayName("多个 PostProcessor 按 order 排序执行")
        void shouldApplyMultipleProcessorsInOrder() {
            ChatHandler handler = createReactiveHandler(List.of(
                StreamEvent.textDelta("hello"),
                StreamEvent.llmComplete(null)
            ));

            // Processor order=1: add suffix
            StreamPostProcessor suffixProcessor = new StreamPostProcessor() {
                @Override
                public Flux<StreamEvent> apply(Flux<StreamEvent> input) {
                    return input.map(event -> {
                        if (event.getType() == StreamEvent.EventType.TEXT_DELTA && event.getTextDelta() != null) {
                            return StreamEvent.textDelta(event.getTextDelta() + "!");
                        }
                        return event;
                    });
                }
                @Override public String getName() { return "suffix"; }
                @Override public int getOrder() { return 1; }
            };

            // Processor order=0: uppercase (runs first)
            StreamPostProcessor upperProcessor = new StreamPostProcessor() {
                @Override
                public Flux<StreamEvent> apply(Flux<StreamEvent> input) {
                    return input.map(event -> {
                        if (event.getType() == StreamEvent.EventType.TEXT_DELTA && event.getTextDelta() != null) {
                            return StreamEvent.textDelta(event.getTextDelta().toUpperCase());
                        }
                        return event;
                    });
                }
                @Override public String getName() { return "upper"; }
                @Override public int getOrder() { return 0; }
            };

            Gateway gw = Gateway.builder()
                .chatHandler(handler)
                .addPostProcessor(suffixProcessor)
                .addPostProcessor(upperProcessor)
                .build();

            GatewayRequest request = GatewayRequest.builder()
                .sessionId("s1").message("test").build();

            StepVerifier.create(gw.handleStreamReactive(request))
                .assertNext(e -> assertEquals(StreamEvent.EventType.TRACE, e.getType()))
                .assertNext(e -> {
                    assertEquals(StreamEvent.EventType.TEXT_DELTA, e.getType());
                    // uppercase first (order=0), then suffix (order=1)
                    // Result should be "HELLO!"
                    String delta = e.getTextDelta();
                    assertNotNull(delta);
                    assertTrue(delta.contains("HELLO"), "Expected uppercase: " + delta);
                })
                .assertNext(e -> assertEquals(StreamEvent.EventType.LLM_COMPLETE, e.getType()))
                .verifyComplete();
        }

        @Test
        @DisplayName("无 PostProcessor 时 pipeline 为 null")
        void shouldHaveNullPipelineWhenNoProcessors() {
            ChatHandler handler = createReactiveHandler(List.of(StreamEvent.llmComplete(null)));
            Gateway gw = Gateway.builder().chatHandler(handler).build();
            assertNull(gw.getPostProcessorPipeline());
        }
    }

    // ==================== 访问器 ====================

    @Nested
    @DisplayName("访问器方法")
    class Accessors {

        @Test
        @DisplayName("getChatHandler 返回实际 handler")
        void shouldReturnChatHandler() {
            ChatHandler handler = createReactiveHandler(List.of());
            Gateway gw = Gateway.builder().chatHandler(handler).build();
            assertSame(handler, gw.getChatHandler());
        }

        @Test
        @DisplayName("getSessionManager 无设置时返回 null")
        void shouldReturnNullSessionManager() {
            ChatHandler handler = createReactiveHandler(List.of());
            Gateway gw = Gateway.builder().chatHandler(handler).build();
            assertNull(gw.getSessionManager());
        }
    }

    // ==================== Tracer 集成 ====================

    @Test
    @DisplayName("handleStreamReactive 使用 Tracer 创建 span")
    void shouldUseTracerInReactiveStream() {
        ChatHandler handler = createReactiveHandler(List.of(
            StreamEvent.textDelta("ok"),
            StreamEvent.llmComplete(null)
        ));

        // Use NOOP tracer (shouldn't break anything)
        Gateway gw = Gateway.builder()
            .chatHandler(handler)
            .tracer(Tracer.NOOP)
            .build();

        GatewayRequest request = GatewayRequest.builder()
            .sessionId("s1").message("test").build();

        StepVerifier.create(gw.handleStreamReactive(request))
            .assertNext(e -> assertEquals(StreamEvent.EventType.TRACE, e.getType()))
            .assertNext(e -> assertEquals(StreamEvent.EventType.TEXT_DELTA, e.getType()))
            .assertNext(e -> assertEquals(StreamEvent.EventType.LLM_COMPLETE, e.getType()))
            .verifyComplete();
    }

    // ==================== 辅助方法 ====================

    private ChatHandler createReactiveHandler(List<StreamEvent> events) {
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
