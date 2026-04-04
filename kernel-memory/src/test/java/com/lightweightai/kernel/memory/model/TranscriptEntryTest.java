package com.lightweightai.kernel.memory.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TranscriptEntry")
class TranscriptEntryTest {

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethodTests {

        @Test
        void shouldCreateUserMessage() {
            TranscriptEntry entry = TranscriptEntry.userMessage("Hello");

            assertEquals("message", entry.getType());
            assertEquals("user", entry.getRole());
            assertEquals("Hello", entry.getContent());
            assertNotNull(entry.getTimestamp());
            assertNull(entry.getToolCalls());
            assertNull(entry.getToolResult());
        }

        @Test
        void shouldCreateAssistantMessage() {
            TranscriptEntry entry = TranscriptEntry.assistantMessage("Hi there");

            assertEquals("message", entry.getType());
            assertEquals("assistant", entry.getRole());
            assertEquals("Hi there", entry.getContent());
        }

        @Test
        void shouldCreateAssistantWithToolCalls() {
            TranscriptEntry.ToolCallEntry toolCall = new TranscriptEntry.ToolCallEntry(
                    "call-1", "web_search", Map.of("query", "weather"));

            TranscriptEntry entry = TranscriptEntry.assistantWithToolCalls(
                    "Let me search", List.of(toolCall));

            assertEquals("message", entry.getType());
            assertEquals("assistant", entry.getRole());
            assertEquals("Let me search", entry.getContent());
            assertNotNull(entry.getToolCalls());
            assertEquals(1, entry.getToolCalls().size());
            assertEquals("web_search", entry.getToolCalls().get(0).getName());
        }

        @Test
        void shouldCreateToolResult() {
            TranscriptEntry.ToolResultEntry result = new TranscriptEntry.ToolResultEntry(
                    "call-1", "web_search", "Sunny 25°C", false);

            TranscriptEntry entry = TranscriptEntry.toolResult(result);

            assertEquals("tool_result", entry.getType());
            assertNull(entry.getRole());
            assertNull(entry.getContent());
            assertNotNull(entry.getToolResult());
            assertEquals("web_search", entry.getToolResult().getName());
            assertFalse(entry.getToolResult().isError());
        }

        @Test
        void shouldCreateSystemEvent() {
            TranscriptEntry entry = TranscriptEntry.systemEvent(
                    "Session started", Map.of("sessionId", "s1"));

            assertEquals("system", entry.getType());
            assertEquals("system", entry.getRole());
            assertEquals("Session started", entry.getContent());
            assertNotNull(entry.getMetadata());
            assertEquals("s1", entry.getMetadata().get("sessionId"));
        }
    }

    @Nested
    @DisplayName("ToolCallEntry")
    class ToolCallEntryTests {

        @Test
        void shouldCreateWithAllFields() {
            TranscriptEntry.ToolCallEntry entry = new TranscriptEntry.ToolCallEntry(
                    "tc-1", "calculate", Map.of("expr", "2+2"));

            assertEquals("tc-1", entry.getId());
            assertEquals("calculate", entry.getName());
            assertEquals("2+2", entry.getArguments().get("expr"));
        }

        @Test
        void shouldHandleNullArguments() {
            TranscriptEntry.ToolCallEntry entry = new TranscriptEntry.ToolCallEntry(
                    "tc-2", "get_time", null);

            assertEquals("tc-2", entry.getId());
            assertNull(entry.getArguments());
        }
    }

    @Nested
    @DisplayName("ToolResultEntry")
    class ToolResultEntryTests {

        @Test
        void shouldCreateSuccessResult() {
            TranscriptEntry.ToolResultEntry entry = new TranscriptEntry.ToolResultEntry(
                    "tc-1", "calculate", "4", false);

            assertEquals("tc-1", entry.getToolCallId());
            assertEquals("calculate", entry.getName());
            assertEquals("4", entry.getResult());
            assertFalse(entry.isError());
        }

