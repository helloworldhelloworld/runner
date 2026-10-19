package com.lightweightai.kernel.gateway;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Gateway - handleStream 事件分发验证")
class GatewayDispatchEventTest {

    @Test
    @DisplayName("TEXT_DELTA 事件分发到 onDelta 并累加 fullText")
    void textDeltaDispatchedAndAccumulated() {
        Gateway gateway = Gateway.builder()
                .chatHandler(chatHandlerEmitting(
                        StreamEvent.textDelta("Hello "),
                        StreamEvent.textDelta("World")))
                .build();

        List<String> deltas = new ArrayList<>();
        AtomicReference<GatewayResponse> completed = new AtomicReference<>();

        gateway.handleStream(
                GatewayRequest.builder().message("hi").sessionId("s1").build(),
                GatewayStreamHandler.simple(
                        deltas::add,
                        completed::set,
                        err -> fail("unexpected error: " + err)));

        assertEquals(List.of("Hello ", "World"), deltas);
        assertNotNull(completed.get());
        assertEquals("Hello World", completed.get().getText());
    }

    @Test
    @DisplayName("TOOL_CALL_START 事件分发到 onToolCallStart")
    void toolCallStartDispatched() {
        ToolCall tc = new ToolCall("call_1", "search", Map.of("q", "test"));
        Gateway gateway = Gateway.builder()
                .chatHandler(chatHandlerEmitting(StreamEvent.toolCallStart(tc)))
                .build();

        List<ToolCall> calls = new ArrayList<>();
        gateway.handleStream(
                GatewayRequest.builder().message("hi").sessionId("s1").build(),
                new CapturingStreamHandler() {
                    @Override public void onToolCallStart(ToolCall toolCall) { calls.add(toolCall); }
                });

        assertEquals(1, calls.size());
        assertEquals("search", calls.get(0).getName());
    }

    @Test
    @DisplayName("TOOL_RESULT 事件分发到 onToolResult")
    void toolResultDispatched() {
        ToolResultChunk chunk = ToolResultChunk.complete("search", ToolResult.success("found"));
        Gateway gateway = Gateway.builder()
                .chatHandler(chatHandlerEmitting(StreamEvent.toolResult(chunk)))
                .build();

        List<ToolResultChunk> results = new ArrayList<>();
        gateway.handleStream(
                GatewayRequest.builder().message("hi").sessionId("s1").build(),
                new CapturingStreamHandler() {
                    @Override public void onToolResult(ToolResultChunk c) { results.add(c); }
                });

        assertEquals(1, results.size());
        assertEquals("search", results.get(0).getToolName());
    }

    @Test
    @DisplayName("TOOL_PROGRESS 事件分发到 onToolProgress")
    void toolProgressDispatched() {
        ToolResultChunk chunk = ToolResultChunk.progress("download", "50%", 50, 100);
        Gateway gateway = Gateway.builder()
                .chatHandler(chatHandlerEmitting(StreamEvent.toolProgress(chunk)))
                .build();

        List<ToolResultChunk> progress = new ArrayList<>();
        gateway.handleStream(
                GatewayRequest.builder().message("hi").sessionId("s1").build(),
                new CapturingStreamHandler() {
                    @Override public void onToolProgress(ToolResultChunk c) { progress.add(c); }
                });

        assertEquals(1, progress.size());
        assertEquals("50%", progress.get(0).getMessage());
    }

    @Test
    @DisplayName("TOOL_ERROR 事件分发到 onToolError")
    void toolErrorDispatched() {
        ToolResultChunk chunk = ToolResultChunk.error("broken_tool", "timeout");
        Gateway gateway = Gateway.builder()
                .chatHandler(chatHandlerEmitting(StreamEvent.toolError(chunk)))
                .build();

        List<ToolResultChunk> errors = new ArrayList<>();
        gateway.handleStream(
                GatewayRequest.builder().message("hi").sessionId("s1").build(),
                new CapturingStreamHandler() {
                    @Override public void onToolError(ToolResultChunk c) { errors.add(c); }
                });

        assertEquals(1, errors.size());
        assertEquals("timeout", errors.get(0).getMessage());
    }

    @Test
    @DisplayName("POST_PROCESS_DATA 事件分发到 onPostProcessData")
    void postProcessDataDispatched() {
        StreamEvent event = StreamEvent.postProcessData("card", Map.of("type", "weather"));
        Gateway gateway = Gateway.builder()
                .chatHandler(chatHandlerEmitting(event))
                .build();

        List<String> categories = new ArrayList<>();
        List<Map<String, Object>> datas = new ArrayList<>();
        gateway.handleStream(
                GatewayRequest.builder().message("hi").sessionId("s1").build(),
                new CapturingStreamHandler() {
                    @Override public void onPostProcessData(String category, Map<String, Object> data) {
                        categories.add(category);
                        datas.add(data);
                    }
                });

        assertEquals(List.of("card"), categories);
        assertEquals("weather", datas.get(0).get("type"));
    }

    @Test
    @DisplayName("ERROR 事件分发到 onError")
    void errorEventDispatched() {
        RuntimeException ex = new RuntimeException("test error");
        Gateway gateway = Gateway.builder()
                .chatHandler(chatHandlerEmitting(StreamEvent.error(ex)))
                .build();

        List<Throwable> errors = new ArrayList<>();
        gateway.handleStream(
                GatewayRequest.builder().message("hi").sessionId("s1").build(),
                new CapturingStreamHandler() {
                    @Override public void onError(Throwable error) { errors.add(error); }
                });

        assertEquals(1, errors.size());
        assertSame(ex, errors.get(0));
    }

    @Test
    @DisplayName("handleStreamReactive 在 chatHandler 流前注入 user.message trace 事件")
    void reactiveStreamPrependsUserTrace() {
        Gateway gateway = Gateway.builder()
                .chatHandler(chatHandlerEmitting(StreamEvent.textDelta("reply")))
                .build();

        List<StreamEvent> events = gateway.handleStreamReactive(
                GatewayRequest.builder().message("hello").sessionId("s1").build()
        ).collectList().block();

        assertNotNull(events);
        assertTrue(events.size() >= 2);
        assertEquals(StreamEvent.EventType.TRACE, events.get(0).getType());
        assertEquals("user.message", events.get(0).getTracePhase());
        assertEquals("hello", events.get(0).getTraceMessage());
    }

    @Test
    @DisplayName("Builder 缺少 chatHandler 和 agentLoop 时抛异常")
    void builderRequiresChatHandlerOrAgentLoop() {
        assertThrows(IllegalArgumentException.class, () -> Gateway.builder().build());
    }

    private static ChatHandler chatHandlerEmitting(StreamEvent... events) {
        return new ChatHandler() {
            @Override public GatewayResponse chat(GatewayRequest request) {
                return GatewayResponse.builder()
                        .requestId(request.getRequestId())
                        .sessionId(request.getSessionId())
                        .text("")
                        .build();
            }
            @Override public CompletableFuture<GatewayResponse> chatStream(
                    GatewayRequest request, StreamCallback callback) {
                return CompletableFuture.completedFuture(chat(request));
            }
            @Override public Flux<StreamEvent> chatStreamReactive(GatewayRequest request) {
                return Flux.fromArray(events);
            }
        };
    }

    private abstract static class CapturingStreamHandler implements GatewayStreamHandler {
        @Override public void onDelta(String delta) {}
        @Override public void onComplete(GatewayResponse response) {}
        @Override public void onError(Throwable error) {}
    }
}
