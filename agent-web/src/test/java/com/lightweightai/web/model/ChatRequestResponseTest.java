package com.lightweightai.web.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatRequest 和 ChatResponse 模型类测试。
 *
 * 这些是 web 模块的核心数据模型，在 ChatController/ChatService 等多处使用。
 * 覆盖所有 getter/setter 和工厂方法。
 */
@DisplayName("ChatRequest & ChatResponse 模型类")
class ChatRequestResponseTest {

    @Nested
    @DisplayName("ChatRequest")
    class ChatRequestTests {

        @Test
        @DisplayName("所有字段 getter/setter")
        void allFieldsGetSet() {
            ChatRequest req = new ChatRequest();
            req.setMessage("hello");
            req.setActiveSkills(List.of("coding", "writing"));
            req.setUseToolCalling(true);
            req.setOptions(Map.of("temperature", 0.5));
            req.setSessionId("sess-1");
            req.setSoulComfortMode(true);
            req.setModel("claude-3");
            req.setDebug(true);

            assertEquals("hello", req.getMessage());
            assertEquals(List.of("coding", "writing"), req.getActiveSkills());
            assertTrue(req.isUseToolCalling());
            assertEquals(0.5, req.getOptions().get("temperature"));
            assertEquals("sess-1", req.getSessionId());
            assertTrue(req.isSoulComfortMode());
            assertEquals("claude-3", req.getModel());
            assertTrue(req.isDebug());
        }

        @Test
        @DisplayName("默认 boolean 值为 false")
        void defaultBooleansFalse() {
            ChatRequest req = new ChatRequest();
            assertFalse(req.isUseToolCalling());
            assertFalse(req.isSoulComfortMode());
            assertFalse(req.isDebug());
        }

        @Test
        @DisplayName("默认引用类型为 null")
        void defaultReferencesNull() {
            ChatRequest req = new ChatRequest();
            assertNull(req.getMessage());
            assertNull(req.getActiveSkills());
            assertNull(req.getOptions());
            assertNull(req.getSessionId());
            assertNull(req.getModel());
        }
    }

    @Nested
    @DisplayName("ChatResponse")
    class ChatResponseTests {

        @Test
        @DisplayName("success 工厂方法")
        void successFactory() {
            ChatResponse resp = ChatResponse.success("hello world");
            assertEquals("hello world", resp.getResponse());
            assertNull(resp.getError());
        }

        @Test
        @DisplayName("error 工厂方法")
        void errorFactory() {
            ChatResponse resp = ChatResponse.error("something went wrong");
            assertNull(resp.getResponse());
            assertEquals("something went wrong", resp.getError());
        }

        @Test
        @DisplayName("所有字段 getter/setter")
        void allFieldsGetSet() {
            ChatResponse resp = new ChatResponse();
            resp.setResponse("answer");
            resp.setToolCallsUsed(List.of("search", "calc"));
            resp.setSkillsApplied(List.of("coding"));
            resp.setMetadata(Map.of("provider", "claude"));
            resp.setError(null);

            assertEquals("answer", resp.getResponse());
            assertEquals(2, resp.getToolCallsUsed().size());
            assertEquals(1, resp.getSkillsApplied().size());
            assertEquals("claude", resp.getMetadata().get("provider"));
            assertNull(resp.getError());
        }

        @Test
        @DisplayName("默认引用类型为 null")
        void defaultReferencesNull() {
            ChatResponse resp = new ChatResponse();
            assertNull(resp.getResponse());
            assertNull(resp.getToolCallsUsed());
            assertNull(resp.getSkillsApplied());
            assertNull(resp.getMetadata());
            assertNull(resp.getError());
            assertNull(resp.getDebug());
        }
    }
}
