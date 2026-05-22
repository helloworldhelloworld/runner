package com.lightweightai.web.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChatRequest - 聊天请求模型")
class ChatRequestTest {

    @Test
    @DisplayName("设置和获取所有字段")
    void setAndGetAllFields() {
        ChatRequest req = new ChatRequest();
        req.setMessage("你好");
        req.setActiveSkills(List.of("soul-comfort", "weather"));
        req.setUseToolCalling(true);
        req.setOptions(Map.of("temperature", 0.7));
        req.setSessionId("session-abc");
        req.setSoulComfortMode(true);
        req.setModel("anthropic/claude-3.5-sonnet");
        req.setDebug(true);

        assertEquals("你好", req.getMessage());
        assertEquals(List.of("soul-comfort", "weather"), req.getActiveSkills());
        assertTrue(req.isUseToolCalling());
        assertEquals(0.7, req.getOptions().get("temperature"));
        assertEquals("session-abc", req.getSessionId());
        assertTrue(req.isSoulComfortMode());
        assertEquals("anthropic/claude-3.5-sonnet", req.getModel());
        assertTrue(req.isDebug());
    }

    @Test
    @DisplayName("默认布尔值为 false")
    void defaultBooleansAreFalse() {
        ChatRequest req = new ChatRequest();
        assertFalse(req.isUseToolCalling());
        assertFalse(req.isSoulComfortMode());
        assertFalse(req.isDebug());
    }

    @Test
    @DisplayName("默认引用类型为 null")
    void defaultRefsAreNull() {
        ChatRequest req = new ChatRequest();
        assertNull(req.getMessage());
        assertNull(req.getActiveSkills());
        assertNull(req.getOptions());
        assertNull(req.getSessionId());
        assertNull(req.getModel());
    }
}
