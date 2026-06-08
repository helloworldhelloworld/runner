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
        @DisplayName("assistantMessage 创建助手消息")
        void assistantMessage() {
            TranscriptEntry entry = TranscriptEntry.assistantMessage("hi there");

            assertEquals("message", entry.getType());
            assertEquals("assistant", entry.getRole());
            assertEquals("hi there", entry.getContent());
        }

        @Test
        @DisplayName("assistantWithToolCalls 创建含工具调用的助手消息")
        void assistantWithToolCalls() {
            var toolCall = new TranscriptEntry.ToolCallEntry(
                "call_1", "search", Map.of("q", "test"));

            TranscriptEntry entry = TranscriptEntry.assistantWithToolCalls(
                "Let me search", List.of(toolCall));

            assertEquals("assistant", entry.getRole());
            assertEquals(1, entry.getToolCalls().size());
            assertEquals("search", entry.getToolCalls().get(0).getName());
            assertEquals("call_1", entry.getToolCalls().get(0).getId());
            assertEquals("test", entry.getToolCalls().get(0).getArguments().get("q"));
        }

        @Test
        @DisplayName("toolResult 创建工具结果条目")
        void toolResult() {
            var result = new TranscriptEntry.ToolResultEntry(
                "call_1", "search", "found 3 results", false);

            TranscriptEntry entry = TranscriptEntry.toolResult(result);

            assertEquals("tool_result", entry.getType());
            assertNotNull(entry.getToolResult());
            assertEquals("call_1", entry.getToolResult().getToolCallId());
            assertEquals("search", entry.getToolResult().getName());
            assertEquals("found 3 results", entry.getToolResult().getResult());
            assertFalse(entry.getToolResult().isError());
        }

        @Test
        @DisplayName("systemEvent 创建系统事件")
        void systemEvent() {
            TranscriptEntry entry = TranscriptEntry.systemEvent(
                "session started", Map.of("version", "1.0"));

            assertEquals("system", entry.getType());
            assertEquals("system", entry.getRole());
            assertEquals("session started", entry.getContent());
            assertEquals("1.0", entry.getMetadata().get("version"));
        }
    }

    @Nested
    @DisplayName("ToolResultEntry 错误标志")
    class ToolResultError {

        @Test
        @DisplayName("isError 为 true 的工具结果")
        void errorToolResult() {
            var result = new TranscriptEntry.ToolResultEntry(
                "call_1", "exec", "command not found", true);

            assertTrue(result.isError());
        }
    }

    @Test
    @DisplayName("null timestamp 默认为当前时间")
    void nullTimestampDefaultsToNow() {
        TranscriptEntry entry = new TranscriptEntry(
            "message", null, "user", "hello", null, null, null);
        assertNotNull(entry.getTimestamp());
    }

    @Test
    @DisplayName("显式 timestamp 被保留")
    void explicitTimestampPreserved() {
        Instant fixed = Instant.parse("2024-01-01T00:00:00Z");
        TranscriptEntry entry = new TranscriptEntry(
            "message", fixed, "user", "hello", null, null, null);
        assertEquals(fixed, entry.getTimestamp());
    }
}
