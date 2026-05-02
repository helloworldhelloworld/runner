package com.lightweightai.web.websocket;

import com.lightweightai.kernel.agent.ClientToolDispatcher;
import com.lightweightai.kernel.agent.directive.Directive;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionAwareClientToolDispatcher - Lazy-binding client tool dispatcher")
class SessionAwareClientToolDispatcherTest {

    private SessionAwareClientToolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new SessionAwareClientToolDispatcher();
    }

    @Test
    @DisplayName("initially has no delegate")
    void initiallyNoDelegate() {
        assertFalse(dispatcher.hasDelegate());
    }

    @Test
    @DisplayName("dispatch without delegate completes exceptionally with IllegalStateException")
    void dispatchWithoutDelegateFails() {
        CompletableFuture<ToolResult> future = dispatcher.dispatch("call-1", "tool", null);

        assertTrue(future.isCompletedExceptionally());
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("tool"));
    }

    @Test
    @DisplayName("setDelegate makes hasDelegate return true")
    void setDelegateMakesHasDelegate() {
        dispatcher.setDelegate((callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("ok")));

        assertTrue(dispatcher.hasDelegate());
    }

    @Test
    @DisplayName("dispatch with delegate forwards to delegate")
    void dispatchWithDelegateForwards() throws Exception {
        AtomicReference<String> capturedCallId = new AtomicReference<>();
        AtomicReference<String> capturedToolName = new AtomicReference<>();

        dispatcher.setDelegate((callId, toolName, directive) -> {
            capturedCallId.set(callId);
            capturedToolName.set(toolName);
            return CompletableFuture.completedFuture(ToolResult.success("result-from-client"));
        });

        CompletableFuture<ToolResult> future = dispatcher.dispatch("call-42", "get_position", null);

        assertFalse(future.isCompletedExceptionally());
        ToolResult result = future.get();
        assertEquals("result-from-client", result.getContent());
        assertEquals("call-42", capturedCallId.get());
        assertEquals("get_position", capturedToolName.get());
    }

    @Test
    @DisplayName("clearDelegate removes delegate")
    void clearDelegateRemoves() {
        dispatcher.setDelegate((callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("ok")));
        assertTrue(dispatcher.hasDelegate());

        dispatcher.clearDelegate();

        assertFalse(dispatcher.hasDelegate());
    }

    @Test
    @DisplayName("dispatch after clearDelegate fails again")
    void dispatchAfterClearFails() {
        dispatcher.setDelegate((callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("ok")));
        dispatcher.clearDelegate();

        CompletableFuture<ToolResult> future = dispatcher.dispatch("call-2", "tool", null);
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    @DisplayName("replacing delegate uses new delegate")
    void replacingDelegateUsesNew() throws Exception {
        dispatcher.setDelegate((callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("first")));

        dispatcher.setDelegate((callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("second")));

        ToolResult result = dispatcher.dispatch("c", "t", null).get();
        assertEquals("second", result.getContent());
    }
}
