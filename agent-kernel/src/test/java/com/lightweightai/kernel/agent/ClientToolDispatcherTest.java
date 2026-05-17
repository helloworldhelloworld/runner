package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.agent.directive.Directive;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClientToolDispatcher — async dispatch contract")
class ClientToolDispatcherTest {

    @Test
    @DisplayName("dispatch returns CompletableFuture that completes with ToolResult")
    void dispatchReturnsCompletableFuture() throws Exception {
        ClientToolDispatcher dispatcher = (callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("client result for " + toolName));

        CompletableFuture<ToolResult> future = dispatcher.dispatch("call_1", "camera_capture", null);

        ToolResult result = future.get(1, TimeUnit.SECONDS);
        assertNotNull(result);
        assertFalse(result.isError());
        assertEquals("client result for camera_capture", result.getContent());
    }

    @Test
    @DisplayName("dispatch receives correct callId and toolName")
    void dispatchReceivesCorrectParams() throws Exception {
        AtomicReference<String> capturedCallId = new AtomicReference<>();
        AtomicReference<String> capturedToolName = new AtomicReference<>();

        ClientToolDispatcher dispatcher = (callId, toolName, directive) -> {
            capturedCallId.set(callId);
            capturedToolName.set(toolName);
            return CompletableFuture.completedFuture(ToolResult.success("ok"));
        };

        dispatcher.dispatch("call_abc", "location_get", null).get(1, TimeUnit.SECONDS);

        assertEquals("call_abc", capturedCallId.get());
        assertEquals("location_get", capturedToolName.get());
    }

    @Test
    @DisplayName("dispatch can return error result from client")
    void dispatchCanReturnError() throws Exception {
        ClientToolDispatcher dispatcher = (callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.error("Client denied: permission not granted"));

        ToolResult result = dispatcher.dispatch("call_2", "contacts", null).get(1, TimeUnit.SECONDS);

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("permission not granted"));
    }

    @Test
    @DisplayName("dispatch can complete exceptionally for network failures")
    void dispatchCanFailExceptionally() {
        ClientToolDispatcher dispatcher = (callId, toolName, directive) -> {
            CompletableFuture<ToolResult> f = new CompletableFuture<>();
            f.completeExceptionally(new RuntimeException("WebSocket disconnected"));
            return f;
        };

        CompletableFuture<ToolResult> future = dispatcher.dispatch("call_3", "tool", null);

        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    @DisplayName("dispatch with async delay completes after delay")
    void dispatchWithAsyncDelay() throws Exception {
        ClientToolDispatcher dispatcher = (callId, toolName, directive) ->
                CompletableFuture.supplyAsync(() -> {
                    try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                    return ToolResult.success("delayed result");
                });

        long start = System.currentTimeMillis();
        ToolResult result = dispatcher.dispatch("call_4", "slow_tool", null).get(2, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - start;

        assertFalse(result.isError());
        assertEquals("delayed result", result.getContent());
        assertTrue(elapsed >= 40); // at least some delay
    }
}
