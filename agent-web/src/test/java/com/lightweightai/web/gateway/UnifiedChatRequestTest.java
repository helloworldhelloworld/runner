package com.lightweightai.web.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UnifiedChatRequest - 统一聊天请求模型")
class UnifiedChatRequestTest {

    @Test
    @DisplayName("默认客户端类型为 UNKNOWN")
    void shouldDefaultToUnknownClientType() {
        UnifiedChatRequest request = new UnifiedChatRequest();
        assertEquals(UnifiedChatRequest.ClientType.UNKNOWN, request.getClientType());
    }

    @Test
    @DisplayName("设置客户端类型正确返回")
    void shouldReturnSetClientType() {
        UnifiedChatRequest request = new UnifiedChatRequest();
        request.setClientType(UnifiedChatRequest.ClientType.IOS);
        assertEquals(UnifiedChatRequest.ClientType.IOS, request.getClientType());
    }

    @Test
    @DisplayName("基本字段 getter/setter")
    void shouldSetAndGetBasicFields() {
        UnifiedChatRequest request = new UnifiedChatRequest();
        request.setSessionId("sess-1");
        request.setMessage("hello");
        request.setStream(true);
        request.setModel("claude-3");

        assertEquals("sess-1", request.getSessionId());
        assertEquals("hello", request.getMessage());
        assertTrue(request.isStream());
        assertEquals("claude-3", request.getModel());
    }

    @Test
    @DisplayName("扩展参数获取 - 存在的 key")
    void shouldReturnExtraValueForExistingKey() {
        UnifiedChatRequest request = new UnifiedChatRequest();
        Map<String, Object> extra = new HashMap<>();
        extra.put("theme", "dark");
        request.setExtra(extra);

        assertEquals("dark", request.getExtra("theme", "light"));
    }

    @Test
    @DisplayName("扩展参数获取 - 不存在的 key 返回默认值")
    void shouldReturnDefaultForMissingKey() {
        UnifiedChatRequest request = new UnifiedChatRequest();
        Map<String, Object> extra = new HashMap<>();
        request.setExtra(extra);

        assertEquals("light", request.getExtra("theme", "light"));
    }

    @Test
    @DisplayName("扩展参数获取 - extra 为 null 返回默认值")
    void shouldReturnDefaultWhenExtraIsNull() {
        UnifiedChatRequest request = new UnifiedChatRequest();
        assertEquals(42, (int) request.getExtra("count", 42));
    }

    @Test
    @DisplayName("toString 包含关键信息")
    void shouldContainKeyInfoInToString() {
        UnifiedChatRequest request = new UnifiedChatRequest();
        request.setSessionId("sess-x");
        request.setClientType(UnifiedChatRequest.ClientType.WEB);
        request.setStream(true);

        String str = request.toString();
        assertTrue(str.contains("sess-x"));
        assertTrue(str.contains("WEB"));
        assertTrue(str.contains("true"));
    }

    @Test
    @DisplayName("所有客户端类型枚举值存在")
    void shouldHaveAllClientTypes() {
        UnifiedChatRequest.ClientType[] types = UnifiedChatRequest.ClientType.values();
        assertEquals(7, types.length);
    }
}
