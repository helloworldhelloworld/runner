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

@DisplayName("SessionAwareClientToolDispatcher — 会话感知延迟绑定调度器")
class SessionAwareClientToolDispatcherTest {

    private SessionAwareClientToolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new SessionAwareClientToolDispatcher();
    }

    @Test
    @DisplayName("初始状态无 delegate — hasDelegate() 为 false")
    void initiallyNoDelegate() {
        assertFalse(dispatcher.hasDelegate());
    }

    @Test
    @DisplayName("无 delegate 时 dispatch 返回异常完成的 Future")
    void noDelegateDispatchFails() {
        CompletableFuture<ToolResult> future = dispatcher.dispatch(
                "call-1", "test_tool",
                new Directive("call-1", "ns", "action", Map.of(), 5000));

        assertTrue(future.isCompletedExceptionally());
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    @Test
    @DisplayName("setDelegate 后 hasDelegate() 为 true 且 dispatch 转发")
    void setDelegateEnablesDispatch() throws Exception {
        ToolResult expected = ToolResult.success("delegate result");
        dispatcher.setDelegate((callId, toolName, directive) ->
                CompletableFuture.completedFuture(expected));

        assertTrue(dispatcher.hasDelegate());

        CompletableFuture<ToolResult> future = dispatcher.dispatch(
                "call-1", "test_tool",
                new Directive("call-1", "ns", "action", Map.of(), 5000));

        ToolResult actual = future.get();
        assertSame(expected, actual,
                "dispatch should forward to delegate and return its result");
    }

    @Test
    @DisplayName("clearDelegate 后 hasDelegate() 回到 false 且 dispatch 失败")
    void clearDelegateDisablesDispatch() {
        dispatcher.setDelegate((callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("ok")));
        dispatcher.clearDelegate();

        assertFalse(dispatcher.hasDelegate());

        CompletableFuture<ToolResult> future = dispatcher.dispatch(
                "call-1", "tool",
                new Directive("call-1", "ns", "action", Map.of(), 5000));
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    @DisplayName("dispatch 传递正确的 callId, toolName, directive 到 delegate")
    void dispatchPassesCorrectArguments() throws Exception {
        AtomicReference<String> capturedCallId = new AtomicReference<>();
        AtomicReference<String> capturedToolName = new AtomicReference<>();
        AtomicReference<Directive> capturedDirective = new AtomicReference<>();

        dispatcher.setDelegate((callId, toolName, directive) -> {
            capturedCallId.set(callId);
            capturedToolName.set(toolName);
            capturedDirective.set(directive);
            return CompletableFuture.completedFuture(ToolResult.success("ok"));
        });

        Directive dir = new Directive("my-call", "Camera", "TakePhoto", Map.of("res", "1080p"), 30000);
        dispatcher.dispatch("my-call", "Camera.TakePhoto", dir);

        assertEquals("my-call", capturedCallId.get(),
                "delegate should receive exact callId");
        assertEquals("Camera.TakePhoto", capturedToolName.get(),
                "delegate should receive exact toolName");
        assertSame(dir, capturedDirective.get(),
                "delegate should receive exact directive object");
    }
}
