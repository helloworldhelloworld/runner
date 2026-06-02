package com.lightweightai.kernel.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionManager.SessionContext — construction and accessors")
class SessionManagerTest {

    @Nested
    @DisplayName("Full constructor")
    class FullConstructor {

        @Test
        @DisplayName("stores all fields correctly")
        void allFields() {
            Map<String, Object> extra = Map.of("key", "value", "count", 42);
            SessionManager.SessionContext ctx = new SessionManager.SessionContext(
                    "user-123", "device-abc", "web", extra);

            assertEquals("user-123", ctx.getUserId());
            assertEquals("device-abc", ctx.getDeviceId());
            assertEquals("web", ctx.getChannel());
            assertEquals(Map.of("key", "value", "count", 42), ctx.getExtra());
        }

        @Test
        @DisplayName("null extra becomes empty immutable map")
        void nullExtra() {
            SessionManager.SessionContext ctx = new SessionManager.SessionContext(
                    "u1", "d1", "ch", null);

            assertNotNull(ctx.getExtra());
            assertTrue(ctx.getExtra().isEmpty());
        }

        @Test
        @DisplayName("extra map is defensively copied (immutable)")
        void defensiveCopy() {
            var mutableExtra = new java.util.HashMap<String, Object>();
            mutableExtra.put("a", 1);
            SessionManager.SessionContext ctx = new SessionManager.SessionContext(
                    "u", "d", "c", mutableExtra);

            mutableExtra.put("b", 2);
            assertFalse(ctx.getExtra().containsKey("b"),
                    "mutation of original map must not affect context");
        }

        @Test
        @DisplayName("allows null userId, deviceId, channel")
        void nullableFields() {
            SessionManager.SessionContext ctx = new SessionManager.SessionContext(
                    null, null, null, null);

            assertNull(ctx.getUserId());
            assertNull(ctx.getDeviceId());
            assertNull(ctx.getChannel());
        }
    }

    @Nested
    @DisplayName("simple() factory")
    class SimpleFactory {

        @Test
        @DisplayName("creates context with only userId, rest null/empty")
        void simpleFactory() {
            SessionManager.SessionContext ctx = SessionManager.SessionContext.simple("alice");

            assertEquals("alice", ctx.getUserId());
            assertNull(ctx.getDeviceId());
            assertNull(ctx.getChannel());
            assertTrue(ctx.getExtra().isEmpty());
        }
    }

    @Nested
    @DisplayName("Default interface methods")
    class DefaultMethods {

        @Test
        @DisplayName("isSessionAlive defaults to true")
        void defaultAlive() {
            SessionManager manager = new NoOpSessionManager();
            assertTrue(manager.isSessionAlive("any-session"));
        }

        @Test
        @DisplayName("onSessionStart/onSessionStop have no-op defaults")
        void lifecycleDefaults() {
            SessionManager manager = new NoOpSessionManager();
            assertDoesNotThrow(() ->
                    manager.onSessionStart("s1", SessionManager.SessionContext.simple("u1")));
            assertDoesNotThrow(() -> manager.onSessionStop("s1"));
        }
    }

    // Minimal implementation for testing default methods
    private static class NoOpSessionManager implements SessionManager {
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
    }
}
