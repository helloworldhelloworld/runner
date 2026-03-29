package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TranscriptEntry - session transcript entries")
class TranscriptEntryTest {

    @Test
    void userMessage_factoryMethod() {
        TranscriptEntry entry = TranscriptEntry.userMessage("Hello");

        assertEquals("message", entry.getType());
        assertEquals("user", entry.getRole());
        assertEquals("Hello", entry.getContent());
        assertNotNull(entry.getTimestamp());
        assertNull(entry.getToolCalls());
        assertNull(entry.getToolResult());
    }

    @Test
    void assistantMessage_factoryMethod() {
        TranscriptEntry entry = TranscriptEntry.assistantMessage("Hi there");

        assertEquals("message", entry.getType());
        assertEquals("assistant", entry.getRole());
        assertEquals("Hi there", entry.getContent());
    }

    @Test
    void assistantWithToolCalls_factoryMethod() {
        var toolCall = new TranscriptEntry.ToolCallEntry(
            "call-1", "calculator", Map.of("expr", "2+2"));
        TranscriptEntry entry = TranscriptEntry.assistantWithToolCalls(
            "Let me calculate", List.of(toolCall));

        assertEquals("assistant", entry.getRole());
        assertNotNull(entry.getToolCalls());
        assertEquals(1, entry.getToolCalls().size());
        assertEquals("calculator", entry.getToolCalls().get(0).getName());
    }

    @Test
    void toolResult_factoryMethod() {
        var result = new TranscriptEntry.ToolResultEntry("call-1", "calculator", "4", false);
        TranscriptEntry entry = TranscriptEntry.toolResult(result);

        assertEquals("tool_result", entry.getType());
        assertNotNull(entry.getToolResult());
        assertEquals("calculator", entry.getToolResult().getName());
        assertEquals("4", entry.getToolResult().getResult());
        assertFalse(entry.getToolResult().isError());
    }

    @Test
    void systemEvent_factoryMethod() {
        Map<String, Object> meta = Map.of("event", "session_start");
        TranscriptEntry entry = TranscriptEntry.systemEvent("Session started", meta);

        assertEquals("system", entry.getType());
        assertEquals("system", entry.getRole());
        assertEquals("Session started", entry.getContent());
        assertEquals("session_start", entry.getMetadata().get("event"));
    }

    @Test
    void toolCallEntry_getters() {
        var call = new TranscriptEntry.ToolCallEntry("id-1", "weather", Map.of("city", "Beijing"));
        assertEquals("id-1", call.getId());
        assertEquals("weather", call.getName());
        assertEquals("Beijing", call.getArguments().get("city"));
    }

    @Test
    void toolResultEntry_errorFlag() {
        var result = new TranscriptEntry.ToolResultEntry("call-1", "tool", "error msg", true);
        assertTrue(result.isError());
        assertEquals("call-1", result.getToolCallId());
    }

    @Test
    void constructor_nullTimestamp_defaultsToNow() {
        TranscriptEntry entry = new TranscriptEntry("message", null, "user", "hi", null, null, null);
        assertNotNull(entry.getTimestamp());
    }
}