        @Test
        void shouldCreateErrorResult() {
            TranscriptEntry.ToolResultEntry entry = new TranscriptEntry.ToolResultEntry(
                    "tc-2", "web_search", "Timeout", true);

            assertTrue(entry.isError());
            assertEquals("Timeout", entry.getResult());
        }
    }

    @Nested
    @DisplayName("Timestamp Handling")
    class TimestampTests {

        @Test
        void shouldDefaultTimestampToNow() {
            Instant before = Instant.now();
            TranscriptEntry entry = TranscriptEntry.userMessage("test");
            Instant after = Instant.now();

            assertFalse(entry.getTimestamp().isBefore(before));
            assertFalse(entry.getTimestamp().isAfter(after));
        }

        @Test
        void shouldPreserveExplicitTimestamp() {
            Instant fixed = Instant.parse("2025-01-15T10:30:00Z");
            TranscriptEntry entry = new TranscriptEntry(
                    "message", fixed, "user", "hello", null, null, null);

            assertEquals(fixed, entry.getTimestamp());
        }

        @Test
        void shouldDefaultNullTimestampToNow() {
            TranscriptEntry entry = new TranscriptEntry(
                    "message", null, "user", "hello", null, null, null);

            assertNotNull(entry.getTimestamp());
        }
    }

    @Nested
    @DisplayName("JSON Serialization")
    class JsonTests {

        private ObjectMapper mapper;

        @BeforeEach
        void setUp() {
            mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
        }

        @Test
        void shouldSerializeAndDeserializeUserMessage() throws Exception {
            TranscriptEntry original = TranscriptEntry.userMessage("Hello world");

            String json = mapper.writeValueAsString(original);
            TranscriptEntry deserialized = mapper.readValue(json, TranscriptEntry.class);

            assertEquals(original.getType(), deserialized.getType());
            assertEquals(original.getRole(), deserialized.getRole());
            assertEquals(original.getContent(), deserialized.getContent());
        }

        @Test
        void shouldSerializeToolCallEntry() throws Exception {
            TranscriptEntry.ToolCallEntry toolCall = new TranscriptEntry.ToolCallEntry(
                    "tc-1", "search", Map.of("q", "test"));

            TranscriptEntry original = TranscriptEntry.assistantWithToolCalls(
                    "Searching...", List.of(toolCall));

            String json = mapper.writeValueAsString(original);
            TranscriptEntry deserialized = mapper.readValue(json, TranscriptEntry.class);

            assertNotNull(deserialized.getToolCalls());
            assertEquals(1, deserialized.getToolCalls().size());
            assertEquals("search", deserialized.getToolCalls().get(0).getName());
            assertEquals("test", deserialized.getToolCalls().get(0).getArguments().get("q"));
        }

        @Test
        void shouldSerializeToolResultEntry() throws Exception {
            TranscriptEntry.ToolResultEntry result = new TranscriptEntry.ToolResultEntry(
                    "tc-1", "search", "found 3 results", false);
            TranscriptEntry original = TranscriptEntry.toolResult(result);

            String json = mapper.writeValueAsString(original);
            TranscriptEntry deserialized = mapper.readValue(json, TranscriptEntry.class);

            assertNotNull(deserialized.getToolResult());
            assertEquals("search", deserialized.getToolResult().getName());
            assertFalse(deserialized.getToolResult().isError());
        }

        @Test
        void shouldSerializeSystemEventWithMetadata() throws Exception {
            TranscriptEntry original = TranscriptEntry.systemEvent(
                    "Connected", Map.of("ip", "127.0.0.1", "port", 8080));

            String json = mapper.writeValueAsString(original);
            TranscriptEntry deserialized = mapper.readValue(json, TranscriptEntry.class);

            assertEquals("system", deserialized.getType());
            assertNotNull(deserialized.getMetadata());
            assertEquals("127.0.0.1", deserialized.getMetadata().get("ip"));
        }
    }
}
