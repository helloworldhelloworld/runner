package com.lightweightai.web.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UnifiedChatResponse - 统一聊天响应工厂方法")
class UnifiedChatResponseTest {

    @Test
    @DisplayName("full 工厂方法")
    void fullResponse() {
        UnifiedChatResponse resp = UnifiedChatResponse.full("r1", "s1", "Hello");

        assertEquals("r1", resp.getRequestId());
        assertEquals("s1", resp.getSessionId());
        assertEquals(UnifiedChatResponse.ResponseType.FULL, resp.getType());
        assertEquals("Hello", resp.getContent());
        assertFalse(resp.isError());
        assertNull(resp.getToolEvent());
    }

    @Test
    @DisplayName("delta 工厂方法")
    void deltaResponse() {
        UnifiedChatResponse resp = UnifiedChatResponse.delta("r1", "chunk");

        assertEquals("r1", resp.getRequestId());
        assertEquals(UnifiedChatResponse.ResponseType.DELTA, resp.getType());
        assertEquals("chunk", resp.getContent());
        assertNull(resp.getSessionId());
    }

    @Test
    @DisplayName("complete 工厂方法")
    void completeResponse() {
        UnifiedChatResponse resp = UnifiedChatResponse.complete("r1", "s1", "full text");

        assertEquals(UnifiedChatResponse.ResponseType.COMPLETE, resp.getType());
        assertEquals("full text", resp.getContent());
        assertEquals("s1", resp.getSessionId());
    }

    @Test
    @DisplayName("error 工厂方法")
    void errorResponse() {
        UnifiedChatResponse resp = UnifiedChatResponse.error("r1", "connection failed");

        assertEquals(UnifiedChatResponse.ResponseType.ERROR, resp.getType());
        assertTrue(resp.isError());
        assertEquals("connection failed", resp.getErrorMessage());
    }

    @Test
    @DisplayName("toolCallStart 工厂方法")
    void toolCallStartResponse() {
        UnifiedChatResponse resp = UnifiedChatResponse.toolCallStart("r1", "search", "call-1");

        assertEquals(UnifiedChatResponse.ResponseType.TOOL_CALL_START, resp.getType());
        assertNotNull(resp.getToolEvent());
        assertEquals("search", resp.getToolEvent().getToolName());
        assertEquals("call-1", resp.getToolEvent().getToolCallId());
    }

    @Test
    @DisplayName("toolProgress 工厂方法")
    void toolProgressResponse() {
        UnifiedChatResponse resp = UnifiedChatResponse.toolProgress("r1", "fetch", 50, 100, "downloading");

        assertEquals(UnifiedChatResponse.ResponseType.TOOL_PROGRESS, resp.getType());
        assertNotNull(resp.getToolEvent());
        assertEquals("fetch", resp.getToolEvent().getToolName());
        assertEquals(50.0, resp.getToolEvent().getProgress());
        assertEquals(100.0, resp.getToolEvent().getTotal());
        assertEquals("downloading", resp.getToolEvent().getMessage());
    }

    @Test
    @DisplayName("toolLog 工厂方法")
    void toolLogResponse() {
        UnifiedChatResponse resp = UnifiedChatResponse.toolLog("r1", "calc", "computing...");

        assertEquals(UnifiedChatResponse.ResponseType.TOOL_LOG, resp.getType());
        assertNotNull(resp.getToolEvent());
        assertEquals("calc", resp.getToolEvent().getToolName());
        assertEquals("computing...", resp.getToolEvent().getMessage());
    }

    @Test
    @DisplayName("toolResult 工厂方法")
    void toolResultResponse() {
        UnifiedChatResponse resp = UnifiedChatResponse.toolResult("r1", "search", "3 results found", false);

        assertEquals(UnifiedChatResponse.ResponseType.TOOL_RESULT, resp.getType());
        assertNotNull(resp.getToolEvent());
        assertEquals("search", resp.getToolEvent().getToolName());
        assertEquals("3 results found", resp.getToolEvent().getContent());
        assertFalse(resp.getToolEvent().getIsError());
    }

    @Test
    @DisplayName("toolResult 错误标记")
    void toolResultWithError() {
        UnifiedChatResponse resp = UnifiedChatResponse.toolResult("r1", "api", "timeout", true);

        assertTrue(resp.getToolEvent().getIsError());
    }

    @Test
    @DisplayName("toolError 工厂方法")
    void toolErrorResponse() {
        UnifiedChatResponse resp = UnifiedChatResponse.toolError("r1", "api", "connection refused");

        assertEquals(UnifiedChatResponse.ResponseType.TOOL_ERROR, resp.getType());
        assertNotNull(resp.getToolEvent());
        assertEquals("api", resp.getToolEvent().getToolName());
        assertEquals("connection refused", resp.getToolEvent().getMessage());
    }

    @Test
    @DisplayName("setter 可修改属性")
    void settersWork() {
        UnifiedChatResponse resp = UnifiedChatResponse.full("r1", "s1", "text");

        resp.setLatencyMs(150);
        resp.setSkillsApplied(List.of("soul-comfort"));
        resp.setMetadata(Map.of("model", "claude-sonnet"));

        assertEquals(150, resp.getLatencyMs());
        assertEquals(List.of("soul-comfort"), resp.getSkillsApplied());
        assertEquals("claude-sonnet", resp.getMetadata().get("model"));
    }

    @Test
    @DisplayName("toString 包含类型和请求ID")
    void toStringFormat() {
        UnifiedChatResponse resp = UnifiedChatResponse.delta("r123", "chunk");
        String str = resp.toString();

        assertTrue(str.contains("DELTA"));
        assertTrue(str.contains("r123"));
    }

    @Test
    @DisplayName("ResponseType 枚举完整")
    void allResponseTypesExist() {
        UnifiedChatResponse.ResponseType[] types = UnifiedChatResponse.ResponseType.values();
        assertEquals(9, types.length);
    }
}
