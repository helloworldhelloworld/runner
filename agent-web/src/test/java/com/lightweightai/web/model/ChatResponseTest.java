package com.lightweightai.web.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChatResponse - 聊天响应模型")
class ChatResponseTest {

    @Nested
    @DisplayName("工厂方法")
    class FactoryTests {

        @Test
        @DisplayName("success 创建成功响应")
        void successFactory() {
            ChatResponse resp = ChatResponse.success("你好！有什么可以帮你的？");
            assertEquals("你好！有什么可以帮你的？", resp.getResponse());
            assertNull(resp.getError());
        }

        @Test
        @DisplayName("error 创建错误响应")
        void errorFactory() {
            ChatResponse resp = ChatResponse.error("Service unavailable");
            assertEquals("Service unavailable", resp.getError());
            assertNull(resp.getResponse());
        }
    }

    @Test
    @DisplayName("设置和获取扩展字段")
    void setAndGetExtendedFields() {
        ChatResponse resp = new ChatResponse();
        resp.setResponse("Done");
        resp.setToolCallsUsed(List.of("get_weather", "calculate"));
        resp.setSkillsApplied(List.of("soul-comfort"));
        resp.setMetadata(Map.of("duration", 1200));

        assertEquals("Done", resp.getResponse());
        assertEquals(2, resp.getToolCallsUsed().size());
        assertTrue(resp.getToolCallsUsed().contains("get_weather"));
        assertEquals(List.of("soul-comfort"), resp.getSkillsApplied());
        assertEquals(1200, resp.getMetadata().get("duration"));
    }

    @Test
    @DisplayName("debug 字段可设可取")
    void debugField() {
        ChatResponse resp = new ChatResponse();
        assertNull(resp.getDebug());
        resp.setDebug(null);
        assertNull(resp.getDebug());
    }
}
