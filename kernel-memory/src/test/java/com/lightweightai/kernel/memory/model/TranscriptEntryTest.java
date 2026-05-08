package com.lightweightai.kernel.memory.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TranscriptEntry} — JSONL transcript entry for session audit trail.
 *
 * Covers: factory methods, JSON serialization round-trip, inner classes.
 */
@DisplayName("TranscriptEntry - 会话转录条目")
class TranscriptEntryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    // ==================== Factory Methods ====================

    @Nested
    @DisplayName("工厂方法")
    class FactoryMethods {

        @Test
        @DisplayName("userMessage — 创建用户消息")
        void userMessage_createsCorrectEntry() {
            TranscriptEntry entry = TranscriptEntry.userMessage("Hello");

            assertEquals("message", entry.getType());
            assertEquals("user", entry.getRole());
            assertEquals("Hello", entry.getContent());
            assertNotNull(entry.getTimestamp());
            assertNull(entry.getToolCalls());
            assertNull(entry.getToolResult());
        }

        @Test
        @DisplayName("assistantMessage — 创建助手消息")
        void assistantMessage_createsCorrectEntry() {
            TranscriptEntry entry = TranscriptEntry.assistantMessage("Hi there");

            assertEquals("message", entry.getType());
            assertEquals("assistant", entry.getRole());
            assertEquals("Hi there", entry.getContent());
        }

        @Test
        @DisplayName("assistantWithToolCalls — 包含工具调用")
        void assistantWithToolCalls_includesToolCalls() {
            TranscriptEntry.ToolCallEntry tc = new TranscriptEntry.ToolCallEntry(
                "call-1", "weather", Map.of("city", "Beijing")
            );
            TranscriptEntry entry = TranscriptEntry.assistantWithToolCalls("Let me check", List.of(tc));

            assertEquals("assistant", entry.getRole());
            assertEquals("Let me check", entry.getContent());
            assertNotNull(entry.getToolCalls());
            assertEquals(1, entry.getToolCalls().size());
            assertEquals("weather", entry.getToolCalls().get(0).getName());
        }

        @Test
        @DisplayName("toolResult — 工具结果条目")
        void toolResult_createsCorrectEntry() {
            TranscriptEntry.ToolResultEntry result = new TranscriptEntry.ToolResultEntry(
                "call-1", "weather", "Sunny, 25°C", false
            );
            TranscriptEntry entry = TranscriptEntry.toolResult(result);

            assertEquals("tool_result", entry.getType());
            assertNull(entry.getRole());
            assertNotNull(entry.getToolResult());
            assertEquals("weather", entry.getToolResult().getName());
            assertFalse(entry.getToolResult().isError());
        }

        @Test
        @DisplayName("systemEvent — 系统事件条目")
        void systemEvent_createsCorrectEntry() {
            Map<String, Object> meta = Map.of("reason", "compaction");
            TranscriptEntry entry = TranscriptEntry.systemEvent("Context compacted", meta);

            assertEquals("system", entry.getType());
            assertEquals("system", entry.getRole());
            assertEquals("Context compacted", entry.getContent());
            assertNotNull(entry.getMetadata());
            assertEquals("compaction", entry.getMetadata().get("reason"));
        }
    }

    // ==================== Timestamp ====================

    @Nested
    @DisplayName("时间戳处理")
    class TimestampHandling {

        @Test
        @DisplayName("构造时提供 timestamp — 使用提供的值")
        void constructor_withTimestamp_usesProvided() {
            Instant fixed = Instant.parse("2024-01-01T00:00:00Z");
            TranscriptEntry entry = new TranscriptEntry(
                "message", fixed, "user", "test", null, null, null
            );

            assertEquals(fixed, entry.getTimestamp());
        }

        @Test
        @DisplayName("构造时 null timestamp — 使用 Instant.now()")
        void constructor_nullTimestamp_usesNow() {
            Instant before = Instant.now();
            TranscriptEntry entry = new TranscriptEntry(
                "message", null, "user", "test", null, null, null
            );
            Instant after = Instant.now();

            assertFalse(entry.getTimestamp().isBefore(before));
            assertFalse(entry.getTimestamp().isAfter(after));
        }
    }

    // ==================== Inner Classes ====================

    @Nested
    @DisplayName("ToolCallEntry / ToolResultEntry")
    class InnerClasses {

        @Test
        @DisplayName("ToolCallEntry — 存储调用信息")
        void toolCallEntry_storesFields() {
            TranscriptEntry.ToolCallEntry tc = new TranscriptEntry.ToolCallEntry(
                "tc-1", "search", Map.of("query", "test")
            );

            assertEquals("tc-1", tc.getId());
            assertEquals("search", tc.getName());
            assertEquals("test", tc.getArguments().get("query"));
        }

        @Test
        @DisplayName("ToolResultEntry — error 标记")
        void toolResultEntry_errorFlag() {
            TranscriptEntry.ToolResultEntry success = new TranscriptEntry.ToolResultEntry(
                "tc-1", "tool", "ok", false
            );
            TranscriptEntry.ToolResultEntry error = new TranscriptEntry.ToolResultEntry(
                "tc-2", "tool", "failed", true
            );

            assertFalse(success.isError());
            assertTrue(error.isError());
        }
    }

    // ==================== JSON Round-Trip ====================

    @Test
    @DisplayName("JSON 序列化/反序列化往返")
    void jsonRoundTrip() throws Exception {
        TranscriptEntry original = TranscriptEntry.userMessage("Hello world");

        String json = MAPPER.writeValueAsString(original);
        TranscriptEntry deserialized = MAPPER.readValue(json, TranscriptEntry.class);

        assertEquals(original.getType(), deserialized.getType());
        assertEquals(original.getRole(), deserialized.getRole());
        assertEquals(original.getContent(), deserialized.getContent());
        assertNotNull(deserialized.getTimestamp());
    }
}
