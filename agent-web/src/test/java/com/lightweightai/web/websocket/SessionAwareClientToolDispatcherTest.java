package com.lightweightai.web.websocket;

import com.lightweightai.kernel.agent.ClientToolDispatcher;
import com.lightweightai.kernel.agent.directive.Directive;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class SessionAwareClientToolDispatcherTest {

    private SessionAwareClientToolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new SessionAwareClientToolDispatcher();
    }

    @Test
    void dispatchWithoutDelegateReturnsFailedFuture() {
        CompletableFuture<ToolResult> future = dispatcher.dispatch("call-1", "get_position", null);
        assertTrue(future.isCompletedExceptionally());

        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("get_position"));
    }

    @Test
    void dispatchWithDelegateDelegatesToIt() throws Exception {
        ToolResult expected = ToolResult.success("call-1", "{\"lat\":39.9}");
        ClientToolDispatcher mockDelegate = (callId, toolName, directive) ->
                CompletableFuture.completedFuture(expected);

        dispatcher.setDelegate(mockDelegate);

        CompletableFuture<ToolResult> future = dispatcher.dispatch("call-1", "get_position", null);
        assertSame(expected, future.get());
    }

    @Test
    void clearDelegateCausesSubsequentDispatchToFail() {
        ClientToolDispatcher mockDelegate = (callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success(callId, "ok"));

        dispatcher.setDelegate(mockDelegate);
        assertFalse(dispatcher.dispatch("c1", "tool", null).isCompletedExceptionally());

        dispatcher.clearDelegate();
        assertTrue(dispatcher.dispatch("c2", "tool", null).isCompletedExceptionally());
    }

    @Test
    void hasDelegateReturnsFalseInitially() {
        assertFalse(dispatcher.hasDelegate());
    }

    @Test
    void hasDelegateReturnsTrueAfterSet() {
        dispatcher.setDelegate((c, t, d) -> CompletableFuture.completedFuture(null));
        assertTrue(dispatcher.hasDelegate());
    }

    @Test
    void hasDelegateReturnsFalseAfterClear() {
        dispatcher.setDelegate((c, t, d) -> CompletableFuture.completedFuture(null));
        dispatcher.clearDelegate();
        assertFalse(dispatcher.hasDelegate());
    }

    @Test
    void lastDelegateWins() throws Exception {
        ToolResult result1 = ToolResult.success("c", "first");
        ToolResult result2 = ToolResult.success("c", "second");

        dispatcher.setDelegate((c, t, d) -> CompletableFuture.completedFuture(result1));
        dispatcher.setDelegate((c, t, d) -> CompletableFuture.completedFuture(result2));

        ToolResult actual = dispatcher.dispatch("c", "tool", null).get();
        assertSame(result2, actual);
    }

    @Test
    void delegateReceivesExactParameters() throws Exception {
        String[] captured = new String[2];
        ClientToolDispatcher delegate = (callId, toolName, directive) -> {
            captured[0] = callId;
            captured[1] = toolName;
            return CompletableFuture.completedFuture(ToolResult.success(callId, "ok"));
        };

        dispatcher.setDelegate(delegate);
        dispatcher.dispatch("my-call-id", "my-tool-name", null).get();

        assertEquals("my-call-id", captured[0]);
        assertEquals("my-tool-name", captured[1]);
    }
}
