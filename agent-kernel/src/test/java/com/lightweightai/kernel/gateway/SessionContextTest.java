package com.lightweightai.kernel.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionManager.SessionContext - 会话启动上下文")
class SessionContextTest {

    @Nested
    @DisplayName("构造与字段")
    class ConstructionTests {

        @Test
        @DisplayName("完整构造所有字段正确填充")
        void fullConstruction() {
            Map<String, Object> extra = Map.of("locale", "zh-CN");
            SessionManager.SessionContext ctx = new SessionManager.SessionContext(
                    "user-1", "device-abc", "wechat", extra);

            assertEquals("user-1", ctx.getUserId());
            assertEquals("device-abc", ctx.getDeviceId());
            assertEquals("wechat", ctx.getChannel());
            assertEquals("zh-CN", ctx.getExtra().get("locale"));
        }

        @Test
        @DisplayName("simple 工厂方法仅设置 userId")
        void simpleFactory() {
            SessionManager.SessionContext ctx = SessionManager.SessionContext.simple("u1");

            assertEquals("u1", ctx.getUserId());
            assertNull(ctx.getDeviceId());
            assertNull(ctx.getChannel());
            assertTrue(ctx.getExtra().isEmpty());
        }

        @Test
        @DisplayName("extra 为 null 时默认为空 Map")
        void nullExtraDefaultsToEmpty() {
            SessionManager.SessionContext ctx = new SessionManager.SessionContext(
                    "u1", null, null, null);

            assertNotNull(ctx.getExtra());
            assertTrue(ctx.getExtra().isEmpty());
        }

        @Test
        @DisplayName("extra 是不可变的防御性拷贝")
        void extraIsImmutableCopy() {
            HashMap<String, Object> mutable = new HashMap<>();
            mutable.put("key", "value");
            SessionManager.SessionContext ctx = new SessionManager.SessionContext(
                    "u1", null, null, mutable);

            mutable.put("injected", "evil");
            assertFalse(ctx.getExtra().containsKey("injected"));

            assertThrows(UnsupportedOperationException.class, () ->
                    ctx.getExtra().put("another", "value"));
        }
    }

    @Nested
    @DisplayName("生命周期 defaults")
    class LifecycleDefaults {

        @Test
        @DisplayName("isSessionAlive 默认返回 true")
        void defaultSessionAlive() {
            SessionManager manager = new SessionManager() {
                @Override
                public java.util.List<Map<String, String>> getSessionHistory(String sid) {
                    return java.util.List.of();
                }
                @Override
                public Map<String, Object> getSessionSummary(String sid) {
                    return Map.of();
                }
                @Override
                public void clearSession(String sid) {}
            };

            assertTrue(manager.isSessionAlive("any-session"));
        }
    }
}
