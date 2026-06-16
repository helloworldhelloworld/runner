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

@DisplayName("SessionAwareClientToolDispatcher - delegate lifecycle and dispatch")
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
    @DisplayName("dispatch without delegate returns failed future")
    void dispatchWithoutDelegate() {
        CompletableFuture<ToolResult> future = dispatcher.dispatch("call-1", "get_position", null);

        assertTrue(future.isCompletedExceptionally());
        assertThrows(ExecutionException.class, future::get);

        try {
            future.get();
        } catch (ExecutionException e) {
            assertInstanceOf(IllegalStateException.class, e.getCause());
            assertTrue(e.getCause().getMessage().contains("get_position"));
        } catch (InterruptedException e) {
            fail("Unexpected interruption");
        }
    }

    @Test
    @DisplayName("setDelegate enables dispatch")
    void setDelegateEnablesDispatch() {
        ToolResult expected = ToolResult.success("position: 37.7749,-122.4194");
        ClientToolDispatcher mockDelegate = (callId, toolName, directive) ->
                CompletableFuture.completedFuture(expected);

        dispatcher.setDelegate(mockDelegate);

        assertTrue(dispatcher.hasDelegate());

        CompletableFuture<ToolResult> future = dispatcher.dispatch("call-2", "get_position", null);
        ToolResult result = future.join();

        assertFalse(result.isError());
        assertEquals("position: 37.7749,-122.4194", result.getContent());
    }

    @Test
    @DisplayName("clearDelegate causes subsequent dispatches to fail")
    void clearDelegateDisablesDispatch() {
        ClientToolDispatcher mockDelegate = (callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("ok"));

        dispatcher.setDelegate(mockDelegate);
        assertTrue(dispatcher.hasDelegate());

        dispatcher.clearDelegate();
        assertFalse(dispatcher.hasDelegate());

        CompletableFuture<ToolResult> future = dispatcher.dispatch("call-3", "tool", null);
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    @DisplayName("delegate receives correct callId and toolName")
    void delegateReceivesCorrectArgs() {
        String[] captured = new String[2];
        ClientToolDispatcher captureDelegate = (callId, toolName, directive) -> {
            captured[0] = callId;
            captured[1] = toolName;
            return CompletableFuture.completedFuture(ToolResult.success("ok"));
        };

        dispatcher.setDelegate(captureDelegate);
        dispatcher.dispatch("my-call-id", "my_tool", null).join();

        assertEquals("my-call-id", captured[0]);
        assertEquals("my_tool", captured[1]);
    }

    @Test
    @DisplayName("replacing delegate switches to new one")
    void replacingDelegate() {
        ClientToolDispatcher first = (callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("first"));
        ClientToolDispatcher second = (callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("second"));

        dispatcher.setDelegate(first);
        assertEquals("first", dispatcher.dispatch("c1", "t", null).join().getContent());

        dispatcher.setDelegate(second);
        assertEquals("second", dispatcher.dispatch("c2", "t", null).join().getContent());
    }
}
