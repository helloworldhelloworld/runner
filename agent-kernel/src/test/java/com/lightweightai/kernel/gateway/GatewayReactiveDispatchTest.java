package com.lightweightai.kernel.gateway;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.core.postprocess.StreamPostProcessor;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Gateway - Reactive dispatch & event routing")
class GatewayReactiveDispatchTest {

    @Nested
    @DisplayName("handleStreamReactive")
    class HandleStreamReactive {

        @Test
        @DisplayName("prepends user trace with sessionId and requestId in data")
        void prependsUserTraceWithRequestMetadata() {
            ChatHandler handler = reactiveChatHandler(List.of(
                StreamEvent.textDelta("hi"),
                StreamEvent.llmComplete(null)
            ));

            Gateway gw = Gateway.builder().chatHandler(handler).build();
            GatewayRequest request = GatewayRequest.builder()
                .requestId("req-42")
                .sessionId("sess-7")
                .message("hello")
                .build();

            StepVerifier.create(gw.handleStreamReactive(request))
                .assertNext(e -> {
                    assertEquals(StreamEvent.EventType.TRACE, e.getType());
                    assertEquals("user.message", e.getTracePhase());
                    assertEquals("hello", e.getTraceMessage());
                    assertEquals("sess-7", e.getData().get("sessionId"));
                    assertEquals("req-42", e.getData().get("requestId"));
                })
                .assertNext(e -> assertEquals(StreamEvent.EventType.TEXT_DELTA, e.getType()))
                .assertNext(e -> assertEquals(StreamEvent.EventType.LLM_COMPLETE, e.getType()))
                .verifyComplete();
        }

        @Test
        @DisplayName("delegates directly to ChatHandler.chatStreamReactive")
        void delegatesToChatHandlerReactiveStream() {
            List<StreamEvent> source = List.of(
                StreamEvent.textDelta("a"),
                StreamEvent.textDelta("b"),
                StreamEvent.textDelta("c"),
                StreamEvent.llmComplete(null)
            );
            ChatHandler handler = reactiveChatHandler(source);

            Gateway gw = Gateway.builder().chatHandler(handler).build();
            GatewayRequest request = GatewayRequest.builder()
                .sessionId("s1").message("test").build();

            List<StreamEvent.EventType> types = new ArrayList<>();
            gw.handleStreamReactive(request)
                .doOnNext(e -> types.add(e.getType()))
                .blockLast();

            assertEquals(List.of(
                StreamEvent.EventType.TRACE,
                StreamEvent.EventType.TEXT_DELTA,
                StreamEvent.EventType.TEXT_DELTA,
                StreamEvent.EventType.TEXT_DELTA,
                StreamEvent.EventType.LLM_COMPLETE
            ), types);
        }

        @Test
        @DisplayName("propagates Flux error from ChatHandler")
        void propagatesFluxErrorFromChatHandler() {
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
                    return Flux.error(new RuntimeException("provider down"));
                }
            };

            Gateway gw = Gateway.builder().chatHandler(handler).build();
            GatewayRequest request = GatewayRequest.builder()
                .sessionId("s1").message("x").build();

