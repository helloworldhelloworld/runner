package com.lightweightai.web.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChatResponse - 聊天响应模型")
class ChatResponseTest {

    @Test
    @DisplayName("success() 工厂方法设置响应文本")
    void shouldCreateSuccessResponse() {
        ChatResponse resp = ChatResponse.success("你好！");
        assertEquals("你好！", resp.getResponse());
        assertNull(resp.getError());
    }

    @Test
    @DisplayName("error() 工厂方法设置错误信息")
    void shouldCreateErrorResponse() {
        ChatResponse resp = ChatResponse.error("API 超时");
        assertEquals("API 超时", resp.getError());
        assertNull(resp.getResponse());
    }

    @Test
    @DisplayName("所有字段 getter/setter")
    void shouldSetAndGetAllFields() {
        ChatResponse resp = new ChatResponse();
        resp.setResponse("test");
        resp.setToolCallsUsed(List.of("search", "calc"));
        resp.setSkillsApplied(List.of("empathy"));
        resp.setMetadata(Map.of("latency", 100));
        resp.setError("err");

        assertEquals("test", resp.getResponse());
        assertEquals(2, resp.getToolCallsUsed().size());
        assertEquals(1, resp.getSkillsApplied().size());
        assertEquals(100, resp.getMetadata().get("latency"));
        assertEquals("err", resp.getError());
    }

    @Test
    @DisplayName("默认值均为 null")
    void shouldHaveNullDefaults() {
        ChatResponse resp = new ChatResponse();
        assertNull(resp.getResponse());
        assertNull(resp.getToolCallsUsed());
        assertNull(resp.getSkillsApplied());
        assertNull(resp.getMetadata());
        assertNull(resp.getError());
        assertNull(resp.getDebug());
    }
}
