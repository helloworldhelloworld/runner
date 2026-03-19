package com.lightweightai.web.websocket;

import com.lightweightai.kernel.llm.ToolResult;
import io.vertx.core.Future;
import io.vertx.core.http.ServerWebSocket;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VertxWebSocketClientToolDispatcherTest {

    @Test
    void shouldRejectSecondDispatchWhenSingleFlightPending() {
        ServerWebSocket ws = mock(ServerWebSocket.class);
        when(ws.isClosed()).thenReturn(false);
        when(ws.writeTextMessage(anyString())).thenReturn(Future.succeededFuture());

        VertxWebSocketClientToolDispatcher dispatcher = new VertxWebSocketClientToolDispatcher(ws);

        var first = dispatcher.dispatch("c1", "GeoInformation.GetPosition", Map.of("precision", 3));
        var second = dispatcher.dispatch("c2", "GeoInformation.GetPosition", Map.of("precision", 3));

        CompletionException ex = assertThrows(CompletionException.class, second::join);
        assertTrue(ex.getCause().getMessage().contains("busy"));

        assertTrue(dispatcher.completeCall("c1", ToolResult.success("ok")));
        assertEquals(0, dispatcher.pendingCount());
        assertFalse(first.isCompletedExceptionally());
    }

    @Test
    void shouldFallbackCompleteLatestWhenIdMissing() {
        ServerWebSocket ws = mock(ServerWebSocket.class);
        when(ws.isClosed()).thenReturn(false);
        when(ws.writeTextMessage(anyString())).thenReturn(Future.succeededFuture());

        VertxWebSocketClientToolDispatcher dispatcher = new VertxWebSocketClientToolDispatcher(ws);

        var pending = dispatcher.dispatch("c1", "GeoInformation.GetPosition", Map.of("precision", 3));

        assertTrue(dispatcher.completeLatestCall(ToolResult.success("ok")));
        assertEquals("ok", pending.join().getContent());
        assertEquals(0, dispatcher.pendingCount());
    }
}
