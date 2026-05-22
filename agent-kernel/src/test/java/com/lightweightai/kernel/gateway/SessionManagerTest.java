package com.lightweightai.kernel.gateway;

import com.lightweightai.kernel.gateway.SessionManager.SessionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionManager - 会话管理接口")
class SessionManagerTest {

    @Nested
    @DisplayName("SessionContext 构造")
    class SessionContextTests {

        @Test
        @DisplayName("完整构造并获取所有字段")
        void fullConstruction() {
            Map<String, Object> extra = Map.of("lang", "zh");
            SessionContext ctx = new SessionContext("user1", "dev1", "wechat", extra);

            assertEquals("user1", ctx.getUserId());
            assertEquals("dev1", ctx.getDeviceId());
            assertEquals("wechat", ctx.getChannel());
            assertEquals("zh", ctx.getExtra().get("lang"));
        }

        @Test
        @DisplayName("simple 工厂方法只设置 userId")
        void simpleFactory() {
            SessionContext ctx = SessionContext.simple("user2");

            assertEquals("user2", ctx.getUserId());
            assertNull(ctx.getDeviceId());
            assertNull(ctx.getChannel());
            assertTrue(ctx.getExtra().isEmpty());
        }

        @Test
        @DisplayName("null extra 默认为空不可变 Map")
        void nullExtraDefaultsToEmpty() {
            SessionContext ctx = new SessionContext("u", "d", "c", null);
            assertNotNull(ctx.getExtra());
            assertTrue(ctx.getExtra().isEmpty());
        }

        @Test
        @DisplayName("extra Map 是不可变拷贝")
        void extraIsImmutableCopy() {
            Map<String, Object> extra = new java.util.HashMap<>();
            extra.put("key", "val");
            SessionContext ctx = new SessionContext("u", null, null, extra);

            assertThrows(UnsupportedOperationException.class,
                    () -> ctx.getExtra().put("new", "entry"));
        }
    }

    @Nested
    @DisplayName("接口默认方法")
    class DefaultMethodTests {

        private final SessionManager manager = new SessionManager() {
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
        };

        @Test
        @DisplayName("isSessionAlive 默认返回 true")
        void defaultIsAlive() {
            assertTrue(manager.isSessionAlive("any-session"));
        }

        @Test
        @DisplayName("onSessionStart 默认为 no-op")
        void defaultOnSessionStartNoOp() {
            assertDoesNotThrow(() ->
                    manager.onSessionStart("s1", SessionContext.simple("u1")));
        }

        @Test
        @DisplayName("onSessionStop 默认为 no-op")
        void defaultOnSessionStopNoOp() {
            assertDoesNotThrow(() -> manager.onSessionStop("s1"));
        }
    }
}
