package com.lightweightai.kernel.gateway;

import com.lightweightai.kernel.gateway.SessionManager.SessionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionManager - 会话管理接口及 SessionContext")
class SessionManagerTest {

    @Nested
    @DisplayName("SessionContext")
    class SessionContextTest {

        @Test
        @DisplayName("完整构造 → 所有字段正确")
        void fullConstructor() {
            Map<String, Object> extra = Map.of("platform", "iOS", "version", "2.0");
            SessionContext ctx = new SessionContext("user-1", "device-abc", "wechat", extra);

            assertEquals("user-1", ctx.getUserId());
            assertEquals("device-abc", ctx.getDeviceId());
            assertEquals("wechat", ctx.getChannel());
            assertEquals("iOS", ctx.getExtra().get("platform"));
            assertEquals("2.0", ctx.getExtra().get("version"));
        }

        @Test
        @DisplayName("simple 工厂 → 只有 userId")
        void simpleFactory() {
            SessionContext ctx = SessionContext.simple("user-42");

            assertEquals("user-42", ctx.getUserId());
            assertNull(ctx.getDeviceId());
            assertNull(ctx.getChannel());
            assertTrue(ctx.getExtra().isEmpty());
        }

        @Test
        @DisplayName("extra 为 null → 使用空 Map")
        void nullExtraBecomesEmpty() {
            SessionContext ctx = new SessionContext("u", "d", "c", null);

            assertNotNull(ctx.getExtra());
            assertTrue(ctx.getExtra().isEmpty());
        }

        @Test
        @DisplayName("extra 是不可变副本")
        void extraIsImmutable() {
            SessionContext ctx = new SessionContext("u", null, null, Map.of("k", "v"));

            assertThrows(UnsupportedOperationException.class,
                    () -> ctx.getExtra().put("new", "val"));
        }
    }

    @Nested
    @DisplayName("接口默认方法")
    class DefaultMethods {

        @Test
        @DisplayName("onSessionStart 默认无操作")
        void defaultOnSessionStart() {
            SessionManager sm = minimalImpl();
            assertDoesNotThrow(() -> sm.onSessionStart("s1", SessionContext.simple("u1")));
        }

        @Test
        @DisplayName("onSessionStop 默认无操作")
        void defaultOnSessionStop() {
            SessionManager sm = minimalImpl();
            assertDoesNotThrow(() -> sm.onSessionStop("s1"));
        }

        @Test
        @DisplayName("isSessionAlive 默认返回 true")
        void defaultIsAlive() {
            SessionManager sm = minimalImpl();
            assertTrue(sm.isSessionAlive("any-session"));
        }

        private SessionManager minimalImpl() {
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
}
