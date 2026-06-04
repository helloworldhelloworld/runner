package com.lightweightai.kernel.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionManager - 会话管理接口及 SessionContext")
class SessionManagerTest {

    @Nested
    @DisplayName("SessionContext 值对象")
    class SessionContextTests {

        @Test
        @DisplayName("完整构造函数保存所有字段")
        void fullConstructor() {
            SessionManager.SessionContext ctx = new SessionManager.SessionContext(
                    "user-1", "device-1", "web", Map.of("lang", "zh"));

            assertEquals("user-1", ctx.getUserId());
            assertEquals("device-1", ctx.getDeviceId());
            assertEquals("web", ctx.getChannel());
            assertEquals("zh", ctx.getExtra().get("lang"));
        }

        @Test
        @DisplayName("simple 工厂方法只需 userId")
        void simpleFactory() {
            SessionManager.SessionContext ctx = SessionManager.SessionContext.simple("user-1");

            assertEquals("user-1", ctx.getUserId());
            assertNull(ctx.getDeviceId());
            assertNull(ctx.getChannel());
            assertTrue(ctx.getExtra().isEmpty());
        }

        @Test
        @DisplayName("extra 为 null 时使用空 Map")
        void nullExtraBecomesEmptyMap() {
            SessionManager.SessionContext ctx = new SessionManager.SessionContext(
                    "user-1", "device-1", "web", null);

            assertNotNull(ctx.getExtra());
            assertTrue(ctx.getExtra().isEmpty());
        }

        @Test
        @DisplayName("extra Map 是不可变拷贝")
        void extraIsImmutableCopy() {
            Map<String, Object> original = new java.util.HashMap<>();
            original.put("key", "value");

            SessionManager.SessionContext ctx = new SessionManager.SessionContext(
                    "user-1", null, null, original);

            original.put("key2", "value2");
            assertFalse(ctx.getExtra().containsKey("key2"));

            assertThrows(UnsupportedOperationException.class,
                    () -> ctx.getExtra().put("new", "value"));
        }
    }

    @Nested
    @DisplayName("接口默认方法")
    class DefaultMethods {

        private final SessionManager defaultImpl = new SessionManager() {
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

        @Test
        @DisplayName("isSessionAlive 默认返回 true")
        void defaultIsSessionAlive() {
            assertTrue(defaultImpl.isSessionAlive("any-session"));
        }

        @Test
        @DisplayName("onSessionStart 默认为 no-op（不抛异常）")
        void defaultOnSessionStart() {
            assertDoesNotThrow(() ->
                    defaultImpl.onSessionStart("s1", SessionManager.SessionContext.simple("user")));
        }

        @Test
        @DisplayName("onSessionStop 默认为 no-op（不抛异常）")
        void defaultOnSessionStop() {
            assertDoesNotThrow(() -> defaultImpl.onSessionStop("s1"));
        }
    }
}
