package com.lightweightai.web.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UnifiedChatResponse - 统一聊天响应模型")
class UnifiedChatResponseTest {

    @Nested
    @DisplayName("工厂方法")
    class FactoryMethods {

        @Test
        @DisplayName("full() 创建完整响应")
        void shouldCreateFullResponse() {
            UnifiedChatResponse resp = UnifiedChatResponse.full("req-1", "sess-1", "Hello!");

            assertEquals("req-1", resp.getRequestId());
            assertEquals("sess-1", resp.getSessionId());
            assertEquals(UnifiedChatResponse.ResponseType.FULL, resp.getType());
            assertEquals("Hello!", resp.getContent());
            assertFalse(resp.isError());
        }

        @Test
        @DisplayName("delta() 创建流式增量")
        void shouldCreateDeltaResponse() {
            UnifiedChatResponse resp = UnifiedChatResponse.delta("req-2", "He");

            assertEquals("req-2", resp.getRequestId());
            assertEquals(UnifiedChatResponse.ResponseType.DELTA, resp.getType());
            assertEquals("He", resp.getContent());
        }

        @Test
        @DisplayName("complete() 创建流式完成")
        void shouldCreateCompleteResponse() {
            UnifiedChatResponse resp = UnifiedChatResponse.complete("req-3", "sess-3", "Full text");

            assertEquals(UnifiedChatResponse.ResponseType.COMPLETE, resp.getType());
            assertEquals("sess-3", resp.getSessionId());
            assertEquals("Full text", resp.getContent());
        }

        @Test
        @DisplayName("error() 创建错误响应")
        void shouldCreateErrorResponse() {
            UnifiedChatResponse resp = UnifiedChatResponse.error("req-4", "Something broke");

            assertEquals(UnifiedChatResponse.ResponseType.ERROR, resp.getType());
            assertTrue(resp.isError());
            assertEquals("Something broke", resp.getErrorMessage());
        }

        @Test
        @DisplayName("toolCallStart() 创建工具调用开始事件")
        void shouldCreateToolCallStartEvent() {
            UnifiedChatResponse resp = UnifiedChatResponse.toolCallStart("req-5", "search", "tc-1");

            assertEquals(UnifiedChatResponse.ResponseType.TOOL_CALL_START, resp.getType());
            assertNotNull(resp.getToolEvent());
            assertEquals("search", resp.getToolEvent().getToolName());
            assertEquals("tc-1", resp.getToolEvent().getToolCallId());
        }

        @Test
        @DisplayName("toolProgress() 创建工具进度事件")
        void shouldCreateToolProgressEvent() {
            UnifiedChatResponse resp = UnifiedChatResponse.toolProgress("req-6", "download", 50.0, 100.0, "Halfway");

            assertEquals(UnifiedChatResponse.ResponseType.TOOL_PROGRESS, resp.getType());
            assertEquals("download", resp.getToolEvent().getToolName());
            assertEquals(50.0, resp.getToolEvent().getProgress());
            assertEquals(100.0, resp.getToolEvent().getTotal());
            assertEquals("Halfway", resp.getToolEvent().getMessage());
        }

        @Test
        @DisplayName("toolLog() 创建工具日志事件")
        void shouldCreateToolLogEvent() {
            UnifiedChatResponse resp = UnifiedChatResponse.toolLog("req-7", "compile", "Building...");

            assertEquals(UnifiedChatResponse.ResponseType.TOOL_LOG, resp.getType());
            assertEquals("compile", resp.getToolEvent().getToolName());
            assertEquals("Building...", resp.getToolEvent().getMessage());
        }

        @Test
        @DisplayName("toolResult() 创建工具结果事件")
        void shouldCreateToolResultEvent() {
            UnifiedChatResponse resp = UnifiedChatResponse.toolResult("req-8", "search", "Found 5 results", false);

            assertEquals(UnifiedChatResponse.ResponseType.TOOL_RESULT, resp.getType());
            assertEquals("search", resp.getToolEvent().getToolName());
            assertEquals("Found 5 results", resp.getToolEvent().getContent());
            assertEquals(false, resp.getToolEvent().getIsError());
        }

        @Test
        @DisplayName("toolError() 创建工具错误事件")
        void shouldCreateToolErrorEvent() {
            UnifiedChatResponse resp = UnifiedChatResponse.toolError("req-9", "api-call", "Timeout");

            assertEquals(UnifiedChatResponse.ResponseType.TOOL_ERROR, resp.getType());
            assertEquals("api-call", resp.getToolEvent().getToolName());
            assertEquals("Timeout", resp.getToolEvent().getMessage());
        }
    }

    @Test
    @DisplayName("所有响应类型枚举值存在")
    void shouldHaveAllResponseTypes() {
        UnifiedChatResponse.ResponseType[] types = UnifiedChatResponse.ResponseType.values();
        assertEquals(9, types.length);
    }

    @Test
    @DisplayName("ToolEvent getter/setter 完整性")
    void shouldSetAndGetToolEventFields() {
        UnifiedChatResponse.ToolEvent te = new UnifiedChatResponse.ToolEvent();
        te.setToolName("tool-x");
        te.setToolCallId("call-1");
        te.setProgress(75.0);
        te.setTotal(100.0);
        te.setMessage("Processing");
        te.setContent("result data");
        te.setIsError(true);

        assertEquals("tool-x", te.getToolName());
        assertEquals("call-1", te.getToolCallId());
        assertEquals(75.0, te.getProgress());
        assertEquals(100.0, te.getTotal());
        assertEquals("Processing", te.getMessage());
        assertEquals("result data", te.getContent());
        assertTrue(te.getIsError());
    }

    @Test
    @DisplayName("toString 包含类型和请求ID")
    void shouldContainTypeAndRequestIdInToString() {
        UnifiedChatResponse resp = UnifiedChatResponse.full("req-10", "sess-10", "test");
        String str = resp.toString();
        assertTrue(str.contains("FULL"));
        assertTrue(str.contains("req-10"));
    }

    @Test
    @DisplayName("setter 覆盖工厂方法值")
    void shouldAllowOverridingFactoryValues() {
        UnifiedChatResponse resp = UnifiedChatResponse.full("req-1", "sess-1", "Hello");
        resp.setLatencyMs(150);
        resp.setMetadata(java.util.Map.of("key", "val"));
        resp.setSkillsApplied(java.util.List.of("empathy"));

        assertEquals(150, resp.getLatencyMs());
        assertEquals("val", resp.getMetadata().get("key"));
        assertEquals(1, resp.getSkillsApplied().size());
    }
}
