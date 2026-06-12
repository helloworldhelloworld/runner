package com.lightweightai.web.websocket;

import com.lightweightai.kernel.agent.ToolRegistry;
import io.vertx.core.http.ServerWebSocket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@DisplayName("ClientToolSessionBinder - ownership handoff & concurrent sessions")
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
    @DisplayName("bind registers GetPosition tool in the global registry")
    void bindRegistersTools() {
        binder.bind("s1", mockDispatcher());
        assertTrue(registry.has("GetPosition"));
    }

    @Test
    @DisplayName("unbind removes tools from the global registry")
    void unbindRemovesTools() {
        binder.bind("s1", mockDispatcher());
        binder.unbind("s1");
        assertFalse(registry.has("GetPosition"));
    }

    @Test
    @DisplayName("second session takes ownership, first unbind does NOT remove tool")
    void ownershipHandoff() {
        binder.bind("s1", mockDispatcher());
        binder.bind("s2", mockDispatcher());

        assertTrue(registry.has("GetPosition"), "tool should still be registered after handoff");

        binder.unbind("s1");
        assertTrue(registry.has("GetPosition"),
                "s1 lost ownership, unbind must NOT remove tool owned by s2");

        binder.unbind("s2");
        assertFalse(registry.has("GetPosition"),
                "s2 is the current owner, unbind should remove tool");
    }

    @Test
    @DisplayName("unbind for unknown session is a no-op")
    void unbindUnknownSessionIsNoop() {
        assertDoesNotThrow(() -> binder.unbind("nonexistent"));
    }

    @Test
    @DisplayName("double unbind for same session is safe")
    void doubleUnbindIsSafe() {
        binder.bind("s1", mockDispatcher());
        binder.unbind("s1");
        assertDoesNotThrow(() -> binder.unbind("s1"));
    }

    @Test
    @DisplayName("rebind same session re-registers tools")
    void rebindSameSession() {
        binder.bind("s1", mockDispatcher());
        binder.unbind("s1");
        assertFalse(registry.has("GetPosition"));

        binder.bind("s1", mockDispatcher());
        assertTrue(registry.has("GetPosition"));
    }

    @Test
    @DisplayName("three sessions with sequential handoff")
    void threeSessionHandoff() {
        binder.bind("s1", mockDispatcher());
        binder.bind("s2", mockDispatcher());
        binder.bind("s3", mockDispatcher());

        binder.unbind("s1");
        assertTrue(registry.has("GetPosition"), "s3 owns it");

        binder.unbind("s2");
        assertTrue(registry.has("GetPosition"), "s3 still owns it");

        binder.unbind("s3");
        assertFalse(registry.has("GetPosition"), "no owner left");
    }
}
