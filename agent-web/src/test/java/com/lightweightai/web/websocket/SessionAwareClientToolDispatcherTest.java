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

@DisplayName("SessionAwareClientToolDispatcher - delegate-based dispatch")
class SessionAwareClientToolDispatcherTest {

    private SessionAwareClientToolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new SessionAwareClientToolDispatcher();
    }

    private Directive testDirective() {
        return new Directive("call-1", "NS", "Action", Map.of(), 5000);
    }

    @Test
    @DisplayName("dispatch fails when no delegate is set")
    void shouldFailWithoutDelegate() {
        assertFalse(dispatcher.hasDelegate());

        CompletableFuture<ToolResult> future = dispatcher.dispatch("id", "tool", testDirective());
        assertTrue(future.isCompletedExceptionally());

        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("No client connected"));
    }

    @Test
    @DisplayName("dispatch delegates to set delegate")
    void shouldDelegateWhenDelegateIsSet() throws Exception {
        ToolResult expected = ToolResult.success("result from client");
        ClientToolDispatcher mockDelegate = (callId, toolName, directive) ->
                CompletableFuture.completedFuture(expected);

        dispatcher.setDelegate(mockDelegate);
        assertTrue(dispatcher.hasDelegate());

        CompletableFuture<ToolResult> future = dispatcher.dispatch("id", "tool", testDirective());
        ToolResult actual = future.get();

        assertEquals("result from client", actual.getContent());
        assertFalse(actual.isError());
    }

    @Test
    @DisplayName("clearDelegate removes the delegate")
    void shouldClearDelegate() {
        ClientToolDispatcher mockDelegate = (callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("ok"));

        dispatcher.setDelegate(mockDelegate);
        assertTrue(dispatcher.hasDelegate());

        dispatcher.clearDelegate();
        assertFalse(dispatcher.hasDelegate());

        CompletableFuture<ToolResult> future = dispatcher.dispatch("id", "tool", testDirective());
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    @DisplayName("dispatch passes callId and toolName to delegate")
    void shouldPassCallIdAndToolNameToDelegate() throws Exception {
        String[] capturedCallId = new String[1];
        String[] capturedToolName = new String[1];

        ClientToolDispatcher capturingDelegate = (callId, toolName, directive) -> {
            capturedCallId[0] = callId;
            capturedToolName[0] = toolName;
            return CompletableFuture.completedFuture(ToolResult.success("ok"));
        };

        dispatcher.setDelegate(capturingDelegate);
        dispatcher.dispatch("call-123", "my_tool", testDirective()).get();

        assertEquals("call-123", capturedCallId[0]);
        assertEquals("my_tool", capturedToolName[0]);
    }

    @Test
    @DisplayName("setDelegate replaces previous delegate")
    void shouldReplacePreviousDelegate() throws Exception {
        ClientToolDispatcher first = (callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("first"));
        ClientToolDispatcher second = (callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("second"));

        dispatcher.setDelegate(first);
        dispatcher.setDelegate(second);

        ToolResult result = dispatcher.dispatch("id", "tool", testDirective()).get();
        assertEquals("second", result.getContent());
    }
}
