package com.lightweightai.web.websocket;

import com.lightweightai.kernel.agent.ClientToolDispatcher;
import com.lightweightai.kernel.agent.directive.Directive;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SessionAwareClientToolDispatcher — delegate binding/unbinding and dispatch chain.
 *
 * This is a critical transmission test: verifies that dispatch calls
 * are correctly routed to the delegate when bound, and fail gracefully
 * when no client is connected.
 */
@DisplayName("SessionAwareClientToolDispatcher — delegation chain")
class SessionAwareClientToolDispatcherTest {

    private SessionAwareClientToolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new SessionAwareClientToolDispatcher();
    }

    @Test
    @DisplayName("dispatch fails with IllegalStateException when no delegate is set")
    void dispatchFailsWithoutDelegate() {
        assertFalse(dispatcher.hasDelegate());

        CompletableFuture<ToolResult> future = dispatcher.dispatch(
                "call-1", "GetPosition",
                Directive.fromToolName("call-1", "GetPosition", Map.of(), 5000));

        assertTrue(future.isCompletedExceptionally());
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("No client connected"));
    }

    @Test
    @DisplayName("dispatch routes to delegate when bound")
    void dispatchRoutesToDelegate() throws Exception {
        AtomicReference<String> capturedCallId = new AtomicReference<>();
        AtomicReference<String> capturedToolName = new AtomicReference<>();
        AtomicReference<Directive> capturedDirective = new AtomicReference<>();

        ClientToolDispatcher mockDelegate = (callId, toolName, directive) -> {
            capturedCallId.set(callId);
            capturedToolName.set(toolName);
            capturedDirective.set(directive);
            return CompletableFuture.completedFuture(ToolResult.success("lat=31.2,lon=121.4"));
        };

        dispatcher.setDelegate(mockDelegate);
        assertTrue(dispatcher.hasDelegate());

        Directive directive = Directive.fromToolName("call-1", "GetPosition", Map.of(), 5000);
        CompletableFuture<ToolResult> future = dispatcher.dispatch("call-1", "GetPosition", directive);
        ToolResult result = future.get();

        assertEquals("call-1", capturedCallId.get());
        assertEquals("GetPosition", capturedToolName.get());
        assertNotNull(capturedDirective.get());
        assertFalse(result.isError());
        assertEquals("lat=31.2,lon=121.4", result.getContent());
    }

    @Test
    @DisplayName("clearDelegate makes dispatch fail again")
    void clearDelegateRestoresFailure() {
        ClientToolDispatcher mockDelegate = (callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("ok"));

        dispatcher.setDelegate(mockDelegate);
        assertTrue(dispatcher.hasDelegate());

        dispatcher.clearDelegate();
        assertFalse(dispatcher.hasDelegate());

        CompletableFuture<ToolResult> future = dispatcher.dispatch(
                "call-2", "TakePhoto",
                Directive.fromToolName("call-2", "TakePhoto", Map.of(), 5000));
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    @DisplayName("setDelegate replaces previous delegate")
    void setDelegateReplaces() throws Exception {
        AtomicReference<String> calledDelegate = new AtomicReference<>();

        ClientToolDispatcher first = (callId, toolName, directive) -> {
            calledDelegate.set("first");
            return CompletableFuture.completedFuture(ToolResult.success("first"));
        };
        ClientToolDispatcher second = (callId, toolName, directive) -> {
            calledDelegate.set("second");
            return CompletableFuture.completedFuture(ToolResult.success("second"));
        };

        dispatcher.setDelegate(first);
        dispatcher.setDelegate(second);

        Directive d = Directive.fromToolName("c", "t", Map.of(), 1000);
        dispatcher.dispatch("c", "t", d).get();

        assertEquals("second", calledDelegate.get());
    }

    @Test
    @DisplayName("dispatch is thread-safe during delegate swap")
    void threadSafetyDuringSwap() throws Exception {
        ClientToolDispatcher slowDelegate = (callId, toolName, directive) -> {
            CompletableFuture<ToolResult> f = new CompletableFuture<>();
            new Thread(() -> {
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                f.complete(ToolResult.success("slow"));
            }).start();
            return f;
        };

        dispatcher.setDelegate(slowDelegate);

        Directive d = Directive.fromToolName("c", "t", Map.of(), 5000);
        CompletableFuture<ToolResult> inflight = dispatcher.dispatch("c", "t", d);

        dispatcher.clearDelegate();

        ToolResult result = inflight.get();
        assertEquals("slow", result.getContent());
    }
}
