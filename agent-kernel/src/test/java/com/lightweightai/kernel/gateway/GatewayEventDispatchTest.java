package com.lightweightai.kernel.gateway;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests Gateway.handleStream event dispatch to GatewayStreamHandler.
 *
 * Verifies that all StreamEvent types are correctly routed to the
 * corresponding handler callback — not just TEXT_DELTA.
 */
@DisplayName("Gateway - Event dispatch to GatewayStreamHandler")
class GatewayEventDispatchTest {

    private CapturingStreamHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CapturingStreamHandler();
    }

    @Test
    @DisplayName("TEXT_DELTA events dispatch to onDelta")
    void textDeltaDispatchesToOnDelta() throws Exception {
        Gateway gateway = buildGateway(Flux.just(
                StreamEvent.textDelta("Hello "),
                StreamEvent.textDelta("world")
        ));

        GatewayRequest request = buildRequest();
        gateway.handleStream(request, handler);
        handler.awaitComplete();

        assertEquals(List.of("Hello ", "world"), handler.deltas);
        assertEquals("Hello world", handler.completedResponse.get().getText());
    }

    @Test
    @DisplayName("TOOL_CALL_START events dispatch to onToolCallStart")
    void toolCallStartDispatches() throws Exception {
        ToolCall tc = new ToolCall("call_1", "weather", Map.of("city", "Tokyo"));
        Gateway gateway = buildGateway(Flux.just(
                StreamEvent.toolCallStart(tc)
        ));

        gateway.handleStream(buildRequest(), handler);
        handler.awaitComplete();

        assertEquals(1, handler.toolCallStarts.size());
        assertEquals("weather", handler.toolCallStarts.get(0).getName());
    }

    @Test
    @DisplayName("TOOL_RESULT events dispatch to onToolResult")
    void toolResultDispatches() throws Exception {
        ToolResultChunk chunk = ToolResultChunk.complete("weather",
                ToolResult.success("call_1", "Sunny, 25°C"));
        Gateway gateway = buildGateway(Flux.just(
                StreamEvent.toolResult(chunk)
        ));

        gateway.handleStream(buildRequest(), handler);
        handler.awaitComplete();

        assertEquals(1, handler.toolResults.size());
        assertEquals("weather", handler.toolResults.get(0).getToolName());
    }

    @Test
    @DisplayName("TOOL_PROGRESS events dispatch to onToolProgress")
    void toolProgressDispatches() throws Exception {
        ToolResultChunk chunk = ToolResultChunk.progress("search", "Searching...", 0.5, 1.0);
        Gateway gateway = buildGateway(Flux.just(
                StreamEvent.toolProgress(chunk)
        ));

        gateway.handleStream(buildRequest(), handler);
        handler.awaitComplete();

        assertEquals(1, handler.toolProgress.size());
    }

    @Test
    @DisplayName("TOOL_ERROR events dispatch to onToolError")
    void toolErrorDispatches() throws Exception {
        ToolResultChunk chunk = ToolResultChunk.error("tool", "timeout");
        Gateway gateway = buildGateway(Flux.just(
                StreamEvent.toolError(chunk)
        ));

        gateway.handleStream(buildRequest(), handler);
        handler.awaitComplete();

        assertEquals(1, handler.toolErrors.size());
    }

    @Test
    @DisplayName("ERROR events dispatch to handler.onError")
    void errorEventDispatches() throws Exception {
        RuntimeException err = new RuntimeException("LLM failed");
        Gateway gateway = buildGateway(Flux.just(
                StreamEvent.error(err)
        ));

        gateway.handleStream(buildRequest(), handler);
        handler.awaitComplete();

        assertNotNull(handler.errorReceived.get());
        assertEquals("LLM failed", handler.errorReceived.get().getMessage());
    }

    @Test
    @DisplayName("LLM_COMPLETE events are silently consumed (completion via Flux.onComplete)")
    void llmCompleteIsSilent() throws Exception {
        LLMResponse response = LLMResponse.builder()
                .message(ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.ASSISTANT)
                        .textContent("done")
                        .build())
                .stopReason("end_turn")
                .build();

        Gateway gateway = buildGateway(Flux.just(
                StreamEvent.textDelta("done"),
                StreamEvent.llmComplete(response)
        ));

        gateway.handleStream(buildRequest(), handler);
        handler.awaitComplete();

        assertEquals("done", handler.completedResponse.get().getText());
    }

    @Test
    @DisplayName("POST_PROCESS_DATA events dispatch to onPostProcessData")
    void postProcessDataDispatches() throws Exception {
        Gateway gateway = buildGateway(Flux.just(
                StreamEvent.postProcessData("card", Map.of("type", "weather_card"))
        ));

        gateway.handleStream(buildRequest(), handler);
        handler.awaitComplete();

        assertEquals(1, handler.postProcessData.size());
        assertEquals("card", handler.postProcessData.get(0).category);
    }

    // ==================== Helpers ====================

    private Gateway buildGateway(Flux<StreamEvent> events) {
        ChatHandler chatHandler = new ChatHandler() {
            @Override
            public GatewayResponse chat(GatewayRequest request) {
                return GatewayResponse.builder()
                        .text("sync fallback")
                        .build();
            }

            @Override
            public Flux<StreamEvent> chatStreamReactive(GatewayRequest request) {
                return events;
            }
        };

        return Gateway.builder()
                .chatHandler(chatHandler)
                .build();
    }

    private GatewayRequest buildRequest() {
        return GatewayRequest.builder()
                .message("test")
                .sessionId("sess-1")
                .requestId("req-1")
                .build();
    }

    /**
     * Captures all callback invocations for assertion.
     */
    static class CapturingStreamHandler implements GatewayStreamHandler {
        final List<String> deltas = new ArrayList<>();
        final List<ToolCall> toolCallStarts = new ArrayList<>();
        final List<ToolResultChunk> toolResults = new ArrayList<>();
        final List<ToolResultChunk> toolProgress = new ArrayList<>();
        final List<ToolResultChunk> toolErrors = new ArrayList<>();
        final List<PostProcessEntry> postProcessData = new ArrayList<>();
        final AtomicReference<Throwable> errorReceived = new AtomicReference<>();
        final AtomicReference<GatewayResponse> completedResponse = new AtomicReference<>();
        private final CountDownLatch completeLatch = new CountDownLatch(1);

        @Override
        public void onDelta(String delta) {
            deltas.add(delta);
        }

        @Override
        public void onToolCallStart(ToolCall toolCall) {
            toolCallStarts.add(toolCall);
        }

        @Override
        public void onToolResult(ToolResultChunk chunk) {
            toolResults.add(chunk);
        }

        @Override
        public void onToolProgress(ToolResultChunk chunk) {
            toolProgress.add(chunk);
        }

        @Override
        public void onToolError(ToolResultChunk chunk) {
            toolErrors.add(chunk);
        }

        @Override
        public void onPostProcessData(String category, Map<String, Object> data) {
            postProcessData.add(new PostProcessEntry(category, data));
        }

        @Override
        public void onError(Throwable error) {
            errorReceived.set(error);
        }

        @Override
        public void onComplete(GatewayResponse response) {
            completedResponse.set(response);
            completeLatch.countDown();
        }

        void awaitComplete() throws InterruptedException {
            assertTrue(completeLatch.await(5, TimeUnit.SECONDS),
                    "handleStream should complete within 5 seconds");
        }

        record PostProcessEntry(String category, Object data) {}
    }
}
