package com.lightweightai.kernel.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionManager.SessionContext - session lifecycle data")
class SessionContextTest {

    @Nested
    @DisplayName("Full constructor")
    class FullConstructor {

        @Test
        @DisplayName("stores all fields correctly")
        void allFields() {
            Map<String, Object> extra = Map.of("lang", "zh", "timezone", "Asia/Shanghai");
            SessionManager.SessionContext ctx = new SessionManager.SessionContext(
                "user-123", "device-456", "wechat", extra);

            assertEquals("user-123", ctx.getUserId());
            assertEquals("device-456", ctx.getDeviceId());
            assertEquals("wechat", ctx.getChannel());
            assertEquals("zh", ctx.getExtra().get("lang"));
            assertEquals("Asia/Shanghai", ctx.getExtra().get("timezone"));
        }

        @Test
        @DisplayName("null extra defaults to empty map")
        void nullExtraDefaultsToEmpty() {
            SessionManager.SessionContext ctx = new SessionManager.SessionContext(
                "user-1", "dev-1", "web", null);

            assertNotNull(ctx.getExtra());
            assertTrue(ctx.getExtra().isEmpty());
        }

        @Test
        @DisplayName("extra map is immutable copy")
        void extraIsImmutableCopy() {
            Map<String, Object> extra = new HashMap<>();
            extra.put("key", "original");
            SessionManager.SessionContext ctx = new SessionManager.SessionContext(
                "user-1", null, null, extra);

            extra.put("injected", "value");
            assertFalse(ctx.getExtra().containsKey("injected"));

            assertThrows(UnsupportedOperationException.class,
                () -> ctx.getExtra().put("mutate", "fail"));
        }
    }

    @Nested
    @DisplayName("simple factory method")
    class SimpleFactory {

        @Test
        @DisplayName("creates context with userId only, others null/empty")
        void simpleContext() {
            SessionManager.SessionContext ctx = SessionManager.SessionContext.simple("user-abc");

            assertEquals("user-abc", ctx.getUserId());
            assertNull(ctx.getDeviceId());
            assertNull(ctx.getChannel());
            assertTrue(ctx.getExtra().isEmpty());
        }
    }

    @Nested
    @DisplayName("SessionManager default lifecycle methods")
    class DefaultLifecycle {

        @Test
        @DisplayName("isSessionAlive defaults to true")
        void isAliveDefaultsTrue() {
            SessionManager manager = new SessionManager() {
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

            assertTrue(manager.isSessionAlive("any-session"));
        }
    }
}
