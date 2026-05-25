package com.lightweightai.web.observer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DebugInfo — 调试信息收集与截断")
class DebugInfoTest {

    @Test
    @DisplayName("duration 等于 endTime - startTime")
    void durationCalculation() {
        DebugInfo info = new DebugInfo();
        info.setStartTime(1000);
        info.setEndTime(3500);
        assertEquals(2500, info.getDuration());
    }

    @Test
    @DisplayName("addRequestMessage 追加到列表并正确设置 index/role/content")
    void addRequestMessage() {
        DebugInfo info = new DebugInfo();
        info.addRequestMessage(0, "SYSTEM", "prompt");
        info.addRequestMessage(1, "USER", "hello");

        assertEquals(2, info.getRequestMessages().size());
        assertEquals(0, info.getRequestMessages().get(0).getIndex());
        assertEquals("SYSTEM", info.getRequestMessages().get(0).getRole());
        assertEquals("prompt", info.getRequestMessages().get(0).getContent());
        assertEquals("USER", info.getRequestMessages().get(1).getRole());
    }

    @Test
    @DisplayName("MessageInfo 截断超过 500 字符的 content")
    void messageInfoTruncatesLongContent() {
        String longContent = "a".repeat(600);
        DebugInfo info = new DebugInfo();
        info.addRequestMessage(0, "USER", longContent);

        String content = info.getRequestMessages().get(0).getContent();
        assertTrue(content.length() < 600);
        assertTrue(content.endsWith("...[truncated]"));
    }

    @Test
    @DisplayName("MessageInfo 对 null content 返回 (null) 而非 NPE")
    void messageInfoNullContent() {
        DebugInfo info = new DebugInfo();
        info.addRequestMessage(0, "USER", null);
        assertEquals("(null)", info.getRequestMessages().get(0).getContent());
    }

    @Test
    @DisplayName("addToolCall 记录工具名、参数、结果和耗时")
    void addToolCall() {
        DebugInfo info = new DebugInfo();
        info.addToolCall("GetPoi", Map.of("query", "新街口"), "{\"id\":1}", 150);

        assertEquals(1, info.getToolCalls().size());
        DebugInfo.ToolCallInfo tc = info.getToolCalls().get(0);
        assertEquals("GetPoi", tc.getToolName());
        assertEquals(Map.of("query", "新街口"), tc.getArguments());
        assertEquals("{\"id\":1}", tc.getResult());
        assertEquals(150, tc.getDuration());
    }

    @Test
    @DisplayName("ToolCallInfo 截断超过 300 字符的 result")
    void toolCallInfoTruncatesLongResult() {
        String longResult = "b".repeat(400);
        DebugInfo info = new DebugInfo();
        info.addToolCall("Tool", Map.of(), longResult, 10);

        String result = info.getToolCalls().get(0).getResult();
        assertTrue(result.length() < 400);
        assertTrue(result.endsWith("...[truncated]"));
    }

    @Test
    @DisplayName("ToolCallInfo 对 null result 返回 (null)")
    void toolCallInfoNullResult() {
        DebugInfo info = new DebugInfo();
        info.addToolCall("Tool", Map.of(), null, 10);
        assertEquals("(null)", info.getToolCalls().get(0).getResult());
    }

    @Test
    @DisplayName("所有 setter/getter 往返正确")
    void settersAndGetters() {
        DebugInfo info = new DebugInfo();
        info.setSessionId("s1");
        info.setUserInput("hi");
        info.setResponseText("response");
        info.setStopReason("end_turn");
        info.setResponseTokens(42);
        info.setTotalRequestTokens(100);
        info.setSystemPromptPreview("preview");
        info.setError("err");

        assertEquals("s1", info.getSessionId());
        assertEquals("hi", info.getUserInput());
        assertEquals("response", info.getResponseText());
        assertEquals("end_turn", info.getStopReason());
        assertEquals(42, info.getResponseTokens());
        assertEquals(100, info.getTotalRequestTokens());
        assertEquals("preview", info.getSystemPromptPreview());
        assertEquals("err", info.getError());
    }
}
