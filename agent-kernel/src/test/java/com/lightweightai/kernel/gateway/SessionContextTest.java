package com.lightweightai.kernel.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionManager.SessionContext — 会话启动上下文")
class SessionContextTest {

    @Test
    @DisplayName("构造器存储所有字段")
    void constructorStoresAllFields() {
        Map<String, Object> extra = Map.of("lang", "zh-CN");
        SessionManager.SessionContext ctx = new SessionManager.SessionContext(
                "user1", "device-abc", "wechat", extra);

        assertEquals("user1", ctx.getUserId());
        assertEquals("device-abc", ctx.getDeviceId());
        assertEquals("wechat", ctx.getChannel());
        assertEquals("zh-CN", ctx.getExtra().get("lang"));
    }

    @Test
    @DisplayName("simple 工厂方法仅设置 userId")
    void simpleFactoryMethod() {
        SessionManager.SessionContext ctx = SessionManager.SessionContext.simple("u1");

        assertEquals("u1", ctx.getUserId());
        assertNull(ctx.getDeviceId());
        assertNull(ctx.getChannel());
        assertTrue(ctx.getExtra().isEmpty());
    }

    @Test
    @DisplayName("extra 为 null 时默认空 Map")
    void nullExtraDefaultsToEmptyMap() {
        SessionManager.SessionContext ctx = new SessionManager.SessionContext(
                "u1", "d1", "ch", null);

        assertNotNull(ctx.getExtra());
        assertTrue(ctx.getExtra().isEmpty());
    }

    @Test
    @DisplayName("extra 是不可变拷贝")
    void extraIsImmutableCopy() {
        Map<String, Object> mutable = new HashMap<>();
        mutable.put("key", "value");
        SessionManager.SessionContext ctx = new SessionManager.SessionContext(
                "u1", "d1", "ch", mutable);

        mutable.put("added", "later");
        assertNull(ctx.getExtra().get("added"));

        assertThrows(UnsupportedOperationException.class,
                () -> ctx.getExtra().put("new", "val"));
    }
}
