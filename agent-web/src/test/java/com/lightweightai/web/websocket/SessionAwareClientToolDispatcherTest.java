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

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionAwareClientToolDispatcher — delegate lifecycle management")
class SessionAwareClientToolDispatcherTest {

    private SessionAwareClientToolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new SessionAwareClientToolDispatcher();
    }

    @Test
    @DisplayName("dispatch fails with exception when no delegate is set")
    void dispatchFailsWithoutDelegate() {
        assertFalse(dispatcher.hasDelegate());

        CompletableFuture<ToolResult> future = dispatcher.dispatch(
                "call-1", "get_position",
                new Directive("call-1", "Geo", "getPosition", Map.of(), 5000));

        assertTrue(future.isCompletedExceptionally());
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("No client connected"));
    }

    @Test
    @DisplayName("dispatch delegates to bound delegate")
    void dispatchDelegatesToBoundDelegate() throws ExecutionException, InterruptedException {
        ClientToolDispatcher mockDelegate = (callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("lat=31.2,lng=121.4"));

        dispatcher.setDelegate(mockDelegate);
        assertTrue(dispatcher.hasDelegate());

        CompletableFuture<ToolResult> future = dispatcher.dispatch(
                "call-2", "get_position",
                new Directive("call-2", "Geo", "getPosition", Map.of(), 5000));

        ToolResult result = future.get();
        assertFalse(result.isError());
        assertEquals("lat=31.2,lng=121.4", result.getContent());
    }

    @Test
    @DisplayName("clearDelegate makes dispatch fail again")
    void clearDelegateMakesDispatchFail() {
        ClientToolDispatcher mockDelegate = (callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("ok"));

        dispatcher.setDelegate(mockDelegate);
        assertTrue(dispatcher.hasDelegate());

        dispatcher.clearDelegate();
        assertFalse(dispatcher.hasDelegate());

        CompletableFuture<ToolResult> future = dispatcher.dispatch(
                "call-3", "camera",
                new Directive("call-3", "Camera", "capture", Map.of(), 5000));

        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    @DisplayName("setDelegate replaces previous delegate")
    void setDelegateReplacesExisting() throws ExecutionException, InterruptedException {
        ClientToolDispatcher first = (callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("first"));
        ClientToolDispatcher second = (callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("second"));

        dispatcher.setDelegate(first);
        dispatcher.setDelegate(second);

        ToolResult result = dispatcher.dispatch(
                "call-4", "tool",
                new Directive("call-4", "NS", "act", Map.of(), 5000)).get();

        assertEquals("second", result.getContent());
    }

    @Test
    @DisplayName("hasDelegate reflects current state accurately")
    void hasDelegateReflectsState() {
        assertFalse(dispatcher.hasDelegate());

        dispatcher.setDelegate((callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("ok")));
        assertTrue(dispatcher.hasDelegate());

        dispatcher.clearDelegate();
        assertFalse(dispatcher.hasDelegate());
    }

    @Test
    @DisplayName("dispatch passes callId, toolName, and directive to delegate")
    void dispatchPassesAllParameters() throws ExecutionException, InterruptedException {
        String[] capturedCallId = new String[1];
        String[] capturedToolName = new String[1];
        Directive[] capturedDirective = new Directive[1];

        ClientToolDispatcher capturingDelegate = (callId, toolName, directive) -> {
            capturedCallId[0] = callId;
            capturedToolName[0] = toolName;
            capturedDirective[0] = directive;
            return CompletableFuture.completedFuture(ToolResult.success("captured"));
        };

        dispatcher.setDelegate(capturingDelegate);

        Directive directive = new Directive("c1", "Geo", "getPos", Map.of("key", "val"), 10000);
        dispatcher.dispatch("c1", "get_position", directive).get();

        assertEquals("c1", capturedCallId[0]);
        assertEquals("get_position", capturedToolName[0]);
        assertSame(directive, capturedDirective[0]);
    }
}
