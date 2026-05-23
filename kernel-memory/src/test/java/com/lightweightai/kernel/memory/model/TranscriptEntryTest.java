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
        @DisplayName("userMessage 创建用户消息条目")
        void userMessage() {
            TranscriptEntry entry = TranscriptEntry.userMessage("你好");

            assertEquals("message", entry.getType());
            assertEquals("user", entry.getRole());
            assertEquals("你好", entry.getContent());
            assertNotNull(entry.getTimestamp());
            assertNull(entry.getToolCalls());
            assertNull(entry.getToolResult());
            assertNull(entry.getMetadata());
        }

        @Test
        @DisplayName("assistantMessage 创建助手消息条目")
        void assistantMessage() {
            TranscriptEntry entry = TranscriptEntry.assistantMessage("你好！有什么可以帮助你的？");

            assertEquals("message", entry.getType());
            assertEquals("assistant", entry.getRole());
            assertEquals("你好！有什么可以帮助你的？", entry.getContent());
        }

        @Test
        @DisplayName("assistantWithToolCalls 包含工具调用记录")
        void assistantWithToolCalls() {
            List<TranscriptEntry.ToolCallEntry> calls = List.of(
                    new TranscriptEntry.ToolCallEntry("call-1", "get_weather",
                            Map.of("city", "北京")),
                    new TranscriptEntry.ToolCallEntry("call-2", "get_time",
                            Map.of("timezone", "Asia/Shanghai"))
            );

            TranscriptEntry entry = TranscriptEntry.assistantWithToolCalls(
                    "让我查一下", calls);

            assertEquals("assistant", entry.getRole());
            assertEquals("让我查一下", entry.getContent());
            assertEquals(2, entry.getToolCalls().size());
            assertEquals("get_weather", entry.getToolCalls().get(0).getName());
            assertEquals("call-1", entry.getToolCalls().get(0).getId());
            assertEquals("北京", entry.getToolCalls().get(0).getArguments().get("city"));
        }

        @Test
        @DisplayName("toolResult 创建工具结果条目")
        void toolResult() {
            TranscriptEntry.ToolResultEntry resultEntry =
                    new TranscriptEntry.ToolResultEntry("call-1", "get_weather", "晴天 25°C", false);

            TranscriptEntry entry = TranscriptEntry.toolResult(resultEntry);

            assertEquals("tool_result", entry.getType());
            assertNull(entry.getRole());
            assertNull(entry.getContent());
            assertNotNull(entry.getToolResult());
            assertEquals("call-1", entry.getToolResult().getToolCallId());
            assertEquals("get_weather", entry.getToolResult().getName());
            assertEquals("晴天 25°C", entry.getToolResult().getResult());
            assertFalse(entry.getToolResult().isError());
        }

        @Test
        @DisplayName("toolResult 错误状态")
        void toolResultWithError() {
            TranscriptEntry.ToolResultEntry resultEntry =
                    new TranscriptEntry.ToolResultEntry("call-2", "api_call", "connection refused", true);

            TranscriptEntry entry = TranscriptEntry.toolResult(resultEntry);

            assertTrue(entry.getToolResult().isError());
        }

        @Test
        @DisplayName("systemEvent 创建系统事件条目")
        void systemEvent() {
            Map<String, Object> meta = Map.of("reason", "timeout");
            TranscriptEntry entry = TranscriptEntry.systemEvent("会话超时", meta);

            assertEquals("system", entry.getType());
            assertEquals("system", entry.getRole());
            assertEquals("会话超时", entry.getContent());
            assertEquals("timeout", entry.getMetadata().get("reason"));
        }
    }

    @Nested
    @DisplayName("Timestamp 处理")
    class TimestampTests {

        @Test
        @DisplayName("指定 timestamp 时使用指定值")
        void explicitTimestamp() {
            Instant fixed = Instant.parse("2024-01-01T00:00:00Z");
            TranscriptEntry entry = new TranscriptEntry(
                    "message", fixed, "user", "hello", null, null, null);

            assertEquals(fixed, entry.getTimestamp());
        }

        @Test
        @DisplayName("timestamp 为 null 时使用当前时间")
        void defaultTimestamp() {
            Instant before = Instant.now();
            TranscriptEntry entry = new TranscriptEntry(
                    "message", null, "user", "hello", null, null, null);
            Instant after = Instant.now();

            assertNotNull(entry.getTimestamp());
            assertFalse(entry.getTimestamp().isBefore(before));
            assertFalse(entry.getTimestamp().isAfter(after));
        }
    }

    @Nested
    @DisplayName("ToolCallEntry")
    class ToolCallEntryTests {

        @Test
        @DisplayName("完整构造所有字段")
        void fullConstruction() {
            Map<String, Object> args = Map.of("query", "test", "limit", 10);
            TranscriptEntry.ToolCallEntry call =
                    new TranscriptEntry.ToolCallEntry("tc-1", "search", args);

            assertEquals("tc-1", call.getId());
            assertEquals("search", call.getName());
            assertEquals("test", call.getArguments().get("query"));
            assertEquals(10, call.getArguments().get("limit"));
        }
    }

    @Nested
    @DisplayName("ToolResultEntry")
    class ToolResultEntryTests {

        @Test
        @DisplayName("成功结果")
        void successResult() {
            TranscriptEntry.ToolResultEntry result =
                    new TranscriptEntry.ToolResultEntry("tc-1", "calc", 42, false);

            assertEquals("tc-1", result.getToolCallId());
            assertEquals("calc", result.getName());
            assertEquals(42, result.getResult());
            assertFalse(result.isError());
        }

        @Test
        @DisplayName("错误结果")
        void errorResult() {
            TranscriptEntry.ToolResultEntry result =
                    new TranscriptEntry.ToolResultEntry("tc-2", "api", "500 error", true);

            assertTrue(result.isError());
            assertEquals("500 error", result.getResult());
        }
    }
}
