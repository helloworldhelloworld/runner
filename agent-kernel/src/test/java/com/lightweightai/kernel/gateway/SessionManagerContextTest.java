package com.lightweightai.kernel.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionManager.SessionContext — construction and immutability")
class SessionManagerContextTest {

    @Test
    @DisplayName("full constructor preserves all fields")
    void fullConstructor() {
        Map<String, Object> extra = Map.of("theme", "dark", "lang", "zh");
        SessionManager.SessionContext ctx = new SessionManager.SessionContext(
                "user-123", "device-456", "wechat", extra);

        assertEquals("user-123", ctx.getUserId());
        assertEquals("device-456", ctx.getDeviceId());
        assertEquals("wechat", ctx.getChannel());
        assertEquals(Map.of("theme", "dark", "lang", "zh"), ctx.getExtra());
    }

    @Test
    @DisplayName("simple() factory only sets userId")
    void simpleFactory() {
        SessionManager.SessionContext ctx = SessionManager.SessionContext.simple("user-1");

        assertEquals("user-1", ctx.getUserId());
        assertNull(ctx.getDeviceId());
        assertNull(ctx.getChannel());
        assertTrue(ctx.getExtra().isEmpty());
    }

    @Test
    @DisplayName("null extra becomes empty map")
    void nullExtraBecomesEmpty() {
        SessionManager.SessionContext ctx = new SessionManager.SessionContext(
                "u", "d", "c", null);

        assertNotNull(ctx.getExtra());
        assertTrue(ctx.getExtra().isEmpty());
    }

    @Test
    @DisplayName("extra map is immutable")
    void extraMapIsImmutable() {
        SessionManager.SessionContext ctx = new SessionManager.SessionContext(
                "u", "d", "c", Map.of("k", "v"));

        assertThrows(UnsupportedOperationException.class,
                () -> ctx.getExtra().put("new", "val"));
    }

    @Test
    @DisplayName("default interface methods behave correctly")
    void defaultInterfaceMethods() {
        SessionManager manager = new SessionManager() {
            @Override public List<Map<String, String>> getSessionHistory(String sessionId) {
                return List.of();
            }
            @Override public Map<String, Object> getSessionSummary(String sessionId) {
                return Map.of();
            }
            @Override public void clearSession(String sessionId) {}
        };

        assertTrue(manager.isSessionAlive("any-session"));
        assertDoesNotThrow(() -> manager.onSessionStart("s1",
                SessionManager.SessionContext.simple("user")));
        assertDoesNotThrow(() -> manager.onSessionStop("s1"));
    }
}
