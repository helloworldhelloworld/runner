package com.lightweightai.web.websocket;

import com.lightweightai.kernel.agent.ToolRegistry;
import io.vertx.core.http.ServerWebSocket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ClientToolSessionBinderTest {

    @Test
    void shouldRegisterAndUnregisterBuiltInClientToolsForSession() {
        ToolRegistry registry = new ToolRegistry();
        ClientToolSessionBinder binder = new ClientToolSessionBinder(registry);

        VertxWebSocketClientToolDispatcher dispatcher =
            new VertxWebSocketClientToolDispatcher(mock(ServerWebSocket.class));

        binder.bind("s1", dispatcher);
        assertTrue(registry.has("GetPosition"));

        binder.unbind("s1");
        assertFalse(registry.has("GetPosition"));
    }
}
