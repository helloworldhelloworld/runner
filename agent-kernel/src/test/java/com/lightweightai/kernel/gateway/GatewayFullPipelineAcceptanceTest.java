package com.lightweightai.kernel.gateway;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.core.postprocess.StreamPostProcessor;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gateway 全链路传输链验收测试
 *
 * 验证事件从 ChatHandler → Gateway reactive 管道 → PostProcessor → handleStream 分发器
 * 的完整传输链。每个断言检查事件负载（payload），不仅仅是事件存在性。
 *
 * 遵循 CLAUDE.md TDD Rule #1: 断言 payload, 不只是 ceremony。
 */
@DisplayName("Gateway 全链路传输链验收测试")
class GatewayFullPipelineAcceptanceTest {

    @Test
    @DisplayName("工具调用事件完整传输：ChatHandler → Reactive → handleStream 分发器保留所有 payload 字段")
    void toolCallEventsCarryPayloadThroughEntirePipeline() throws Exception {
        ToolCall toolCall = new ToolCall("tc_001", "search_web", Map.of("query", "weather Beijing"));
        ToolResultChunk progress = ToolResultChunk.progress("search_web", "Fetching...", 50, 100);
        ToolResultChunk logChunk = ToolResultChunk.log("search_web", "INFO", "Connected to API");
        ToolResultChunk result = ToolResultChunk.complete("search_web", ToolResult.success("Sunny, 25°C"));
        ToolResultChunk error = ToolResultChunk.error("search_web", "Rate limited");

        ChatHandler handler = reactiveChatHandler(List.of(
            StreamEvent.textDelta("Let me search", Map.of("confidence", 0.95)),
            StreamEvent.toolCallStart(toolCall),
            StreamEvent.toolProgress(progress),
            StreamEvent.toolLog(logChunk),
            StreamEvent.toolResult(result),
            StreamEvent.toolError(error),
            StreamEvent.textDelta("Based on results..."),
            StreamEvent.postProcessData("safety", Map.of("level", "green")),
            StreamEvent.llmComplete(null)
        ));

        Gateway gw = Gateway.builder().chatHandler(handler).build();
        GatewayRequest request = GatewayRequest.builder()
            .sessionId("sess-1").message("What's the weather?").build();

        CountDownLatch latch = new CountDownLatch(1);
        List<String> eventLog = new ArrayList<>();
        AtomicReference<GatewayResponse> finalResp = new AtomicReference<>();
        List<String> deltas = new ArrayList<>();
        List<Map<String, Object>> deltaMetadata = new ArrayList<>();
        AtomicReference<ToolCall> capturedToolCall = new AtomicReference<>();
        AtomicReference<ToolResultChunk> capturedResult = new AtomicReference<>();
        List<String> postProcessCategories = new ArrayList<>();
        List<Map<String, Object>> postProcessData = new ArrayList<>();

        gw.handleStream(request, new GatewayStreamHandler() {
            @Override public void onDelta(String delta) {}

            @Override
            public void onDelta(String delta, Map<String, Object> metadata) {
                eventLog.add("DELTA:" + delta);
                deltas.add(delta);
                deltaMetadata.add(metadata);
            }

            @Override
            public void onToolCallStart(ToolCall tc) {
                eventLog.add("TOOL_START:" + tc.getName());
                capturedToolCall.set(tc);
            }

            @Override
            public void onToolProgress(ToolResultChunk chunk) {
                eventLog.add("TOOL_PROGRESS:" + chunk.getToolName());
            }

            @Override
            public void onToolLog(ToolResultChunk chunk) {
                eventLog.add("TOOL_LOG:" + chunk.getToolName());
            }

            @Override
            public void onToolResult(ToolResultChunk chunk) {
                eventLog.add("TOOL_RESULT:" + chunk.getToolName());
                capturedResult.set(chunk);
            }

            @Override
            public void onToolError(ToolResultChunk chunk) {
                eventLog.add("TOOL_ERROR:" + chunk.getToolName());
            }

            @Override
            public void onPostProcessData(String category, Map<String, Object> data) {
                eventLog.add("POST_PROCESS:" + category);
                postProcessCategories.add(category);
                postProcessData.add(data);
            }

            @Override
            public void onComplete(GatewayResponse response) {
                finalResp.set(response);
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Pipeline should complete within 5s");

        // Payload assertions (not just ceremony)
        assertEquals(List.of(
            "DELTA:Let me search",
            "TOOL_START:search_web",
            "TOOL_PROGRESS:search_web",
            "TOOL_LOG:search_web",
            "TOOL_RESULT:search_web",
            "TOOL_ERROR:search_web",
            "DELTA:Based on results...",
            "POST_PROCESS:safety"
        ), eventLog);

        // Assert tool call payload carried through
        assertEquals("tc_001", capturedToolCall.get().getId());
        assertEquals("search_web", capturedToolCall.get().getName());
        assertEquals("weather Beijing", capturedToolCall.get().getArguments().get("query"));

        // Assert tool result payload carried through
        assertEquals("search_web", capturedResult.get().getToolName());

        // Assert text delta metadata carried through
        assertEquals(0.95, deltaMetadata.get(0).get("confidence"));

        // Assert post-process data carried through
        assertEquals(List.of("safety"), postProcessCategories);
        assertEquals("green", postProcessData.get(0).get("level"));

        // Assert final response aggregates text correctly
        assertEquals("Let me searchBased on results...", finalResp.get().getText());
    }

    @Test
    @DisplayName("PostProcessor 变换应用于工具和文本事件并保留事件顺序")
    void postProcessorTransformsEventsInOrder() throws Exception {
        ChatHandler handler = reactiveChatHandler(List.of(
            StreamEvent.textDelta("hello"),
            StreamEvent.toolCallStart(new ToolCall("id1", "greet", Map.of())),
            StreamEvent.textDelta(" world"),
            StreamEvent.llmComplete(null)
        ));

        StreamPostProcessor tagProcessor = new StreamPostProcessor() {
            @Override
            public Flux<StreamEvent> apply(Flux<StreamEvent> input) {
                return input.concatWith(Flux.just(
                    StreamEvent.postProcessData("tag", Map.of("injected", true))
                ));
            }
            @Override public String getName() { return "tag-injector"; }
            @Override public int getOrder() { return 0; }
        };

        Gateway gw = Gateway.builder()
            .chatHandler(handler)
            .addPostProcessor(tagProcessor)
            .build();

        GatewayRequest request = GatewayRequest.builder()
            .sessionId("s1").message("test").build();

        CountDownLatch latch = new CountDownLatch(1);
        List<String> eventTypes = new ArrayList<>();
        List<Map<String, Object>> injectedData = new ArrayList<>();

        gw.handleStream(request, new GatewayStreamHandler() {
            @Override public void onDelta(String delta) {}
            @Override public void onDelta(String delta, Map<String, Object> metadata) {
                eventTypes.add("DELTA");
            }
            @Override public void onToolCallStart(ToolCall tc) { eventTypes.add("TOOL_START"); }
            @Override public void onPostProcessData(String category, Map<String, Object> data) {
                eventTypes.add("POST:" + category);
                injectedData.add(data);
            }
            @Override public void onComplete(GatewayResponse resp) { latch.countDown(); }
            @Override public void onError(Throwable error) { latch.countDown(); }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        // Verify order: TRACE (ignored) → DELTA → TOOL_START → DELTA → LLM_COMPLETE (no dispatch) → POST:tag
        assertEquals(List.of("DELTA", "TOOL_START", "DELTA", "POST:tag"), eventTypes);

        // Verify injected post-process data payload
        assertEquals(true, injectedData.get(0).get("injected"));
    }

    @Test
    @DisplayName("handleStreamReactive 注入 TRACE 事件携带 sessionId 和 requestId")
    void reactiveStreamInjectsTraceWithRequestMetadata() {
        ChatHandler handler = reactiveChatHandler(List.of(
            StreamEvent.textDelta("ok"),
            StreamEvent.llmComplete(null)
        ));

        Gateway gw = Gateway.builder().chatHandler(handler).build();
        GatewayRequest request = GatewayRequest.builder()
            .requestId("req-42")
            .sessionId("sess-7")
            .message("test input")
            .build();

        List<StreamEvent> events = gw.handleStreamReactive(request).collectList().block();

        assertNotNull(events);
        assertFalse(events.isEmpty());

        StreamEvent firstEvent = events.get(0);
        assertEquals(StreamEvent.EventType.TRACE, firstEvent.getType());
        assertEquals("user.message", firstEvent.getTracePhase());
        assertEquals("test input", firstEvent.getTraceMessage());
        assertEquals("sess-7", firstEvent.getData().get("sessionId"));
        assertEquals("req-42", firstEvent.getData().get("requestId"));
    }

    // ==================== Helper ====================

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
