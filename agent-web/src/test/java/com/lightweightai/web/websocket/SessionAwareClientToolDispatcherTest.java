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

@DisplayName("SessionAwareClientToolDispatcher — delegate binding, dispatch, and lifecycle")
class SessionAwareClientToolDispatcherTest {

    private SessionAwareClientToolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new SessionAwareClientToolDispatcher();
    }

    @Test
    @DisplayName("dispatch without delegate returns failed future with IllegalStateException")
    void dispatchWithoutDelegateFails() {
        Directive directive = new Directive("call-1", "Camera", "TakePhoto", Map.of(), 5000);
        CompletableFuture<ToolResult> future = dispatcher.dispatch("call-1", "Camera.TakePhoto", directive);

        assertTrue(future.isCompletedExceptionally());
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("No client connected"));
    }

    @Test
    @DisplayName("dispatch with delegate forwards call to delegate and returns its result")
    void dispatchWithDelegateForwards() throws Exception {
        AtomicReference<String> capturedCallId = new AtomicReference<>();
        AtomicReference<String> capturedToolName = new AtomicReference<>();
        AtomicReference<Directive> capturedDirective = new AtomicReference<>();

        ClientToolDispatcher mockDelegate = (callId, toolName, directive) -> {
            capturedCallId.set(callId);
            capturedToolName.set(toolName);
            capturedDirective.set(directive);
            return CompletableFuture.completedFuture(ToolResult.success("photo taken"));
        };

        dispatcher.setDelegate(mockDelegate);

        Directive directive = new Directive("call-2", "Camera", "TakePhoto",
                Map.of("resolution", "1080p"), 5000);
        CompletableFuture<ToolResult> future = dispatcher.dispatch("call-2", "Camera.TakePhoto", directive);

        ToolResult result = future.get();
        assertFalse(result.isError());
        assertEquals("photo taken", result.getContent());

        assertEquals("call-2", capturedCallId.get());
        assertEquals("Camera.TakePhoto", capturedToolName.get());
        assertEquals("Camera", capturedDirective.get().getNamespace());
    }

    @Test
    @DisplayName("clearDelegate causes subsequent dispatch to fail")
    void clearDelegateCausesFailure() {
        dispatcher.setDelegate((callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("ok")));

        assertTrue(dispatcher.hasDelegate());

        dispatcher.clearDelegate();

        assertFalse(dispatcher.hasDelegate());

        CompletableFuture<ToolResult> future = dispatcher.dispatch(
                "call-3", "tool", new Directive("call-3", "NS", "Act", Map.of(), 1000));
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    @DisplayName("setDelegate replaces previous delegate")
    void setDelegateReplacesExisting() throws Exception {
        dispatcher.setDelegate((callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("first")));

        dispatcher.setDelegate((callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("second")));

        Directive d = new Directive("c-4", "NS", "Act", Map.of(), 1000);
        ToolResult result = dispatcher.dispatch("c-4", "tool", d).get();
        assertEquals("second", result.getContent(), "Should use latest delegate");
    }

    @Test
    @DisplayName("hasDelegate reflects current binding state")
    void hasDelegateReflectsState() {
        assertFalse(dispatcher.hasDelegate(), "Initially no delegate");

        dispatcher.setDelegate((callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("ok")));
        assertTrue(dispatcher.hasDelegate(), "After setDelegate");

        dispatcher.clearDelegate();
        assertFalse(dispatcher.hasDelegate(), "After clearDelegate");
    }
}
