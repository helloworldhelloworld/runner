package com.lightweightai.web.observer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DebugInfo - Debug data structure")
class DebugInfoTest {

    private DebugInfo debugInfo;

    @BeforeEach
    void setUp() {
        debugInfo = new DebugInfo();
    }

    @Test
    @DisplayName("duration is endTime minus startTime")
    void durationCalculation() {
        debugInfo.setStartTime(1000L);
        debugInfo.setEndTime(1500L);
        assertEquals(500L, debugInfo.getDuration());
    }

    @Test
    @DisplayName("addRequestMessage appends to list")
    void addRequestMessage() {
        debugInfo.addRequestMessage(0, "USER", "hi");
        debugInfo.addRequestMessage(1, "ASSISTANT", "hello");

        assertEquals(2, debugInfo.getRequestMessages().size());
        assertEquals(0, debugInfo.getRequestMessages().get(0).getIndex());
        assertEquals("USER", debugInfo.getRequestMessages().get(0).getRole());
    }

    @Test
    @DisplayName("addToolCall appends to list")
    void addToolCall() {
        debugInfo.addToolCall("calc", Map.of("x", 1), "42", 100L);
        debugInfo.addToolCall("time", Map.of(), "12:00", 50L);

        assertEquals(2, debugInfo.getToolCalls().size());
        assertEquals("calc", debugInfo.getToolCalls().get(0).getToolName());
        assertEquals(100L, debugInfo.getToolCalls().get(0).getDuration());
    }

    @Test
    @DisplayName("MessageInfo truncates content longer than 500 chars")
    void messageInfoTruncation() {
        String longContent = "x".repeat(600);
        DebugInfo.MessageInfo info = new DebugInfo.MessageInfo(0, "USER", longContent);

        assertTrue(info.getContent().length() < 600);
        assertTrue(info.getContent().endsWith("[truncated]"));
    }

    @Test
    @DisplayName("MessageInfo handles null content")
    void messageInfoNullContent() {
        DebugInfo.MessageInfo info = new DebugInfo.MessageInfo(0, "USER", null);
        assertEquals("(null)", info.getContent());
    }

    @Test
    @DisplayName("ToolCallInfo truncates result longer than 300 chars")
    void toolCallInfoTruncation() {
        String longResult = "y".repeat(400);
        DebugInfo.ToolCallInfo info = new DebugInfo.ToolCallInfo("tool", Map.of(), longResult, 10L);

        assertTrue(info.getResult().length() < 400);
        assertTrue(info.getResult().endsWith("[truncated]"));
    }

    @Test
    @DisplayName("ToolCallInfo handles null result")
    void toolCallInfoNullResult() {
        DebugInfo.ToolCallInfo info = new DebugInfo.ToolCallInfo("tool", Map.of(), null, 10L);
        assertEquals("(null)", info.getResult());
    }

    @Test
    @DisplayName("all getters/setters work")
    void gettersSetters() {
        debugInfo.setSessionId("s-1");
        debugInfo.setUserInput("hi");
        debugInfo.setResponseText("hello");
        debugInfo.setStopReason("end_turn");
        debugInfo.setResponseTokens(50);
        debugInfo.setTotalRequestTokens(100);
        debugInfo.setSystemPromptPreview("prompt");
        debugInfo.setError("err");

        assertEquals("s-1", debugInfo.getSessionId());
        assertEquals("hi", debugInfo.getUserInput());
        assertEquals("hello", debugInfo.getResponseText());
        assertEquals("end_turn", debugInfo.getStopReason());
        assertEquals(50, debugInfo.getResponseTokens());
        assertEquals(100, debugInfo.getTotalRequestTokens());
        assertEquals("prompt", debugInfo.getSystemPromptPreview());
        assertEquals("err", debugInfo.getError());
    }
}
