package com.lightweightai.web.websocket;

import com.lightweightai.kernel.agent.directive.Directive;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("SpringWebSocketClientToolDispatcher")
class SpringWebSocketClientToolDispatcherTest {

    @Test
    @DisplayName("single-flight: 第二个 dispatch 应拒绝")
    void shouldRejectSecondDispatchWhenPending() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);

        SpringWebSocketClientToolDispatcher dispatcher = new SpringWebSocketClientToolDispatcher(session);

        var first = dispatcher.dispatch("c1", "GetPosition",
            new Directive("c1", "GeoInformation", "GetPosition", Map.of("precision", 3), 0));
        var second = dispatcher.dispatch("c2", "GetPosition",
            new Directive("c2", "GeoInformation", "GetPosition", Map.of("precision", 3), 0));

        CompletionException ex = assertThrows(CompletionException.class, second::join);
        assertTrue(ex.getCause().getMessage().contains("busy"));

        assertTrue(dispatcher.completeCall("c1", ToolResult.success("ok")));
        assertEquals(0, dispatcher.pendingCount());
        assertFalse(first.isCompletedExceptionally());
    }

    @Test
    @DisplayName("completeCall 应正确解析挂起的 Future")
    void shouldCompleteCallSuccessfully() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);

        SpringWebSocketClientToolDispatcher dispatcher = new SpringWebSocketClientToolDispatcher(session);

        var pending = dispatcher.dispatch("c1", "GetPosition",
            new Directive("c1", "GeoInformation", "GetPosition", Map.of(), 0));

        assertTrue(dispatcher.completeCall("c1", ToolResult.success("position data")));
        assertEquals("position data", pending.join().getContent());
        assertEquals(0, dispatcher.pendingCount());
    }

    @Test
    @DisplayName("completeLatestCall 应回退匹配")
    void shouldFallbackCompleteLatest() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);

        SpringWebSocketClientToolDispatcher dispatcher = new SpringWebSocketClientToolDispatcher(session);

        var pending = dispatcher.dispatch("c1", "GetPosition",
            new Directive("c1", "GeoInformation", "GetPosition", Map.of(), 0));

        assertTrue(dispatcher.completeLatestCall(ToolResult.success("ok")));
        assertEquals("ok", pending.join().getContent());
    }

    @Test
    @DisplayName("cancelAll 应取消所有挂起调用")
    void shouldCancelAllOnDisconnect() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);

        SpringWebSocketClientToolDispatcher dispatcher = new SpringWebSocketClientToolDispatcher(session);

        var pending = dispatcher.dispatch("c1", "GetPosition",
            new Directive("c1", "GeoInformation", "GetPosition", Map.of(), 0));

        dispatcher.cancelAll();
        assertTrue(pending.isCompletedExceptionally());
        assertEquals(0, dispatcher.pendingCount());
    }

    @Test
    @DisplayName("session 关闭时 dispatch 应失败")
    void shouldFailWhenSessionClosed() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(false);

        SpringWebSocketClientToolDispatcher dispatcher = new SpringWebSocketClientToolDispatcher(session);

        var future = dispatcher.dispatch("c1", "GetPosition",
            new Directive("c1", "GeoInformation", "GetPosition", Map.of(), 0));

        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    @DisplayName("dispatch 应通过 session 发送 DIRECTIVE 消息")
    void shouldSendDirectiveViaSession() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);

        SpringWebSocketClientToolDispatcher dispatcher = new SpringWebSocketClientToolDispatcher(session);

        dispatcher.dispatch("c1", "GetPosition",
            new Directive("c1", "GeoInformation", "GetPosition", Map.of("precision", 3), 0));

        verify(session).sendMessage(any(TextMessage.class));
    }
}
