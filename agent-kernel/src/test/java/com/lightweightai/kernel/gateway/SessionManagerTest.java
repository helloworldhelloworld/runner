package com.lightweightai.kernel.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionManager - 会话管理接口契约")
class SessionManagerTest {

    @Test
    @DisplayName("SessionContext.simple 创建最小上下文")
    void sessionContextSimple() {
        SessionManager.SessionContext ctx = SessionManager.SessionContext.simple("user123");
        assertEquals("user123", ctx.getUserId());
        assertNull(ctx.getDeviceId());
        assertNull(ctx.getChannel());
        assertTrue(ctx.getExtra().isEmpty());
    }

    @Test
    @DisplayName("SessionContext 完整构造")
    void sessionContextFull() {
        SessionManager.SessionContext ctx = new SessionManager.SessionContext(
            "user1", "device1", "websocket", Map.of("k", "v"));

        assertEquals("user1", ctx.getUserId());
        assertEquals("device1", ctx.getDeviceId());
        assertEquals("websocket", ctx.getChannel());
        assertEquals("v", ctx.getExtra().get("k"));
    }

    @Test
    @DisplayName("SessionContext extra 不可变")
    void sessionContextExtraImmutable() {
        SessionManager.SessionContext ctx = new SessionManager.SessionContext(
            "u1", null, null, Map.of("a", "b"));
        assertThrows(UnsupportedOperationException.class, () ->
            ctx.getExtra().put("c", "d"));
    }

    @Test
    @DisplayName("SessionContext null extra 变为空 Map")
    void sessionContextNullExtra() {
        SessionManager.SessionContext ctx = new SessionManager.SessionContext(
            "u1", null, null, null);
        assertNotNull(ctx.getExtra());
        assertTrue(ctx.getExtra().isEmpty());
    }

    @Test
    @DisplayName("默认接口方法的合理行为")
    void defaultMethodsBehavior() {
        SessionManager impl = new TestSessionManager();

        impl.onSessionStart("s1", SessionManager.SessionContext.simple("u1"));
        impl.onSessionStop("s1");
        assertTrue(impl.isSessionAlive("s1"));
    }

    @Test
    @DisplayName("具体实现正确处理 CRUD")
    void concreteImplementation() {
        TestSessionManager mgr = new TestSessionManager();
        mgr.addMessage("s1", "user", "hello");
        mgr.addMessage("s1", "assistant", "hi");

        List<Map<String, String>> history = mgr.getSessionHistory("s1");
        assertEquals(2, history.size());
        assertEquals("hello", history.get(0).get("content"));

        Map<String, Object> summary = mgr.getSessionSummary("s1");
        assertEquals(2, summary.get("messageCount"));

        mgr.clearSession("s1");
        assertTrue(mgr.getSessionHistory("s1").isEmpty());
    }

    @Test
    @DisplayName("不同 session 隔离")
    void sessionIsolation() {
        TestSessionManager mgr = new TestSessionManager();
        mgr.addMessage("s1", "user", "a");
        mgr.addMessage("s2", "user", "b");

        assertEquals(1, mgr.getSessionHistory("s1").size());
        assertEquals(1, mgr.getSessionHistory("s2").size());
        assertEquals("a", mgr.getSessionHistory("s1").get(0).get("content"));
        assertEquals("b", mgr.getSessionHistory("s2").get(0).get("content"));
    }

    private static class TestSessionManager implements SessionManager {
        private final java.util.concurrent.ConcurrentHashMap<String, List<Map<String, String>>> sessions =
            new java.util.concurrent.ConcurrentHashMap<>();

        void addMessage(String sessionId, String role, String content) {
            sessions.computeIfAbsent(sessionId, k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                .add(Map.of("role", role, "content", content));
        }

        @Override
        public List<Map<String, String>> getSessionHistory(String sessionId) {
            return sessions.getOrDefault(sessionId, List.of());
        }

        @Override
        public Map<String, Object> getSessionSummary(String sessionId) {
            return Map.of("messageCount", getSessionHistory(sessionId).size());
        }

        @Override
        public void clearSession(String sessionId) {
            sessions.remove(sessionId);
        }
    }
}
