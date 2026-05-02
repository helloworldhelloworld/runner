package com.lightweightai.web.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChatRequest - Request model getters/setters and defaults")
class ChatRequestTest {

    @Test
    @DisplayName("default values are null/false")
    void defaultValues() {
        ChatRequest req = new ChatRequest();

        assertNull(req.getMessage());
        assertNull(req.getActiveSkills());
        assertFalse(req.isUseToolCalling());
        assertNull(req.getOptions());
        assertNull(req.getSessionId());
        assertFalse(req.isSoulComfortMode());
        assertNull(req.getModel());
        assertFalse(req.isDebug());
    }

    @Test
    @DisplayName("all fields round-trip via getters/setters")
    void allFieldsRoundTrip() {
        ChatRequest req = new ChatRequest();
        req.setMessage("hello");
        req.setActiveSkills(List.of("weather"));
        req.setUseToolCalling(true);
        req.setOptions(Map.of("temp", 0.5));
        req.setSessionId("sess-1");
        req.setSoulComfortMode(true);
        req.setModel("anthropic/claude-3.5-sonnet");
        req.setDebug(true);

        assertEquals("hello", req.getMessage());
        assertEquals(List.of("weather"), req.getActiveSkills());
        assertTrue(req.isUseToolCalling());
        assertEquals(0.5, req.getOptions().get("temp"));
        assertEquals("sess-1", req.getSessionId());
        assertTrue(req.isSoulComfortMode());
        assertEquals("anthropic/claude-3.5-sonnet", req.getModel());
        assertTrue(req.isDebug());
    }
}