            StepVerifier.create(gw.handleStreamReactive(request))
                .assertNext(e -> assertEquals(StreamEvent.EventType.TRACE, e.getType()))
                .expectErrorMatches(t -> t instanceof RuntimeException && "provider down".equals(t.getMessage()))
                .verify();
        }
    }

    @Nested
    @DisplayName("dispatchEvent - TEXT_DELTA")
    class DispatchTextDelta {

        @Test
        @DisplayName("passes metadata map through to handler.onDelta")
        void passesMetadataToOnDelta() throws Exception {
            Map<String, Object> meta = Map.of("emotion", "calm", "confidence", 0.95);
            ChatHandler handler = reactiveChatHandler(List.of(
                StreamEvent.textDelta("word", meta),
                StreamEvent.llmComplete(null)
            ));

            Gateway gw = Gateway.builder().chatHandler(handler).build();
            GatewayRequest request = GatewayRequest.builder()
                .sessionId("s1").message("m").build();

            CountDownLatch latch = new CountDownLatch(1);
            List<Map<String, Object>> capturedMeta = new CopyOnWriteArrayList<>();

            gw.handleStream(request, new GatewayStreamHandler() {
                @Override public void onDelta(String delta) {}
                @Override public void onDelta(String delta, Map<String, Object> metadata) {
                    capturedMeta.add(metadata);
                }
                @Override public void onComplete(GatewayResponse resp) { latch.countDown(); }
                @Override public void onError(Throwable error) { latch.countDown(); }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(1, capturedMeta.size());
            assertEquals("calm", capturedMeta.get(0).get("emotion"));
            assertEquals(0.95, capturedMeta.get(0).get("confidence"));
        }

        @Test
        @DisplayName("accumulates all deltas into onComplete response text")
        void accumulatesTextInOnCompleteResponse() throws Exception {
            ChatHandler handler = reactiveChatHandler(List.of(
                StreamEvent.textDelta("Hello"),
                StreamEvent.textDelta(" "),
                StreamEvent.textDelta("World"),
                StreamEvent.llmComplete(null)
            ));

            Gateway gw = Gateway.builder().chatHandler(handler).build();
            GatewayRequest request = GatewayRequest.builder()
                .requestId("r1").sessionId("s1").message("greet").build();

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<GatewayResponse> captured = new AtomicReference<>();

            gw.handleStream(request, new GatewayStreamHandler() {
                @Override public void onDelta(String delta) {}
                @Override public void onComplete(GatewayResponse resp) {
                    captured.set(resp);
                    latch.countDown();
                }
                @Override public void onError(Throwable error) { latch.countDown(); }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            GatewayResponse resp = captured.get();
            assertNotNull(resp);
            assertEquals("Hello World", resp.getText());
            assertEquals("r1", resp.getRequestId());
            assertEquals("s1", resp.getSessionId());
            assertTrue(resp.getLatencyMs() >= 0);
        }
    }

    @Nested
    @DisplayName("dispatchEvent - tool events")
    class DispatchToolEvents {

        @Test
        @DisplayName("dispatches TOOL_LOG to handler.onToolLog")
        void dispatchesToolLogEvent() throws Exception {
            ToolResultChunk logChunk = ToolResultChunk.log("db_query", "INFO", "Executing SQL");
            ChatHandler handler = reactiveChatHandler(List.of(
                StreamEvent.toolLog(logChunk),
                StreamEvent.llmComplete(null)
            ));

            Gateway gw = Gateway.builder().chatHandler(handler).build();
            GatewayRequest request = GatewayRequest.builder()
                .sessionId("s1").message("query").build();

            CountDownLatch latch = new CountDownLatch(1);
            List<ToolResultChunk> capturedLogs = new CopyOnWriteArrayList<>();

            gw.handleStream(request, new GatewayStreamHandler() {
                @Override public void onDelta(String delta) {}
                @Override public void onToolLog(ToolResultChunk chunk) { capturedLogs.add(chunk); }
                @Override public void onComplete(GatewayResponse resp) { latch.countDown(); }
                @Override public void onError(Throwable error) { latch.countDown(); }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(1, capturedLogs.size());
            assertEquals("db_query", capturedLogs.get(0).getToolName());
            assertTrue(capturedLogs.get(0).getMessage().contains("Executing SQL"));
        }

        @Test
        @DisplayName("dispatches full tool lifecycle in order: START -> PROGRESS -> LOG -> RESULT")
        void dispatchesFullToolLifecycle() throws Exception {
            ToolCall call = new ToolCall("tc-1", "search", Map.of("q", "test"));
            ToolResultChunk progress = ToolResultChunk.progress("search", "Searching...", 30, 100);
            ToolResultChunk log = ToolResultChunk.log("search", "DEBUG", "Found 5 results");
            ToolResultChunk result = ToolResultChunk.complete("search", ToolResult.success("5 results"));

            ChatHandler handler = reactiveChatHandler(List.of(
                StreamEvent.toolCallStart(call),
                StreamEvent.toolProgress(progress),
                StreamEvent.toolLog(log),
                StreamEvent.toolResult(result),
                StreamEvent.llmComplete(null)
            ));

            Gateway gw = Gateway.builder().chatHandler(handler).build();
            GatewayRequest request = GatewayRequest.builder()
                .sessionId("s1").message("search test").build();

            CountDownLatch latch = new CountDownLatch(1);
            List<String> eventLog = new CopyOnWriteArrayList<>();

            gw.handleStream(request, new GatewayStreamHandler() {
                @Override public void onDelta(String delta) {}
                @Override public void onToolCallStart(ToolCall tc) {
                    eventLog.add("START:" + tc.getName() + ":" + tc.getId());
                }
                @Override public void onToolProgress(ToolResultChunk chunk) {
                    eventLog.add("PROGRESS:" + chunk.getToolName());
                }
                @Override public void onToolLog(ToolResultChunk chunk) {
                    eventLog.add("LOG:" + chunk.getToolName());
                }
                @Override public void onToolResult(ToolResultChunk chunk) {
                    eventLog.add("RESULT:" + chunk.getToolName() + ":" + chunk.getResult().getContent());
                }
                @Override public void onComplete(GatewayResponse resp) { latch.countDown(); }
                @Override public void onError(Throwable error) { latch.countDown(); }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(List.of(
                "START:search:tc-1",
                "PROGRESS:search",
                "LOG:search",
                "RESULT:search:5 results"
            ), eventLog);
        }

        @Test
        @DisplayName("skips TOOL_CALL_START when toolCall is null")
        void skipsToolCallStartWhenNull() throws Exception {
            StreamEvent nullToolCallEvent = StreamEvent.toolCallStart(null);
            ChatHandler handler = reactiveChatHandler(List.of(
                nullToolCallEvent,
                StreamEvent.textDelta("ok"),
                StreamEvent.llmComplete(null)
            ));

            Gateway gw = Gateway.builder().chatHandler(handler).build();
            GatewayRequest request = GatewayRequest.builder()
                .sessionId("s1").message("test").build();

            CountDownLatch latch = new CountDownLatch(1);
            List<String> eventLog = new CopyOnWriteArrayList<>();

            gw.handleStream(request, new GatewayStreamHandler() {
                @Override public void onDelta(String delta) {}
                @Override public void onDelta(String delta, Map<String, Object> metadata) {
                    eventLog.add("DELTA:" + delta);
                }
                @Override public void onToolCallStart(ToolCall tc) {
                    eventLog.add("START");
                }
                @Override public void onComplete(GatewayResponse resp) { latch.countDown(); }
                @Override public void onError(Throwable error) { latch.countDown(); }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(List.of("DELTA:ok"), eventLog);
        }

        @Test
        @DisplayName("skips TOOL_RESULT, TOOL_ERROR, TOOL_PROGRESS, TOOL_LOG when chunk is null")
        void skipsToolEventsWhenChunkIsNull() throws Exception {
            ChatHandler handler = reactiveChatHandler(List.of(
                StreamEvent.toolResult(null),
                StreamEvent.toolError(null),
                StreamEvent.toolProgress(null),
                StreamEvent.toolLog(null),
                StreamEvent.textDelta("only-this"),
                StreamEvent.llmComplete(null)
            ));

            Gateway gw = Gateway.builder().chatHandler(handler).build();
            GatewayRequest request = GatewayRequest.builder()
                .sessionId("s1").message("test").build();

            CountDownLatch latch = new CountDownLatch(1);
            List<String> eventLog = new CopyOnWriteArrayList<>();

            gw.handleStream(request, new GatewayStreamHandler() {
                @Override public void onDelta(String delta) {}
                @Override public void onDelta(String delta, Map<String, Object> metadata) {
                    eventLog.add("DELTA:" + delta);
                }
                @Override public void onToolResult(ToolResultChunk chunk) { eventLog.add("RESULT"); }
                @Override public void onToolError(ToolResultChunk chunk) { eventLog.add("ERROR"); }
                @Override public void onToolProgress(ToolResultChunk chunk) { eventLog.add("PROGRESS"); }
                @Override public void onToolLog(ToolResultChunk chunk) { eventLog.add("LOG"); }
                @Override public void onComplete(GatewayResponse resp) { latch.countDown(); }
                @Override public void onError(Throwable error) { latch.countDown(); }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(List.of("DELTA:only-this"), eventLog);
        }
    }

    @Nested
    @DisplayName("dispatchEvent - POST_PROCESS_DATA")
    class DispatchPostProcessData {

        @Test
        @DisplayName("passes category and data map to onPostProcessData")
        void passesCategoryAndDataPayload() throws Exception {
            Map<String, Object> cardData = Map.of("title", "Weather", "temp", 25);
            ChatHandler handler = reactiveChatHandler(List.of(
                StreamEvent.postProcessData("card", cardData),
                StreamEvent.llmComplete(null)
            ));

            Gateway gw = Gateway.builder().chatHandler(handler).build();
            GatewayRequest request = GatewayRequest.builder()
                .sessionId("s1").message("weather").build();

            CountDownLatch latch = new CountDownLatch(1);
            List<String> capturedCategories = new CopyOnWriteArrayList<>();
            List<Map<String, Object>> capturedData = new CopyOnWriteArrayList<>();

            gw.handleStream(request, new GatewayStreamHandler() {
                @Override public void onDelta(String delta) {}
                @Override public void onPostProcessData(String category, Map<String, Object> data) {
                    capturedCategories.add(category);
                    capturedData.add(data);
                }
                @Override public void onComplete(GatewayResponse resp) { latch.countDown(); }
                @Override public void onError(Throwable error) { latch.countDown(); }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(List.of("card"), capturedCategories);
            assertEquals("Weather", capturedData.get(0).get("title"));
            assertEquals(25, capturedData.get(0).get("temp"));
        }

        @Test
        @DisplayName("dispatches multiple POST_PROCESS_DATA events with different categories")
        void dispatchesMultiplePostProcessDataEvents() throws Exception {
            ChatHandler handler = reactiveChatHandler(List.of(
                StreamEvent.postProcessData("card", Map.of("type", "weather")),
                StreamEvent.postProcessData("safety", Map.of("score", 0.98)),
                StreamEvent.llmComplete(null)
            ));

            Gateway gw = Gateway.builder().chatHandler(handler).build();
            GatewayRequest request = GatewayRequest.builder()
                .sessionId("s1").message("m").build();

            CountDownLatch latch = new CountDownLatch(1);
            List<String> capturedCategories = new CopyOnWriteArrayList<>();
            List<Map<String, Object>> capturedData = new CopyOnWriteArrayList<>();

            gw.handleStream(request, new GatewayStreamHandler() {
                @Override public void onDelta(String delta) {}
                @Override public void onPostProcessData(String category, Map<String, Object> data) {
                    capturedCategories.add(category);
                    capturedData.add(data);
                }
                @Override public void onComplete(GatewayResponse resp) { latch.countDown(); }
                @Override public void onError(Throwable error) { latch.countDown(); }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(List.of("card", "safety"), capturedCategories);
            assertEquals("weather", capturedData.get(0).get("type"));
            assertEquals(0.98, capturedData.get(1).get("score"));
        }
    }

    @Nested
    @DisplayName("dispatchEvent - ERROR")
    class DispatchError {

        @Test
        @DisplayName("dispatches ERROR event to handler.onError with the original throwable")
        void dispatchesErrorWithOriginalThrowable() throws Exception {
            RuntimeException cause = new RuntimeException("quota exceeded");
            ChatHandler handler = reactiveChatHandler(List.of(
                StreamEvent.textDelta("partial"),
                StreamEvent.error(cause),
                StreamEvent.llmComplete(null)
            ));

            Gateway gw = Gateway.builder().chatHandler(handler).build();
            GatewayRequest request = GatewayRequest.builder()
                .sessionId("s1").message("m").build();

            CountDownLatch latch = new CountDownLatch(1);
            List<Throwable> capturedErrors = new CopyOnWriteArrayList<>();
            List<String> capturedDeltas = new CopyOnWriteArrayList<>();

            gw.handleStream(request, new GatewayStreamHandler() {
                @Override public void onDelta(String delta) {}
                @Override public void onDelta(String delta, Map<String, Object> metadata) {
                    capturedDeltas.add(delta);
                }
                @Override public void onError(Throwable error) { capturedErrors.add(error); }
                @Override public void onComplete(GatewayResponse resp) { latch.countDown(); }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(List.of("partial"), capturedDeltas);
            assertEquals(1, capturedErrors.size());
            assertSame(cause, capturedErrors.get(0));
        }

        @Test
        @DisplayName("Flux-level error routes to subscribe onError, not dispatchEvent")
        void fluxLevelErrorRoutesToSubscribeOnError() throws Exception {
            RuntimeException fluxError = new RuntimeException("connection lost");
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
                        Flux.just(StreamEvent.textDelta("before")),
                        Flux.error(fluxError)
                    );
                }
            };

            Gateway gw = Gateway.builder().chatHandler(handler).build();
            GatewayRequest request = GatewayRequest.builder()
                .sessionId("s1").message("m").build();

            CountDownLatch latch = new CountDownLatch(1);
            List<Throwable> capturedErrors = new CopyOnWriteArrayList<>();
            AtomicReference<GatewayResponse> capturedComplete = new AtomicReference<>();

            gw.handleStream(request, new GatewayStreamHandler() {
                @Override public void onDelta(String delta) {}
                @Override public void onError(Throwable error) {
                    capturedErrors.add(error);
                    latch.countDown();
                }
                @Override public void onComplete(GatewayResponse resp) {
                    capturedComplete.set(resp);
                    latch.countDown();
                }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertFalse(capturedErrors.isEmpty());
            assertEquals("connection lost", capturedErrors.get(0).getMessage());
            assertNull(capturedComplete.get());
        }
    }

    @Nested
    @DisplayName("dispatchEvent - mixed event stream")
    class DispatchMixedStream {

        @Test
        @DisplayName("interleaved text, tool, and post-process events dispatch to correct callbacks")
        void interleavedEventsDispatchCorrectly() throws Exception {
            ToolCall tc = new ToolCall("tc-1", "calc", Map.of("expr", "1+1"));
            ToolResultChunk resultChunk = ToolResultChunk.complete("calc", ToolResult.success("2"));

            ChatHandler handler = reactiveChatHandler(List.of(
                StreamEvent.textDelta("Let me "),
                StreamEvent.toolCallStart(tc),
                StreamEvent.toolResult(resultChunk),
                StreamEvent.textDelta("calculate: "),
                StreamEvent.postProcessData("citation", Map.of("source", "math")),
                StreamEvent.textDelta("2"),
                StreamEvent.llmComplete(null)
            ));

            Gateway gw = Gateway.builder().chatHandler(handler).build();
            GatewayRequest request = GatewayRequest.builder()
                .sessionId("s1").message("calc 1+1").build();

            CountDownLatch latch = new CountDownLatch(1);
            List<String> eventLog = new CopyOnWriteArrayList<>();

            gw.handleStream(request, new GatewayStreamHandler() {
                @Override public void onDelta(String delta) {}
                @Override public void onDelta(String delta, Map<String, Object> metadata) {
                    eventLog.add("DELTA:" + delta);
                }
                @Override public void onToolCallStart(ToolCall toolCall) {
                    eventLog.add("TOOL_START:" + toolCall.getName());
                }
                @Override public void onToolResult(ToolResultChunk chunk) {
                    eventLog.add("TOOL_RESULT:" + chunk.getResult().getContent());
                }
                @Override public void onPostProcessData(String category, Map<String, Object> data) {
                    eventLog.add("POST:" + category + ":" + data.get("source"));
                }
                @Override public void onComplete(GatewayResponse resp) { latch.countDown(); }
                @Override public void onError(Throwable error) { latch.countDown(); }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(List.of(
                "DELTA:Let me ",
                "TOOL_START:calc",
                "TOOL_RESULT:2",
                "DELTA:calculate: ",
                "POST:citation:math",
                "DELTA:2"
            ), eventLog);
        }
    }

    @Nested
    @DisplayName("PostProcessor pipeline")
    class PostProcessorPipeline {

        @Test
        @DisplayName("post-processor injects POST_PROCESS_DATA events into the stream")
        void postProcessorInjectsEvents() {
            ChatHandler handler = reactiveChatHandler(List.of(
                StreamEvent.textDelta("hello"),
                StreamEvent.llmComplete(null)
            ));

            StreamPostProcessor injector = new StreamPostProcessor() {
                @Override
                public Flux<StreamEvent> apply(Flux<StreamEvent> input) {
                    return input.concatWith(
                        Flux.just(StreamEvent.postProcessData("safety", Map.of("score", 0.99)))
                    );
                }
                @Override public String getName() { return "safety-injector"; }
                @Override public int getOrder() { return 10; }
            };

            Gateway gw = Gateway.builder()
                .chatHandler(handler)
                .addPostProcessor(injector)
                .build();

            GatewayRequest request = GatewayRequest.builder()
                .sessionId("s1").message("test").build();

            List<StreamEvent.EventType> types = new ArrayList<>();
            gw.handleStreamReactive(request)
                .doOnNext(e -> types.add(e.getType()))
                .blockLast();

            assertTrue(types.contains(StreamEvent.EventType.POST_PROCESS_DATA));
            assertEquals(StreamEvent.EventType.POST_PROCESS_DATA, types.get(types.size() - 1));
        }

        @Test
        @DisplayName("multiple post-processors apply in order-sorted sequence")
        void multiplePostProcessorsApplyInOrder() {
            ChatHandler handler = reactiveChatHandler(List.of(
                StreamEvent.textDelta("abc"),
                StreamEvent.llmComplete(null)
            ));

            List<String> executionOrder = new CopyOnWriteArrayList<>();

            StreamPostProcessor first = new StreamPostProcessor() {
                @Override
                public Flux<StreamEvent> apply(Flux<StreamEvent> input) {
                    return input.doOnNext(e -> {
                        if (e.getType() == StreamEvent.EventType.TEXT_DELTA) {
                            executionOrder.add("first");
                        }
                    }).map(e -> {
                        if (e.getType() == StreamEvent.EventType.TEXT_DELTA && e.getTextDelta() != null) {
                            return StreamEvent.textDelta(e.getTextDelta().toUpperCase());
                        }
                        return e;
                    });
                }
                @Override public String getName() { return "upper"; }
                @Override public int getOrder() { return 10; }
            };

            StreamPostProcessor second = new StreamPostProcessor() {
                @Override
                public Flux<StreamEvent> apply(Flux<StreamEvent> input) {
                    return input.doOnNext(e -> {
                        if (e.getType() == StreamEvent.EventType.TEXT_DELTA) {
                            executionOrder.add("second");
                        }
                    }).map(e -> {
                        if (e.getType() == StreamEvent.EventType.TEXT_DELTA && e.getTextDelta() != null) {
                            return StreamEvent.textDelta(e.getTextDelta() + "!");
                        }
                        return e;
                    });
                }
                @Override public String getName() { return "exclaim"; }
                @Override public int getOrder() { return 20; }
            };

            Gateway gw = Gateway.builder()
                .chatHandler(handler)
                .addPostProcessor(second)
                .addPostProcessor(first)
                .build();

            GatewayRequest request = GatewayRequest.builder()
                .sessionId("s1").message("test").build();

            StepVerifier.create(gw.handleStreamReactive(request))
                .assertNext(e -> assertEquals(StreamEvent.EventType.TRACE, e.getType()))
                .assertNext(e -> {
                    assertEquals(StreamEvent.EventType.TEXT_DELTA, e.getType());
                    assertEquals("ABC!", e.getTextDelta());
                })
                .assertNext(e -> assertEquals(StreamEvent.EventType.LLM_COMPLETE, e.getType()))
                .verifyComplete();

            assertTrue(executionOrder.indexOf("first") < executionOrder.indexOf("second"));
        }

        @Test
        @DisplayName("post-processor transforms flow through to handleStream dispatch")
        void postProcessorTransformsReachHandleStream() throws Exception {
            ChatHandler handler = reactiveChatHandler(List.of(
                StreamEvent.textDelta("hello"),
                StreamEvent.llmComplete(null)
            ));

            StreamPostProcessor processor = new StreamPostProcessor() {
                @Override
                public Flux<StreamEvent> apply(Flux<StreamEvent> input) {
                    return input.map(e -> {
                        if (e.getType() == StreamEvent.EventType.TEXT_DELTA && e.getTextDelta() != null) {
                            return StreamEvent.textDelta(e.getTextDelta().toUpperCase());
                        }
                        return e;
                    });
                }
                @Override public String getName() { return "upper"; }
            };

            Gateway gw = Gateway.builder()
                .chatHandler(handler)
                .addPostProcessor(processor)
                .build();

            GatewayRequest request = GatewayRequest.builder()
                .sessionId("s1").message("test").build();

            CountDownLatch latch = new CountDownLatch(1);
            List<String> deltas = new CopyOnWriteArrayList<>();
            AtomicReference<GatewayResponse> responseRef = new AtomicReference<>();

            gw.handleStream(request, new GatewayStreamHandler() {
                @Override public void onDelta(String delta) {}
                @Override public void onDelta(String delta, Map<String, Object> metadata) {
                    deltas.add(delta);
                }
                @Override public void onComplete(GatewayResponse resp) {
                    responseRef.set(resp);
                    latch.countDown();
                }
                @Override public void onError(Throwable error) { latch.countDown(); }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(List.of("HELLO"), deltas);
            assertEquals("HELLO", responseRef.get().getText());
        }
    }

    @Nested
    @DisplayName("LLM_COMPLETE and TRACE events")
    class NonDispatchedEvents {

        @Test
        @DisplayName("LLM_COMPLETE event does not trigger any handler callback")
        void llmCompleteDoesNotTriggerCallback() throws Exception {
            LLMResponse llmResp = LLMResponse.builder().stopReason("stop").build();
            ChatHandler handler = reactiveChatHandler(List.of(
                StreamEvent.textDelta("done"),
                StreamEvent.llmComplete(llmResp),
                StreamEvent.llmComplete(null)
            ));

            Gateway gw = Gateway.builder().chatHandler(handler).build();
            GatewayRequest request = GatewayRequest.builder()
                .sessionId("s1").message("m").build();

            CountDownLatch latch = new CountDownLatch(1);
            List<String> events = new CopyOnWriteArrayList<>();

            gw.handleStream(request, new GatewayStreamHandler() {
                @Override public void onDelta(String delta) {}
                @Override public void onDelta(String delta, Map<String, Object> metadata) {
                    events.add("DELTA:" + delta);
                }
                @Override public void onComplete(GatewayResponse resp) {
                    events.add("COMPLETE");
                    latch.countDown();
                }
                @Override public void onError(Throwable error) { latch.countDown(); }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(List.of("DELTA:done", "COMPLETE"), events);
        }

        @Test
        @DisplayName("TRACE events are silently ignored by dispatchEvent")
        void traceEventsAreIgnored() throws Exception {
            ChatHandler handler = reactiveChatHandler(List.of(
                StreamEvent.trace("llm.request", "calling model"),
                StreamEvent.textDelta("response"),
                StreamEvent.trace("llm.response", "model returned"),
                StreamEvent.llmComplete(null)
            ));

            Gateway gw = Gateway.builder().chatHandler(handler).build();
            GatewayRequest request = GatewayRequest.builder()
                .sessionId("s1").message("m").build();

            CountDownLatch latch = new CountDownLatch(1);
            List<String> events = new CopyOnWriteArrayList<>();

            gw.handleStream(request, new GatewayStreamHandler() {
                @Override public void onDelta(String delta) {}
                @Override public void onDelta(String delta, Map<String, Object> metadata) {
                    events.add("DELTA:" + delta);
                }
                @Override public void onComplete(GatewayResponse resp) {
                    events.add("COMPLETE");
                    latch.countDown();
                }
                @Override public void onError(Throwable error) { latch.countDown(); }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(List.of("DELTA:response", "COMPLETE"), events);
        }
    }

    private ChatHandler reactiveChatHandler(List<StreamEvent> events) {
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
