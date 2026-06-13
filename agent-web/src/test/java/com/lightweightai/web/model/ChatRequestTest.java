package com.lightweightai.web.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChatRequest - 聊天请求模型")
class ChatRequestTest {

    @Test
    @DisplayName("所有字段 getter/setter 正常工作")
    void allFieldsWork() {
        ChatRequest req = new ChatRequest();
        req.setMessage("hello");
        req.setActiveSkills(List.of("weather", "math"));
        req.setUseToolCalling(true);
        req.setOptions(Map.of("temperature", 0.7));
        req.setSessionId("sess-1");
        req.setSoulComfortMode(true);
        req.setModel("claude-3-5-sonnet");
        req.setDebug(true);

        assertEquals("hello", req.getMessage());
        assertEquals(List.of("weather", "math"), req.getActiveSkills());
        assertTrue(req.isUseToolCalling());
        assertEquals(0.7, req.getOptions().get("temperature"));
        assertEquals("sess-1", req.getSessionId());
        assertTrue(req.isSoulComfortMode());
        assertEquals("claude-3-5-sonnet", req.getModel());
        assertTrue(req.isDebug());
    }

    @Test
    @DisplayName("默认布尔值为 false")
    void defaultBooleansFalse() {
        ChatRequest req = new ChatRequest();
        assertFalse(req.isUseToolCalling());
        assertFalse(req.isSoulComfortMode());
        assertFalse(req.isDebug());
    }

    @Test
    @DisplayName("默认引用类型为 null")
    void defaultRefsNull() {
        ChatRequest req = new ChatRequest();
        assertNull(req.getMessage());
        assertNull(req.getActiveSkills());
        assertNull(req.getOptions());
        assertNull(req.getSessionId());
        assertNull(req.getModel());
    }
}
