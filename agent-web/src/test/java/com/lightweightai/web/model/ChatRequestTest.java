package com.lightweightai.web.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChatRequest - 聊天请求模型")
class ChatRequestTest {

    @Test
    @DisplayName("所有字段 getter/setter")
    void shouldSetAndGetAllFields() {
        ChatRequest req = new ChatRequest();
        req.setMessage("你好");
        req.setActiveSkills(List.of("empathy", "crisis"));
        req.setUseToolCalling(true);
        req.setOptions(Map.of("temp", 0.7));
        req.setSessionId("sess-1");
        req.setSoulComfortMode(true);
        req.setModel("claude-3");
        req.setDebug(true);

        assertEquals("你好", req.getMessage());
        assertEquals(2, req.getActiveSkills().size());
        assertTrue(req.isUseToolCalling());
        assertEquals(0.7, req.getOptions().get("temp"));
        assertEquals("sess-1", req.getSessionId());
        assertTrue(req.isSoulComfortMode());
        assertEquals("claude-3", req.getModel());
        assertTrue(req.isDebug());
    }

    @Test
    @DisplayName("布尔字段默认为 false")
    void shouldDefaultBooleansToFalse() {
        ChatRequest req = new ChatRequest();
        assertFalse(req.isUseToolCalling());
        assertFalse(req.isSoulComfortMode());
        assertFalse(req.isDebug());
    }

    @Test
    @DisplayName("引用类型字段默认为 null")
    void shouldDefaultReferencesToNull() {
        ChatRequest req = new ChatRequest();
        assertNull(req.getMessage());
        assertNull(req.getActiveSkills());
        assertNull(req.getOptions());
        assertNull(req.getSessionId());
        assertNull(req.getModel());
    }
}
