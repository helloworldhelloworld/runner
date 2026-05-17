package com.lightweightai.kernel.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionManager — session lifecycle and isolation")
class SessionManagerTest {

    @Test
    @DisplayName("sessions are isolated by sessionId")
    void sessionsIsolatedById() {
        InMemorySessionManager mgr = new InMemorySessionManager();

        mgr.addToHistory("session-1", Map.of("role", "user", "content", "hello from 1"));
        mgr.addToHistory("session-2", Map.of("role", "user", "content", "hello from 2"));

        List<Map<String, String>> history1 = mgr.getSessionHistory("session-1");
        List<Map<String, String>> history2 = mgr.getSessionHistory("session-2");

        assertEquals(1, history1.size());
        assertEquals("hello from 1", history1.get(0).get("content"));
        assertEquals(1, history2.size());
        assertEquals("hello from 2", history2.get(0).get("content"));
    }

    @Test
    @DisplayName("clearSession removes all history for that session only")
    void clearSessionRemovesOnlyTargetSession() {
        InMemorySessionManager mgr = new InMemorySessionManager();

        mgr.addToHistory("s1", Map.of("role", "user", "content", "msg1"));
        mgr.addToHistory("s2", Map.of("role", "user", "content", "msg2"));

        mgr.clearSession("s1");

        assertTrue(mgr.getSessionHistory("s1").isEmpty());
        assertEquals(1, mgr.getSessionHistory("s2").size());
    }

    @Test
    @DisplayName("onSessionStart lifecycle callback fires with context")
    void onSessionStartCallbackFires() {
        LifecycleTrackingSessionManager mgr = new LifecycleTrackingSessionManager();
        SessionManager.SessionContext ctx = new SessionManager.SessionContext("user1", "device1", "web", null);

        mgr.onSessionStart("s1", ctx);

        assertEquals(1, mgr.startedSessions.size());
        assertEquals("s1", mgr.startedSessions.get(0));
        assertEquals("user1", mgr.lastContext.getUserId());
        assertEquals("device1", mgr.lastContext.getDeviceId());
        assertEquals("web", mgr.lastContext.getChannel());
    }

    @Test
    @DisplayName("onSessionStop lifecycle callback fires")
    void onSessionStopCallbackFires() {
        LifecycleTrackingSessionManager mgr = new LifecycleTrackingSessionManager();

        mgr.onSessionStop("s1");

        assertEquals(1, mgr.stoppedSessions.size());
        assertEquals("s1", mgr.stoppedSessions.get(0));
    }

    @Test
    @DisplayName("isSessionAlive defaults to true")
    void isSessionAliveDefaultsTrue() {
        SessionManager mgr = new SessionManager() {
            @Override public List<Map<String, String>> getSessionHistory(String s) { return List.of(); }
            @Override public Map<String, Object> getSessionSummary(String s) { return Map.of(); }
            @Override public void clearSession(String s) {}
        };

        assertTrue(mgr.isSessionAlive("any-session"));
    }

    @Test
    @DisplayName("SessionContext.simple creates minimal context")
    void sessionContextSimple() {
        SessionManager.SessionContext ctx = SessionManager.SessionContext.simple("user42");

        assertEquals("user42", ctx.getUserId());
        assertNull(ctx.getDeviceId());
        assertNull(ctx.getChannel());
        assertTrue(ctx.getExtra().isEmpty());
    }

    @Test
    @DisplayName("SessionContext extra map is immutable")
    void sessionContextExtraImmutable() {
        Map<String, Object> extra = new HashMap<>();
        extra.put("key", "value");
        SessionManager.SessionContext ctx = new SessionManager.SessionContext("u", "d", "c", extra);

        assertThrows(UnsupportedOperationException.class, () -> ctx.getExtra().put("new", "val"));
    }

    @Test
    @DisplayName("getSessionSummary returns summary for a session")
    void getSessionSummary() {
        InMemorySessionManager mgr = new InMemorySessionManager();
        mgr.addToHistory("s1", Map.of("role", "user", "content", "hi"));
        mgr.addToHistory("s1", Map.of("role", "assistant", "content", "hello"));

        Map<String, Object> summary = mgr.getSessionSummary("s1");
        assertEquals(2, summary.get("messageCount"));
    }

    // ==================== Test Implementations ====================

    private static class InMemorySessionManager implements SessionManager {
        private final Map<String, List<Map<String, String>>> histories = new ConcurrentHashMap<>();

        void addToHistory(String sessionId, Map<String, String> msg) {
            histories.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(msg);
        }

        @Override
        public List<Map<String, String>> getSessionHistory(String sessionId) {
            return histories.getOrDefault(sessionId, List.of());
        }

        @Override
        public Map<String, Object> getSessionSummary(String sessionId) {
            List<Map<String, String>> h = histories.getOrDefault(sessionId, List.of());
            return Map.of("messageCount", h.size());
        }

        @Override
        public void clearSession(String sessionId) {
            histories.remove(sessionId);
        }
    }

    private static class LifecycleTrackingSessionManager implements SessionManager {
        final List<String> startedSessions = new ArrayList<>();
        final List<String> stoppedSessions = new ArrayList<>();
        SessionManager.SessionContext lastContext;

        @Override
        public List<Map<String, String>> getSessionHistory(String s) { return List.of(); }
        @Override
        public Map<String, Object> getSessionSummary(String s) { return Map.of(); }
        @Override
        public void clearSession(String s) {}

        @Override
        public void onSessionStart(String sessionId, SessionContext context) {
            startedSessions.add(sessionId);
            lastContext = context;
        }

        @Override
        public void onSessionStop(String sessionId) {
            stoppedSessions.add(sessionId);
        }
    }
}
