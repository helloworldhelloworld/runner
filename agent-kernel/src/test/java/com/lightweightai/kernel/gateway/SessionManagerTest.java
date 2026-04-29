package com.lightweightai.kernel.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionManager.SessionContext - 会话上下文")
class SessionManagerTest {

    @Nested
    @DisplayName("SessionContext 构造与访问")
    class SessionContextTests {

        @Test
        @DisplayName("完整构造保留所有字段")
        void fullConstruction() {
            SessionManager.SessionContext ctx = new SessionManager.SessionContext(
                    "user-123", "device-abc", "wechat", Map.of("lang", "zh"));

            assertEquals("user-123", ctx.getUserId());
            assertEquals("device-abc", ctx.getDeviceId());
            assertEquals("wechat", ctx.getChannel());
            assertEquals("zh", ctx.getExtra().get("lang"));
        }

        @Test
        @DisplayName("simple 工厂方法只设置 userId")
        void simpleFactory() {
            SessionManager.SessionContext ctx = SessionManager.SessionContext.simple("user-456");

            assertEquals("user-456", ctx.getUserId());
            assertNull(ctx.getDeviceId());
            assertNull(ctx.getChannel());
            assertTrue(ctx.getExtra().isEmpty());
        }

        @Test
        @DisplayName("null extra 转为空 Map")
        void nullExtraBecomesEmptyMap() {
            SessionManager.SessionContext ctx = new SessionManager.SessionContext(
                    "user", "device", "channel", null);

            assertNotNull(ctx.getExtra());
            assertTrue(ctx.getExtra().isEmpty());
        }

        @Test
        @DisplayName("extra 不可变 — 防御性复制")
        void extraIsUnmodifiable() {
            SessionManager.SessionContext ctx = new SessionManager.SessionContext(
                    "user", null, null, Map.of("k", "v"));

            assertThrows(UnsupportedOperationException.class, () ->
                    ctx.getExtra().put("new", "value"));
        }
    }

    @Nested
    @DisplayName("SessionManager 默认实现")
    class DefaultImplementationTests {

        @Test
        @DisplayName("default isSessionAlive 返回 true")
        void defaultIsSessionAliveReturnsTrue() {
            SessionManager manager = new TestSessionManager();
            assertTrue(manager.isSessionAlive("any-session"));
        }

        @Test
        @DisplayName("default onSessionStart 不抛异常")
        void defaultOnSessionStartNoOp() {
            SessionManager manager = new TestSessionManager();
            assertDoesNotThrow(() ->
                    manager.onSessionStart("sess-1", SessionManager.SessionContext.simple("user")));
        }

        @Test
        @DisplayName("default onSessionStop 不抛异常")
        void defaultOnSessionStopNoOp() {
            SessionManager manager = new TestSessionManager();
            assertDoesNotThrow(() -> manager.onSessionStop("sess-1"));
        }
    }

    private static class TestSessionManager implements SessionManager {
        @Override
        public java.util.List<Map<String, String>> getSessionHistory(String sessionId) {
            return java.util.List.of();
        }

        @Override
        public Map<String, Object> getSessionSummary(String sessionId) {
            return Map.of();
        }

        @Override
        public void clearSession(String sessionId) {
        }
    }
}
