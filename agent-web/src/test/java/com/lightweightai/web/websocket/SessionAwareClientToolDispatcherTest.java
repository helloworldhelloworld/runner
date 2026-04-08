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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SessionAwareClientToolDispatcher 单元测试
 *
 * 覆盖关键路径：
 * - 未绑定 delegate 时 dispatch 返回失败 Future
 * - 绑定后正确透传到实际 delegate
 * - hasDelegate / setDelegate / clearDelegate 生命周期
 */
@DisplayName("SessionAwareClientToolDispatcher - 会话感知端工具分发")
class SessionAwareClientToolDispatcherTest {

    private SessionAwareClientToolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new SessionAwareClientToolDispatcher();
    }

    @Test
    @DisplayName("初始状态下 hasDelegate 应为 false")
    void shouldNotHaveDelegateInitially() {
        assertFalse(dispatcher.hasDelegate());
    }

    @Test
    @DisplayName("未绑定 delegate 时 dispatch 应返回失败 Future")
    void shouldFailDispatchWhenDelegateNotSet() {
        Directive directive = new Directive("d1", "Camera", "TakePhoto", Map.of(), 1000);

        CompletableFuture<ToolResult> future = dispatcher.dispatch("call-1", "Camera.TakePhoto", directive);

        assertTrue(future.isDone());
        assertTrue(future.isCompletedExceptionally());

        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("Camera.TakePhoto"));
    }

    @Test
    @DisplayName("setDelegate 后 hasDelegate 应为 true 并透传 dispatch")
    void shouldDelegateDispatchAfterSet() throws Exception {
        ClientToolDispatcher mockDelegate = mock(ClientToolDispatcher.class);
        ToolResult expected = ToolResult.success("call-1", "{\"lat\":30.0}");
        Directive directive = new Directive("d1", "GeoInformation", "GetPosition", Map.of(), 1000);
        when(mockDelegate.dispatch("call-1", "GeoInformation.GetPosition", directive))
            .thenReturn(CompletableFuture.completedFuture(expected));

        dispatcher.setDelegate(mockDelegate);

        assertTrue(dispatcher.hasDelegate());

        CompletableFuture<ToolResult> future =
            dispatcher.dispatch("call-1", "GeoInformation.GetPosition", directive);

        ToolResult actual = future.get(1, TimeUnit.SECONDS);
        assertSame(expected, actual);
        verify(mockDelegate, times(1)).dispatch("call-1", "GeoInformation.GetPosition", directive);
    }

    @Test
    @DisplayName("clearDelegate 后 dispatch 重新返回失败 Future")
    void shouldFailDispatchAfterClear() {
        ClientToolDispatcher mockDelegate = mock(ClientToolDispatcher.class);
        dispatcher.setDelegate(mockDelegate);
        dispatcher.clearDelegate();

        assertFalse(dispatcher.hasDelegate());

        Directive directive = new Directive("d2", "Camera", "TakePhoto", Map.of(), 1000);
        CompletableFuture<ToolResult> future = dispatcher.dispatch("call-2", "Camera.TakePhoto", directive);

        assertTrue(future.isCompletedExceptionally());
        verifyNoInteractions(mockDelegate);
    }

    @Test
    @DisplayName("setDelegate 覆盖旧 delegate,后续 dispatch 调用新 delegate")
    void shouldOverridePreviousDelegate() throws Exception {
        ClientToolDispatcher first = mock(ClientToolDispatcher.class);
        ClientToolDispatcher second = mock(ClientToolDispatcher.class);
        Directive directive = new Directive("d3", "Camera", "TakePhoto", Map.of(), 1000);
        when(second.dispatch(anyString(), anyString(), any(Directive.class)))
            .thenReturn(CompletableFuture.completedFuture(ToolResult.success("call-3", "ok")));

        dispatcher.setDelegate(first);
        dispatcher.setDelegate(second);

        dispatcher.dispatch("call-3", "Camera.TakePhoto", directive).get(1, TimeUnit.SECONDS);

        verifyNoInteractions(first);
        verify(second, times(1)).dispatch("call-3", "Camera.TakePhoto", directive);
    }
}
