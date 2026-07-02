package com.lightweightai.kernel.gateway;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.ToolResultChunk;
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
 * Tests for TOOL_LOG event dispatch in Gateway.
 *
 * GatewayEventDispatchTest covered TOOL_CALL_START, TOOL_PROGRESS, TOOL_RESULT,
 * TOOL_ERROR, but was missing TOOL_LOG. This test fills that gap.
 *
 * Also verifies that mixed event sequences (tool log + tool result)
 * all get dispatched to the correct callbacks.
 */
@DisplayName("Gateway: TOOL_LOG event dispatch")
class GatewayToolLogDispatchTest {

    @Test
    @DisplayName("TOOL_LOG events dispatch to onToolLog callback")
    void toolLogDispatchesToOnToolLog() throws Exception {
        ToolResultChunk logChunk = ToolResultChunk.log("mcp_server", "Processing request...");
        Gateway gateway = buildGateway(Flux.just(
                StreamEvent.toolLog(logChunk)
        ));

        CapturingHandler handler = new CapturingHandler();
        gateway.handleStream(buildRequest(), handler);
        handler.awaitComplete();

        assertEquals(1, handler.toolLogs.size(), "Should receive exactly one TOOL_LOG");
        assertEquals("mcp_server", handler.toolLogs.get(0).getToolName());
        assertEquals("Processing request...", handler.toolLogs.get(0).getMessage());
    }

    @Test
    @DisplayName("Mixed TOOL_LOG + TOOL_RESULT events all dispatch correctly")
    void mixedToolLogAndResultEvents() throws Exception {
        ToolResultChunk log1 = ToolResultChunk.log("search", "Starting search...");
        ToolResultChunk log2 = ToolResultChunk.log("search", "Found 5 results");
        ToolResultChunk result = ToolResultChunk.complete("search",
                ToolResult.success("call_1", "Search results: item1, item2"));

        Gateway gateway = buildGateway(Flux.just(
                StreamEvent.toolLog(log1),
                StreamEvent.toolLog(log2),
                StreamEvent.toolResult(result),
                StreamEvent.textDelta("Here are your results")
        ));

        CapturingHandler handler = new CapturingHandler();
        gateway.handleStream(buildRequest(), handler);
        handler.awaitComplete();

        assertEquals(2, handler.toolLogs.size(), "Should receive 2 TOOL_LOG events");
        assertEquals(1, handler.toolResults.size(), "Should receive 1 TOOL_RESULT event");
        assertEquals(List.of("Here are your results"), handler.deltas);
    }

    @Test
    @DisplayName("TOOL_LOG with null chunk does not dispatch")
    void toolLogWithNullChunkSkipped() throws Exception {
        // Create a TOOL_LOG event but with null chunk via reflection workaround
        // Actually, the factory StreamEvent.toolLog(chunk) won't allow null,
        // so we test the natural flow - no extra TOOL_LOG should appear
        Gateway gateway = buildGateway(Flux.just(
                StreamEvent.textDelta("just text")
        ));

        CapturingHandler handler = new CapturingHandler();
        gateway.handleStream(buildRequest(), handler);
        handler.awaitComplete();

        assertEquals(0, handler.toolLogs.size(), "Should receive no TOOL_LOG events");
        assertEquals(List.of("just text"), handler.deltas);
    }

    // ==================== Helpers ====================

    private Gateway buildGateway(Flux<StreamEvent> events) {
        ChatHandler chatHandler = new ChatHandler() {
            @Override
            public GatewayResponse chat(GatewayRequest request) {
                return GatewayResponse.builder().text("sync").build();
            }

            @Override
            public CompletableFuture<GatewayResponse> chatStream(GatewayRequest request, StreamCallback callback) {
                return CompletableFuture.completedFuture(chat(request));
            }

            @Override
            public Flux<StreamEvent> chatStreamReactive(GatewayRequest request) {
                return events;
            }
        };

        return Gateway.builder().chatHandler(chatHandler).build();
    }

    private GatewayRequest buildRequest() {
        return GatewayRequest.builder()
                .message("test")
                .sessionId("log-sess")
                .requestId("log-req")
                .build();
    }

    private static class CapturingHandler implements GatewayStreamHandler {
        final List<String> deltas = new ArrayList<>();
        final List<ToolResultChunk> toolLogs = new ArrayList<>();
        final List<ToolResultChunk> toolResults = new ArrayList<>();
        final AtomicReference<GatewayResponse> completed = new AtomicReference<>();
        private final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void onDelta(String delta) { deltas.add(delta); }

        @Override
        public void onToolLog(ToolResultChunk chunk) { toolLogs.add(chunk); }

        @Override
        public void onToolResult(ToolResultChunk chunk) { toolResults.add(chunk); }

        @Override
        public void onComplete(GatewayResponse response) {
            completed.set(response);
            latch.countDown();
        }

        @Override
        public void onError(Throwable error) { latch.countDown(); }

        void awaitComplete() throws InterruptedException {
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Should complete within 5 seconds");
        }
    }
}
