package com.lightweightai.web.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UnifiedChatRequest - 统一聊天请求")
class UnifiedChatRequestTest {

    @Test
    @DisplayName("基本 getter/setter")
    void basicProperties() {
        UnifiedChatRequest req = new UnifiedChatRequest();
        req.setSessionId("s1");
        req.setMessage("Hello");
        req.setStream(true);
        req.setClientType(UnifiedChatRequest.ClientType.IOS);
        req.setModel("claude-sonnet");

        assertEquals("s1", req.getSessionId());
        assertEquals("Hello", req.getMessage());
        assertTrue(req.isStream());
        assertEquals(UnifiedChatRequest.ClientType.IOS, req.getClientType());
        assertEquals("claude-sonnet", req.getModel());
    }

    @Test
    @DisplayName("clientType 默认为 UNKNOWN")
    void defaultClientTypeIsUnknown() {
        UnifiedChatRequest req = new UnifiedChatRequest();
        assertEquals(UnifiedChatRequest.ClientType.UNKNOWN, req.getClientType());
    }

    @Test
    @DisplayName("ClientType 枚举完整")
    void allClientTypesExist() {
        UnifiedChatRequest.ClientType[] types = UnifiedChatRequest.ClientType.values();
        assertEquals(7, types.length);
        assertNotNull(UnifiedChatRequest.ClientType.valueOf("WEB"));
        assertNotNull(UnifiedChatRequest.ClientType.valueOf("IOS"));
        assertNotNull(UnifiedChatRequest.ClientType.valueOf("ANDROID"));
        assertNotNull(UnifiedChatRequest.ClientType.valueOf("HARMONYOS"));
        assertNotNull(UnifiedChatRequest.ClientType.valueOf("MINIPROGRAM"));
        assertNotNull(UnifiedChatRequest.ClientType.valueOf("CLI"));
        assertNotNull(UnifiedChatRequest.ClientType.valueOf("UNKNOWN"));
    }

    @Test
    @DisplayName("extra 参数存取")
    void extraParameters() {
        UnifiedChatRequest req = new UnifiedChatRequest();
        req.setExtra(Map.of("theme", "dark", "lang", "zh"));

        assertEquals(Map.of("theme", "dark", "lang", "zh"), req.getExtra());
    }

    @Test
    @DisplayName("getExtra(key, default) 类型化取值")
    void getExtraWithDefault() {
        UnifiedChatRequest req = new UnifiedChatRequest();
        req.setExtra(Map.of("count", 5));

        assertEquals(5, (int) req.getExtra("count", 0));
        assertEquals(0, (int) req.getExtra("missing", 0));
    }

    @Test
    @DisplayName("getExtra 无 extra 时返回默认值")
    void getExtraWhenNull() {
        UnifiedChatRequest req = new UnifiedChatRequest();
        assertEquals("default", req.getExtra("key", "default"));
    }

    @Test
    @DisplayName("toString 包含关键信息")
    void toStringFormat() {
        UnifiedChatRequest req = new UnifiedChatRequest();
        req.setSessionId("s1");
        req.setClientType(UnifiedChatRequest.ClientType.ANDROID);
        req.setStream(true);

        String str = req.toString();
        assertTrue(str.contains("s1"));
        assertTrue(str.contains("ANDROID"));
        assertTrue(str.contains("true"));
    }
}
