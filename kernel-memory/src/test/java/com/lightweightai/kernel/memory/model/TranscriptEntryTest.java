package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TranscriptEntry - 会话记录条目")
class TranscriptEntryTest {

    @Test
    @DisplayName("userMessage 工厂方法")
    void userMessage() {
        TranscriptEntry entry = TranscriptEntry.userMessage("hello");

        assertEquals("message", entry.getType());
        assertEquals("user", entry.getRole());
        assertEquals("hello", entry.getContent());
        assertNotNull(entry.getTimestamp());
        assertNull(entry.getToolCalls());
        assertNull(entry.getToolResult());
    }

    @Test
    @DisplayName("assistantMessage 工厂方法")
    void assistantMessage() {
        TranscriptEntry entry = TranscriptEntry.assistantMessage("hi there");

        assertEquals("message", entry.getType());
        assertEquals("assistant", entry.getRole());
        assertEquals("hi there", entry.getContent());
    }

    @Test
    @DisplayName("assistantWithToolCalls 工厂方法")
    void assistantWithToolCalls() {
        var toolCall = new TranscriptEntry.ToolCallEntry(
                "call-1", "get_weather", Map.of("city", "Beijing"));
        TranscriptEntry entry = TranscriptEntry.assistantWithToolCalls(
                "Let me check", List.of(toolCall));

        assertEquals("assistant", entry.getRole());
        assertEquals("Let me check", entry.getContent());
        assertNotNull(entry.getToolCalls());
        assertEquals(1, entry.getToolCalls().size());
        assertEquals("get_weather", entry.getToolCalls().get(0).getName());
    }

    @Test
    @DisplayName("toolResult 工厂方法")
    void toolResultEntry() {
        var result = new TranscriptEntry.ToolResultEntry(
                "call-1", "get_weather", "sunny", false);
        TranscriptEntry entry = TranscriptEntry.toolResult(result);

        assertEquals("tool_result", entry.getType());
        assertNotNull(entry.getToolResult());
        assertEquals("get_weather", entry.getToolResult().getName());
        assertEquals("sunny", entry.getToolResult().getResult());
        assertFalse(entry.getToolResult().isError());
    }

    @Test
    @DisplayName("systemEvent 工厂方法")
    void systemEvent() {
        Map<String, Object> meta = Map.of("reason", "timeout");
        TranscriptEntry entry = TranscriptEntry.systemEvent("session ended", meta);

        assertEquals("system", entry.getType());
        assertEquals("system", entry.getRole());
        assertEquals("session ended", entry.getContent());
        assertEquals("timeout", entry.getMetadata().get("reason"));
    }

    @Test
    @DisplayName("ToolCallEntry 属性")
    void toolCallEntry() {
        var tce = new TranscriptEntry.ToolCallEntry("id1", "search", Map.of("q", "test"));

        assertEquals("id1", tce.getId());
        assertEquals("search", tce.getName());
        assertEquals("test", tce.getArguments().get("q"));
    }

    @Test
    @DisplayName("ToolResultEntry 错误标记")
    void toolResultEntryError() {
        var tre = new TranscriptEntry.ToolResultEntry("call-1", "tool1", "error msg", true);

        assertTrue(tre.isError());
        assertEquals("call-1", tre.getToolCallId());
    }

    @Test
    @DisplayName("null timestamp 默认为当前时间")
    void nullTimestampDefault() {
        Instant before = Instant.now();
        TranscriptEntry entry = new TranscriptEntry("message", null, "user", "hi",
                null, null, null);
        assertNotNull(entry.getTimestamp());
        assertFalse(entry.getTimestamp().isBefore(before));
    }
}
