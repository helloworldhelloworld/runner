package com.lightweightai.web.websocket;

import com.lightweightai.kernel.agent.ToolRegistry;
import io.vertx.core.http.ServerWebSocket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@DisplayName("ClientToolSessionBinder - session lifecycle & takeover")
class ClientToolSessionBinderExtendedTest {

    private ToolRegistry registry;
    private ClientToolSessionBinder binder;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        binder = new ClientToolSessionBinder(registry);
    }

    private VertxWebSocketClientToolDispatcher mockDispatcher() {
        return new VertxWebSocketClientToolDispatcher(mock(ServerWebSocket.class));
    }

    @Test
    @DisplayName("bind registers tool, unbind removes it")
    void bindAndUnbind() {
        binder.bind("s1", mockDispatcher());
        assertTrue(registry.has("GetPosition"));

        binder.unbind("s1");
        assertFalse(registry.has("GetPosition"));
    }

    @Test
    @DisplayName("second session takes over the tool from first session")
    void secondSessionTakesOver() {
        binder.bind("s1", mockDispatcher());
        assertTrue(registry.has("GetPosition"));

        binder.bind("s2", mockDispatcher());
        assertTrue(registry.has("GetPosition"), "Tool should still be registered after takeover");

        // s1 unbind should NOT remove the tool (s2 now owns it)
        binder.unbind("s1");
        assertTrue(registry.has("GetPosition"), "s2 still owns the tool after s1 unbind");

        // s2 unbind removes it
        binder.unbind("s2");
        assertFalse(registry.has("GetPosition"));
    }

    @Test
    @DisplayName("unbind unknown session is safe no-op")
    void unbindUnknownSessionIsSafe() {
        assertDoesNotThrow(() -> binder.unbind("nonexistent"));
    }

    @Test
    @DisplayName("double unbind same session is safe")
    void doubleUnbindIsSafe() {
        binder.bind("s1", mockDispatcher());
        binder.unbind("s1");
        assertDoesNotThrow(() -> binder.unbind("s1"));
    }

    @Test
    @DisplayName("rebind same session after unbind re-registers tool")
    void rebindAfterUnbind() {
        binder.bind("s1", mockDispatcher());
        binder.unbind("s1");
        assertFalse(registry.has("GetPosition"));

        binder.bind("s1", mockDispatcher());
        assertTrue(registry.has("GetPosition"));
    }

    @Test
    @DisplayName("bind same session twice replaces dispatcher")
    void bindSameSessionTwice() {
        binder.bind("s1", mockDispatcher());
        binder.bind("s1", mockDispatcher());
        assertTrue(registry.has("GetPosition"));

        binder.unbind("s1");
        assertFalse(registry.has("GetPosition"));
    }
}
