package com.lightweightai.kernel.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionManager — session lifecycle interface")
class SessionManagerTest {

    @Test
    @DisplayName("default onSessionStart is a no-op")
    void defaultOnSessionStartIsNoop() {
        SessionManager sm = stubManager();
        assertDoesNotThrow(() -> sm.onSessionStart("s1", SessionManager.SessionContext.simple("u1")));
    }

    @Test
    @DisplayName("default onSessionStop is a no-op")
    void defaultOnSessionStopIsNoop() {
        SessionManager sm = stubManager();
        assertDoesNotThrow(() -> sm.onSessionStop("s1"));
    }

    @Test
    @DisplayName("default isSessionAlive returns true")
    void defaultIsSessionAliveReturnsTrue() {
        SessionManager sm = stubManager();
        assertTrue(sm.isSessionAlive("any-session"));
    }

    @Test
    @DisplayName("SessionContext.simple creates context with userId only")
    void sessionContextSimple() {
        SessionManager.SessionContext ctx = SessionManager.SessionContext.simple("user123");
        assertEquals("user123", ctx.getUserId());
        assertNull(ctx.getDeviceId());
        assertNull(ctx.getChannel());
        assertTrue(ctx.getExtra().isEmpty());
    }

    @Test
    @DisplayName("SessionContext preserves all fields")
    void sessionContextAllFields() {
        Map<String, Object> extra = Map.of("key", "value");
        SessionManager.SessionContext ctx = new SessionManager.SessionContext(
            "u1", "d1", "web", extra);

        assertEquals("u1", ctx.getUserId());
        assertEquals("d1", ctx.getDeviceId());
        assertEquals("web", ctx.getChannel());
        assertEquals("value", ctx.getExtra().get("key"));
    }

    @Test
    @DisplayName("SessionContext with null extra is safe")
    void sessionContextNullExtra() {
        SessionManager.SessionContext ctx = new SessionManager.SessionContext(
            "u1", null, null, null);

        assertNotNull(ctx.getExtra());
        assertTrue(ctx.getExtra().isEmpty());
    }

    @Test
    @DisplayName("SessionContext extra map is immutable copy")
    void sessionContextExtraImmutable() {
        java.util.HashMap<String, Object> mutable = new java.util.HashMap<>();
        mutable.put("k", "v");
        SessionManager.SessionContext ctx = new SessionManager.SessionContext(
            "u1", null, null, mutable);

        mutable.put("k2", "v2");
        assertFalse(ctx.getExtra().containsKey("k2"), "extra must be an immutable copy");

        assertThrows(UnsupportedOperationException.class,
            () -> ctx.getExtra().put("x", "y"));
    }

    private static SessionManager stubManager() {
        return new SessionManager() {
            @Override
            public java.util.List<Map<String, String>> getSessionHistory(String sessionId) {
                return java.util.List.of();
            }

            @Override
            public Map<String, Object> getSessionSummary(String sessionId) {
                return Map.of();
            }

            @Override
            public void clearSession(String sessionId) {}
        };
    }
}
