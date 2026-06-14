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

@DisplayName("SessionAwareClientToolDispatcher — 会话感知代理模式")
class SessionAwareClientToolDispatcherTest {

    private SessionAwareClientToolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new SessionAwareClientToolDispatcher();
    }

    @Test
    @DisplayName("初始状态无 delegate")
    void initiallyHasNoDelegate() {
        assertFalse(dispatcher.hasDelegate());
    }

    @Test
    @DisplayName("无 delegate 时 dispatch 返回失败 Future")
    void dispatchWithoutDelegateFailsFast() {
        CompletableFuture<ToolResult> future = dispatcher.dispatch("call_1", "get_position", null);

        assertTrue(future.isCompletedExceptionally());
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("get_position"));
    }

    @Test
    @DisplayName("设置 delegate 后 hasDelegate 为 true")
    void setDelegateMakesHasDelegateTrue() {
        dispatcher.setDelegate(stubDispatcher(ToolResult.success("ok")));
        assertTrue(dispatcher.hasDelegate());
    }

    @Test
    @DisplayName("设置 delegate 后 dispatch 转发到 delegate — 传输链验证")
    void dispatchForwardsToDelegate() throws Exception {
        AtomicReference<String> capturedCallId = new AtomicReference<>();
        AtomicReference<String> capturedToolName = new AtomicReference<>();

        ClientToolDispatcher delegate = (callId, toolName, directive) -> {
            capturedCallId.set(callId);
            capturedToolName.set(toolName);
            return CompletableFuture.completedFuture(ToolResult.success("position: 39.9,116.4"));
        };

        dispatcher.setDelegate(delegate);
        CompletableFuture<ToolResult> future = dispatcher.dispatch("call_42", "get_position", null);

        ToolResult result = future.get();
        assertEquals("call_42", capturedCallId.get());
        assertEquals("get_position", capturedToolName.get());
        assertFalse(result.isError());
        assertEquals("position: 39.9,116.4", result.getContent());
    }

    @Test
    @DisplayName("clearDelegate 后回到无 delegate 状态")
    void clearDelegateResetsState() {
        dispatcher.setDelegate(stubDispatcher(ToolResult.success("ok")));
        assertTrue(dispatcher.hasDelegate());

        dispatcher.clearDelegate();
        assertFalse(dispatcher.hasDelegate());
    }

    @Test
    @DisplayName("clearDelegate 后 dispatch 再次失败")
    void dispatchFailsAfterClearDelegate() {
        dispatcher.setDelegate(stubDispatcher(ToolResult.success("ok")));
        dispatcher.clearDelegate();

        CompletableFuture<ToolResult> future = dispatcher.dispatch("call_2", "take_photo", null);
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    @DisplayName("delegate 抛异常时 Future 传播异常")
    void delegateExceptionPropagates() {
        ClientToolDispatcher failingDelegate = (callId, toolName, directive) -> {
            CompletableFuture<ToolResult> f = new CompletableFuture<>();
            f.completeExceptionally(new RuntimeException("WebSocket closed"));
            return f;
        };

        dispatcher.setDelegate(failingDelegate);
        CompletableFuture<ToolResult> future = dispatcher.dispatch("call_3", "scan", null);

        assertTrue(future.isCompletedExceptionally());
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertEquals("WebSocket closed", ex.getCause().getMessage());
    }

    @Test
    @DisplayName("Directive 参数正确传递到 delegate")
    void directivePassedThrough() throws Exception {
        AtomicReference<Directive> capturedDirective = new AtomicReference<>();

        ClientToolDispatcher delegate = (callId, toolName, directive) -> {
            capturedDirective.set(directive);
            return CompletableFuture.completedFuture(ToolResult.success("done"));
        };

        Directive directive = new Directive("dir_1", "Camera", "TakePhoto",
            Map.of("resolution", "1080p"), 30000L);

        dispatcher.setDelegate(delegate);
        dispatcher.dispatch("call_4", "take_photo", directive).get();

        assertNotNull(capturedDirective.get());
        assertEquals("dir_1", capturedDirective.get().getDirectiveId());
        assertEquals("Camera", capturedDirective.get().getNamespace());
    }

    // ==================== Helper ====================

    private ClientToolDispatcher stubDispatcher(ToolResult result) {
        return (callId, toolName, directive) ->
            CompletableFuture.completedFuture(result);
    }
}
