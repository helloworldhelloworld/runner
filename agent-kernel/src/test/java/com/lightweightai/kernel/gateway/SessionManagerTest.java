package com.lightweightai.kernel.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionManager — interface contract & SessionContext")
class SessionManagerTest {

    @Test
    @DisplayName("SessionContext.simple() creates context with userId only")
    void simpleContextCreation() {
        SessionManager.SessionContext ctx = SessionManager.SessionContext.simple("user-1");

        assertEquals("user-1", ctx.getUserId());
        assertNull(ctx.getDeviceId());
        assertNull(ctx.getChannel());
        assertTrue(ctx.getExtra().isEmpty());
    }

    @Test
    @DisplayName("SessionContext full constructor sets all fields")
    void fullContextCreation() {
        Map<String, Object> extra = Map.of("key", "value");
        SessionManager.SessionContext ctx = new SessionManager.SessionContext(
                "user-2", "device-abc", "wechat", extra);

        assertEquals("user-2", ctx.getUserId());
        assertEquals("device-abc", ctx.getDeviceId());
        assertEquals("wechat", ctx.getChannel());
        assertEquals("value", ctx.getExtra().get("key"));
    }

    @Test
    @DisplayName("SessionContext extra is defensively copied")
    void extraIsDefensivelyCopied() {
        java.util.HashMap<String, Object> mutable = new java.util.HashMap<>();
        mutable.put("k", "v");
        SessionManager.SessionContext ctx = new SessionManager.SessionContext(
                "user", null, null, mutable);

        mutable.put("k", "changed");
        assertEquals("v", ctx.getExtra().get("k"));
    }

    @Test
    @DisplayName("SessionContext with null extra returns empty map")
    void nullExtraReturnsEmpty() {
        SessionManager.SessionContext ctx = new SessionManager.SessionContext(
                "user", null, null, null);

        assertNotNull(ctx.getExtra());
        assertTrue(ctx.getExtra().isEmpty());
    }

    @Test
    @DisplayName("default isSessionAlive returns true")
    void defaultIsSessionAlive() {
        SessionManager manager = new StubSessionManager();
        assertTrue(manager.isSessionAlive("any-session"));
    }

    @Test
    @DisplayName("default onSessionStart/Stop do not throw")
    void defaultLifecycleDoNotThrow() {
        SessionManager manager = new StubSessionManager();
        assertDoesNotThrow(() -> manager.onSessionStart("s1",
                SessionManager.SessionContext.simple("u1")));
        assertDoesNotThrow(() -> manager.onSessionStop("s1"));
    }

    private static class StubSessionManager implements SessionManager {
        @Override
        public List<Map<String, String>> getSessionHistory(String sessionId) {
            return List.of();
        }
        @Override
        public Map<String, Object> getSessionSummary(String sessionId) {
            return Map.of();
        }
        @Override
        public void clearSession(String sessionId) {}
    }
}
