package com.lightweightai.web.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChatResponse - 聊天响应模型")
class ChatResponseTest {

    @Test
    @DisplayName("success 工厂方法设置 response")
    void successFactory() {
        ChatResponse resp = ChatResponse.success("hello");
        assertEquals("hello", resp.getResponse());
        assertNull(resp.getError());
    }

    @Test
    @DisplayName("error 工厂方法设置 error")
    void errorFactory() {
        ChatResponse resp = ChatResponse.error("something went wrong");
        assertEquals("something went wrong", resp.getError());
        assertNull(resp.getResponse());
    }

    @Test
    @DisplayName("所有字段 getter/setter 正常工作")
    void allFieldsWork() {
        ChatResponse resp = new ChatResponse();
        resp.setResponse("response text");
        resp.setToolCallsUsed(List.of("search"));
        resp.setSkillsApplied(List.of("weather"));
        resp.setMetadata(Map.of("key", "value"));
        resp.setError(null);

        assertEquals("response text", resp.getResponse());
        assertEquals(List.of("search"), resp.getToolCallsUsed());
        assertEquals(List.of("weather"), resp.getSkillsApplied());
        assertEquals("value", resp.getMetadata().get("key"));
        assertNull(resp.getError());
    }

    @Test
    @DisplayName("debug 字段可设置和获取")
    void debugFieldWorks() {
        ChatResponse resp = ChatResponse.success("ok");
        assertNull(resp.getDebug());

        resp.setDebug(new com.lightweightai.web.observer.DebugInfo());
        assertNotNull(resp.getDebug());
    }
}
