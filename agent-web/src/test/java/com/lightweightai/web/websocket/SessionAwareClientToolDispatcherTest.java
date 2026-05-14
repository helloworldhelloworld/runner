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

@DisplayName("SessionAwareClientToolDispatcher - 会话感知工具派发")
class SessionAwareClientToolDispatcherTest {

    private SessionAwareClientToolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new SessionAwareClientToolDispatcher();
    }

    @Test
    @DisplayName("无 delegate 时 dispatch 返回 exceptionally completed future")
    void dispatchWithoutDelegateReturnsFailedFuture() {
        CompletableFuture<ToolResult> future = dispatcher.dispatch("call-1", "get_position", null);
        assertTrue(future.isCompletedExceptionally());

        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("get_position"));
    }

    @Test
    @DisplayName("设置 delegate 后 dispatch 转发到 delegate")
    void dispatchWithDelegateDelegatesToActual() throws Exception {
        AtomicReference<String> capturedCallId = new AtomicReference<>();
        AtomicReference<String> capturedToolName = new AtomicReference<>();
        ToolResult expectedResult = ToolResult.success("position: 40.7,-74.0");

        ClientToolDispatcher mockDelegate = (callId, toolName, directive) -> {
            capturedCallId.set(callId);
            capturedToolName.set(toolName);
            return CompletableFuture.completedFuture(expectedResult);
        };

        dispatcher.setDelegate(mockDelegate);
        CompletableFuture<ToolResult> future = dispatcher.dispatch("call-2", "get_position", null);

        ToolResult result = future.get();
        assertEquals("call-2", capturedCallId.get());
        assertEquals("get_position", capturedToolName.get());
        assertEquals("position: 40.7,-74.0", result.getContent());
        assertFalse(result.isError());
    }

    @Test
    @DisplayName("hasDelegate 初始为 false")
    void hasDelegateInitiallyFalse() {
        assertFalse(dispatcher.hasDelegate());
    }

    @Test
    @DisplayName("setDelegate 后 hasDelegate 返回 true")
    void hasDelegateTrueAfterSet() {
        dispatcher.setDelegate((callId, toolName, directive) ->
            CompletableFuture.completedFuture(ToolResult.success("ok")));
        assertTrue(dispatcher.hasDelegate());
    }

    @Test
    @DisplayName("clearDelegate 后 hasDelegate 返回 false")
    void hasDelegateFalseAfterClear() {
        dispatcher.setDelegate((callId, toolName, directive) ->
            CompletableFuture.completedFuture(ToolResult.success("ok")));
        dispatcher.clearDelegate();
        assertFalse(dispatcher.hasDelegate());
    }

    @Test
    @DisplayName("clearDelegate 后 dispatch 再次返回 failed future")
    void dispatchAfterClearReturnsFailed() {
        dispatcher.setDelegate((callId, toolName, directive) ->
            CompletableFuture.completedFuture(ToolResult.success("ok")));
        dispatcher.clearDelegate();

        CompletableFuture<ToolResult> future = dispatcher.dispatch("call-3", "take_photo", null);
        assertTrue(future.isCompletedExceptionally());
    }
}
