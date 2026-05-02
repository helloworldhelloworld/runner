package com.lightweightai.web.model;

import com.lightweightai.web.observer.DebugInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChatResponse - Response model factories and fields")
class ChatResponseTest {

    @Test
    @DisplayName("success factory sets response field")
    void successFactory() {
        ChatResponse resp = ChatResponse.success("hello");

        assertEquals("hello", resp.getResponse());
        assertNull(resp.getError());
    }

    @Test
    @DisplayName("error factory sets error field")
    void errorFactory() {
        ChatResponse resp = ChatResponse.error("something failed");

        assertEquals("something failed", resp.getError());
        assertNull(resp.getResponse());
    }

    @Test
    @DisplayName("all fields round-trip via getters/setters")
    void allFieldsRoundTrip() {
        ChatResponse resp = new ChatResponse();
        resp.setResponse("response text");
        resp.setToolCallsUsed(List.of("calc", "weather"));
        resp.setSkillsApplied(List.of("math"));
        resp.setMetadata(Map.of("key", "val"));
        resp.setError("err");
        resp.setDebug(new DebugInfo());

        assertEquals("response text", resp.getResponse());
        assertEquals(List.of("calc", "weather"), resp.getToolCallsUsed());
        assertEquals(List.of("math"), resp.getSkillsApplied());
        assertEquals("val", resp.getMetadata().get("key"));
        assertEquals("err", resp.getError());
        assertNotNull(resp.getDebug());
    }
}
