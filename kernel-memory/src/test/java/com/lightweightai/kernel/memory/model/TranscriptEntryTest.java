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
    class FactoryTests {

        @Test
        @DisplayName("userMessage 创建用户消息条目")
        void userMessageFactory() {
            TranscriptEntry entry = TranscriptEntry.userMessage("你好");
            assertEquals("message", entry.getType());
            assertEquals("user", entry.getRole());
            assertEquals("你好", entry.getContent());
            assertNotNull(entry.getTimestamp());
            assertNull(entry.getToolCalls());
            assertNull(entry.getToolResult());
        }

        @Test
        @DisplayName("assistantMessage 创建助手消息条目")
        void assistantMessageFactory() {
            TranscriptEntry entry = TranscriptEntry.assistantMessage("你好，有什么可以帮助你的？");
            assertEquals("message", entry.getType());
            assertEquals("assistant", entry.getRole());
            assertEquals("你好，有什么可以帮助你的？", entry.getContent());
        }

        @Test
        @DisplayName("assistantWithToolCalls 携带工具调用列表")
        void assistantWithToolCalls() {
            TranscriptEntry.ToolCallEntry call = new TranscriptEntry.ToolCallEntry(
                    "call_1", "get_weather", Map.of("city", "Beijing"));

            TranscriptEntry entry = TranscriptEntry.assistantWithToolCalls(
                    "Let me check the weather", List.of(call));

            assertEquals("assistant", entry.getRole());
            assertNotNull(entry.getToolCalls());
            assertEquals(1, entry.getToolCalls().size());
            assertEquals("get_weather", entry.getToolCalls().get(0).getName());
            assertEquals("Beijing", entry.getToolCalls().get(0).getArguments().get("city"));
        }

        @Test
        @DisplayName("toolResult 创建工具结果条目")
        void toolResultFactory() {
            TranscriptEntry.ToolResultEntry result = new TranscriptEntry.ToolResultEntry(
                    "call_1", "get_weather", "Sunny, 25°C", false);

            TranscriptEntry entry = TranscriptEntry.toolResult(result);

            assertEquals("tool_result", entry.getType());
            assertNotNull(entry.getToolResult());
            assertEquals("call_1", entry.getToolResult().getToolCallId());
            assertEquals("get_weather", entry.getToolResult().getName());
            assertEquals("Sunny, 25°C", entry.getToolResult().getResult());
            assertFalse(entry.getToolResult().isError());
        }

        @Test
        @DisplayName("systemEvent 创建系统事件条目")
        void systemEventFactory() {
            TranscriptEntry entry = TranscriptEntry.systemEvent(
                    "Session started", Map.of("device", "mobile"));

            assertEquals("system", entry.getType());
            assertEquals("system", entry.getRole());
            assertEquals("Session started", entry.getContent());
            assertEquals("mobile", entry.getMetadata().get("device"));
        }
    }

    @Nested
    @DisplayName("ToolCallEntry")
    class ToolCallEntryTests {

        @Test
        @DisplayName("保存所有字段")
        void allFields() {
            TranscriptEntry.ToolCallEntry call = new TranscriptEntry.ToolCallEntry(
                    "id_1", "calculate", Map.of("expr", "2+2"));
            assertEquals("id_1", call.getId());
            assertEquals("calculate", call.getName());
            assertEquals("2+2", call.getArguments().get("expr"));
        }
    }

    @Nested
    @DisplayName("ToolResultEntry")
    class ToolResultEntryTests {

        @Test
        @DisplayName("错误结果的 isError 为 true")
        void errorResult() {
            TranscriptEntry.ToolResultEntry result = new TranscriptEntry.ToolResultEntry(
                    "call_1", "dangerous_tool", "Permission denied", true);
            assertTrue(result.isError());
            assertEquals("Permission denied", result.getResult());
        }
    }

    @Test
    @DisplayName("null timestamp 默认为当前时间")
    void nullTimestampDefaultsToNow() {
        Instant before = Instant.now();
        TranscriptEntry entry = new TranscriptEntry(
                "message", null, "user", "hello", null, null, null);
        assertNotNull(entry.getTimestamp());
        assertFalse(entry.getTimestamp().isBefore(before));
    }
}
