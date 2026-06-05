package com.lightweightai.kernel.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionManager — interface defaults and SessionContext")
class SessionManagerTest {

    @Test
    @DisplayName("SessionContext.simple creates context with userId only")
    void sessionContextSimple() {
        SessionManager.SessionContext ctx = SessionManager.SessionContext.simple("user-1");
        assertEquals("user-1", ctx.getUserId());
        assertNull(ctx.getDeviceId());
        assertNull(ctx.getChannel());
        assertEquals(Map.of(), ctx.getExtra());
    }

    @Test
    @DisplayName("SessionContext stores all fields")
    void sessionContextFull() {
        Map<String, Object> extra = Map.of("platform", "iOS");
        SessionManager.SessionContext ctx = new SessionManager.SessionContext(
                "user-2", "device-abc", "wechat", extra);
        assertEquals("user-2", ctx.getUserId());
        assertEquals("device-abc", ctx.getDeviceId());
        assertEquals("wechat", ctx.getChannel());
        assertEquals(extra, ctx.getExtra());
    }

    @Test
    @DisplayName("SessionContext extra is immutable copy")
    void sessionContextExtraImmutable() {
        java.util.HashMap<String, Object> mutable = new java.util.HashMap<>();
        mutable.put("k", "v");
        SessionManager.SessionContext ctx = new SessionManager.SessionContext("u", null, null, mutable);
        mutable.put("k2", "v2");
        assertFalse(ctx.getExtra().containsKey("k2"));
    }

    @Test
    @DisplayName("SessionContext with null extra defaults to empty map")
    void sessionContextNullExtra() {
        SessionManager.SessionContext ctx = new SessionManager.SessionContext("u", null, null, null);
        assertNotNull(ctx.getExtra());
        assertTrue(ctx.getExtra().isEmpty());
    }

    @Test
    @DisplayName("default lifecycle methods are no-ops")
    void defaultLifecycleMethods() {
        SessionManager sm = new StubSessionManager();
        sm.onSessionStart("s1", SessionManager.SessionContext.simple("u1"));
        sm.onSessionStop("s1");
    }

    @Test
    @DisplayName("default isSessionAlive returns true")
    void defaultIsAlive() {
        SessionManager sm = new StubSessionManager();
        assertTrue(sm.isSessionAlive("any"));
    }

    @Test
    @DisplayName("concrete implementation works through interface")
    void concreteImpl() {
        StubSessionManager sm = new StubSessionManager();
        sm.addHistory("s1", Map.of("role", "user", "content", "hello"));
        assertEquals(1, sm.getSessionHistory("s1").size());
        sm.clearSession("s1");
        assertTrue(sm.getSessionHistory("s1").isEmpty());
    }

    private static class StubSessionManager implements SessionManager {
        private final java.util.Map<String, java.util.List<Map<String, String>>> store = new java.util.HashMap<>();

        void addHistory(String sid, Map<String, String> entry) {
            store.computeIfAbsent(sid, k -> new java.util.ArrayList<>()).add(entry);
        }

        @Override
        public List<Map<String, String>> getSessionHistory(String sid) {
            return store.getOrDefault(sid, List.of());
        }
        @Override
        public Map<String, Object> getSessionSummary(String sid) {
            return Map.of("sessionId", sid);
        }
        @Override
        public void clearSession(String sid) { store.remove(sid); }
    }
}
