package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TranscriptEntry - 会话记录条目")
class TranscriptEntryTest {

    @Nested
    @DisplayName("工厂方法")
    class FactoryMethods {

        @Test
        @DisplayName("userMessage 创建用户消息")
        void shouldCreateUserMessage() {
            TranscriptEntry entry = TranscriptEntry.userMessage("Hello");

            assertEquals("message", entry.getType());
            assertEquals("user", entry.getRole());
            assertEquals("Hello", entry.getContent());
            assertNotNull(entry.getTimestamp());
            assertNull(entry.getToolCalls());
            assertNull(entry.getToolResult());
            assertNull(entry.getMetadata());
        }

        @Test
        @DisplayName("assistantMessage 创建助手消息")
        void shouldCreateAssistantMessage() {
            TranscriptEntry entry = TranscriptEntry.assistantMessage("Hi there");

            assertEquals("message", entry.getType());
            assertEquals("assistant", entry.getRole());
            assertEquals("Hi there", entry.getContent());
        }

        @Test
        @DisplayName("assistantWithToolCalls 包含工具调用")
        void shouldCreateAssistantWithToolCalls() {
            TranscriptEntry.ToolCallEntry tc = new TranscriptEntry.ToolCallEntry(
                    "tc-1", "search", Map.of("query", "test"));

            TranscriptEntry entry = TranscriptEntry.assistantWithToolCalls(
                    "Let me search", List.of(tc));

            assertEquals("assistant", entry.getRole());
            assertEquals("Let me search", entry.getContent());
            assertEquals(1, entry.getToolCalls().size());
            assertEquals("search", entry.getToolCalls().get(0).getName());
            assertEquals("tc-1", entry.getToolCalls().get(0).getId());
            assertEquals("test", entry.getToolCalls().get(0).getArguments().get("query"));
        }

        @Test
        @DisplayName("toolResult 创建工具结果条目")
        void shouldCreateToolResult() {
            TranscriptEntry.ToolResultEntry tr = new TranscriptEntry.ToolResultEntry(
                    "tc-1", "search", "Found 5 results", false);

            TranscriptEntry entry = TranscriptEntry.toolResult(tr);

            assertEquals("tool_result", entry.getType());
            assertNull(entry.getRole());
            assertNotNull(entry.getToolResult());
            assertEquals("tc-1", entry.getToolResult().getToolCallId());
            assertEquals("search", entry.getToolResult().getName());
            assertEquals("Found 5 results", entry.getToolResult().getResult());
            assertFalse(entry.getToolResult().isError());
        }

        @Test
        @DisplayName("systemEvent 创建系统事件")
        void shouldCreateSystemEvent() {
            Map<String, Object> metadata = Map.of("reason", "crisis_detected");
            TranscriptEntry entry = TranscriptEntry.systemEvent("Safety alert", metadata);

            assertEquals("system", entry.getType());
            assertEquals("system", entry.getRole());
            assertEquals("Safety alert", entry.getContent());
            assertEquals("crisis_detected", entry.getMetadata().get("reason"));
        }
    }

    @Nested
    @DisplayName("时间戳处理")
    class TimestampHandling {

        @Test
        @DisplayName("null 时间戳默认为当前时间")
        void shouldDefaultTimestampToNow() {
            Instant before = Instant.now();
            TranscriptEntry entry = new TranscriptEntry(
                    "message", null, "user", "text", null, null, null);
            Instant after = Instant.now();

            assertNotNull(entry.getTimestamp());
            assertFalse(entry.getTimestamp().isBefore(before));
            assertFalse(entry.getTimestamp().isAfter(after));
        }

        @Test
        @DisplayName("指定时间戳被保留")
        void shouldPreserveSpecifiedTimestamp() {
            Instant ts = Instant.parse("2024-01-01T00:00:00Z");
            TranscriptEntry entry = new TranscriptEntry(
                    "message", ts, "user", "text", null, null, null);
            assertEquals(ts, entry.getTimestamp());
        }
    }

    @Nested
    @DisplayName("ToolResultEntry")
    class ToolResultEntryTests {

        @Test
        @DisplayName("错误状态工具结果")
        void shouldCreateErrorToolResult() {
            TranscriptEntry.ToolResultEntry tr = new TranscriptEntry.ToolResultEntry(
                    "tc-2", "api-call", "Timeout error", true);

            assertTrue(tr.isError());
            assertEquals("Timeout error", tr.getResult());
        }
    }
}
