package com.lightweightai.web.websocket;

import com.lightweightai.kernel.agent.ClientToolDispatcher;
import com.lightweightai.kernel.agent.directive.Directive;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SessionAwareClientToolDispatcher 单元测试
 *
 * 覆盖关键路径：delegate 绑定/清除、无 delegate 时失败、正常转发
 */
class SessionAwareClientToolDispatcherTest {

    private SessionAwareClientToolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new SessionAwareClientToolDispatcher();
    }

    // ==================== 初始状态 ====================

    @Test
    @DisplayName("初始状态无 delegate")
    void shouldHaveNoDelegateInitially() {
        assertFalse(dispatcher.hasDelegate());
    }

    // ==================== 无 delegate 时调用 ====================

    @Test
    @DisplayName("无 delegate 时 dispatch 返回异常完成的 Future")
    void shouldReturnFailedFutureWhenNoDelegate() {
        Directive directive = Directive.fromToolName("call-1", "Camera.TakePhoto", Map.of(), 5000);

        CompletableFuture<ToolResult> future = dispatcher.dispatch("call-1", "Camera.TakePhoto", directive);

        assertTrue(future.isCompletedExceptionally());
        CompletionException ex = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("No client connected"));
        assertTrue(ex.getCause().getMessage().contains("Camera.TakePhoto"));
    }

    // ==================== delegate 绑定与转发 ====================

    @Test
    @DisplayName("setDelegate 后 hasDelegate 返回 true")
    void shouldHaveDelegateAfterSet() {
        dispatcher.setDelegate(new StubDispatcher());
        assertTrue(dispatcher.hasDelegate());
    }

    @Test
    @DisplayName("dispatch 正确转发到 delegate 并返回结果")
    void shouldForwardDispatchToDelegate() {
        RecordingDispatcher delegate = new RecordingDispatcher();
        delegate.response.complete(ToolResult.success("photo taken"));
        dispatcher.setDelegate(delegate);

        Directive directive = Directive.fromToolName("call-1", "Camera.TakePhoto", Map.of("res", "1080p"), 5000);
        CompletableFuture<ToolResult> future = dispatcher.dispatch("call-1", "Camera.TakePhoto", directive);

        ToolResult result = future.join();
        assertFalse(result.isError());
        assertEquals("photo taken", result.getContent());

        // 验证 delegate 收到了正确的参数
        assertEquals("call-1", delegate.receivedCallId);
        assertEquals("Camera.TakePhoto", delegate.receivedToolName);
        assertSame(directive, delegate.receivedDirective);
    }

    // ==================== clearDelegate ====================

    @Test
    @DisplayName("clearDelegate 后恢复无 delegate 状态")
    void shouldClearDelegate() {
        dispatcher.setDelegate(new StubDispatcher());
        assertTrue(dispatcher.hasDelegate());

        dispatcher.clearDelegate();
        assertFalse(dispatcher.hasDelegate());
    }

    @Test
    @DisplayName("clearDelegate 后 dispatch 返回失败 Future")
    void shouldFailAfterClearDelegate() {
        dispatcher.setDelegate(new StubDispatcher());
        dispatcher.clearDelegate();

        CompletableFuture<ToolResult> future = dispatcher.dispatch("call-2", "GPS.GetPosition",
                Directive.fromToolName("call-2", "GPS.GetPosition", Map.of(), 3000));

        assertTrue(future.isCompletedExceptionally());
    }

    // ==================== delegate 替换 ====================

    @Test
    @DisplayName("setDelegate 可以替换已有 delegate")
    void shouldReplaceExistingDelegate() {
        RecordingDispatcher first = new RecordingDispatcher();
        RecordingDispatcher second = new RecordingDispatcher();
        second.response.complete(ToolResult.success("from second"));

        dispatcher.setDelegate(first);
        dispatcher.setDelegate(second);

        Directive directive = Directive.fromToolName("call-3", "test", Map.of(), 1000);
        ToolResult result = dispatcher.dispatch("call-3", "test", directive).join();

        assertEquals("from second", result.getContent());
        assertNull(first.receivedCallId);
        assertEquals("call-3", second.receivedCallId);
    }

    // ==================== Test Helpers ====================

    private static class StubDispatcher implements ClientToolDispatcher {
        @Override
        public CompletableFuture<ToolResult> dispatch(String callId, String toolName, Directive directive) {
            return CompletableFuture.completedFuture(ToolResult.success("stub"));
        }
    }

    private static class RecordingDispatcher implements ClientToolDispatcher {
        String receivedCallId;
        String receivedToolName;
        Directive receivedDirective;
        CompletableFuture<ToolResult> response = new CompletableFuture<>();

        @Override
        public CompletableFuture<ToolResult> dispatch(String callId, String toolName, Directive directive) {
            this.receivedCallId = callId;
            this.receivedToolName = toolName;
            this.receivedDirective = directive;
            return response;
        }
    }
}
