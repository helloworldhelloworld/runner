package com.lightweightai.web.websocket;

import com.lightweightai.kernel.agent.ClientToolDispatcher;
import com.lightweightai.kernel.agent.directive.Directive;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionAwareClientToolDispatcher")
class SessionAwareClientToolDispatcherTest {

    private SessionAwareClientToolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new SessionAwareClientToolDispatcher();
    }

    @Test
    @DisplayName("dispatch fails with no delegate set")
    void dispatchFailsWithNoDelegate() {
        assertFalse(dispatcher.hasDelegate());

        CompletableFuture<ToolResult> future = dispatcher.dispatch("call-1", "get_position", null);

        assertTrue(future.isCompletedExceptionally());
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("No client connected"));
    }

    @Test
    @DisplayName("dispatch delegates to set delegate")
    void dispatchDelegatesToSetDelegate() throws Exception {
        ToolResult expected = ToolResult.success("latitude=40,longitude=-74");
        ClientToolDispatcher mock = (callId, toolName, directive) ->
                CompletableFuture.completedFuture(expected);

        dispatcher.setDelegate(mock);
        assertTrue(dispatcher.hasDelegate());

        CompletableFuture<ToolResult> future = dispatcher.dispatch("call-2", "get_position", null);

        ToolResult result = future.get();
        assertFalse(result.isError());
        assertEquals("latitude=40,longitude=-74", result.getContent());
    }

    @Test
    @DisplayName("clearDelegate removes the delegate")
    void clearDelegateRemoves() {
        ClientToolDispatcher mock = (callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("ok"));

        dispatcher.setDelegate(mock);
        assertTrue(dispatcher.hasDelegate());

        dispatcher.clearDelegate();
        assertFalse(dispatcher.hasDelegate());

        CompletableFuture<ToolResult> future = dispatcher.dispatch("call-3", "any_tool", null);
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    @DisplayName("setDelegate replaces previous delegate")
    void setDelegateReplaces() throws Exception {
        ClientToolDispatcher first = (callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("first"));
        ClientToolDispatcher second = (callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("second"));

        dispatcher.setDelegate(first);
        dispatcher.setDelegate(second);

        ToolResult result = dispatcher.dispatch("call-4", "tool", null).get();
        assertEquals("second", result.getContent());
    }
}
