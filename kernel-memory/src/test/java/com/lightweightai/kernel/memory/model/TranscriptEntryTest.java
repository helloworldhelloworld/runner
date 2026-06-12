package com.lightweightai.kernel.memory.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TranscriptEntry - Jackson serialization round-trip and factory methods")
class TranscriptEntryTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Nested
    @DisplayName("Factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("userMessage creates entry with role=user and type=message")
        void userMessage() {
            TranscriptEntry entry = TranscriptEntry.userMessage("Hello");
            assertEquals("message", entry.getType());
            assertEquals("user", entry.getRole());
            assertEquals("Hello", entry.getContent());
            assertNotNull(entry.getTimestamp());
            assertNull(entry.getToolCalls());
            assertNull(entry.getToolResult());
        }

        @Test
        @DisplayName("assistantMessage creates entry with role=assistant")
        void assistantMessage() {
            TranscriptEntry entry = TranscriptEntry.assistantMessage("Hi there");
            assertEquals("message", entry.getType());
            assertEquals("assistant", entry.getRole());
            assertEquals("Hi there", entry.getContent());
        }

        @Test
        @DisplayName("assistantWithToolCalls includes tool call entries")
        void assistantWithToolCalls() {
            TranscriptEntry.ToolCallEntry tc = new TranscriptEntry.ToolCallEntry(
                    "tc-1", "get_weather", Map.of("city", "Beijing"));

            TranscriptEntry entry = TranscriptEntry.assistantWithToolCalls("Checking weather...", List.of(tc));

            assertEquals("assistant", entry.getRole());
            assertEquals(1, entry.getToolCalls().size());
            assertEquals("tc-1", entry.getToolCalls().get(0).getId());
            assertEquals("get_weather", entry.getToolCalls().get(0).getName());
            assertEquals("Beijing", entry.getToolCalls().get(0).getArguments().get("city"));
        }

        @Test
        @DisplayName("toolResult creates entry with type=tool_result")
        void toolResult() {
            TranscriptEntry.ToolResultEntry tr = new TranscriptEntry.ToolResultEntry(
                    "tc-1", "get_weather", "Sunny, 25C", false);

            TranscriptEntry entry = TranscriptEntry.toolResult(tr);
            assertEquals("tool_result", entry.getType());
            assertNotNull(entry.getToolResult());
            assertEquals("tc-1", entry.getToolResult().getToolCallId());
            assertFalse(entry.getToolResult().isError());
        }

        @Test
        @DisplayName("systemEvent creates entry with role=system and metadata")
        void systemEvent() {
            Map<String, Object> meta = Map.of("reason", "context overflow");
            TranscriptEntry entry = TranscriptEntry.systemEvent("Context compacted", meta);

            assertEquals("system", entry.getType());
            assertEquals("system", entry.getRole());
            assertEquals("Context compacted", entry.getContent());
            assertEquals("context overflow", entry.getMetadata().get("reason"));
        }
    }

    @Nested
    @DisplayName("JSON round-trip")
    class JsonRoundTrip {

        @Test
        @DisplayName("userMessage survives serialize then deserialize")
        void userMessageRoundTrip() throws Exception {
            TranscriptEntry original = TranscriptEntry.userMessage("Hello");

            String json = mapper.writeValueAsString(original);
            TranscriptEntry restored = mapper.readValue(json, TranscriptEntry.class);

            assertEquals(original.getType(), restored.getType());
            assertEquals(original.getRole(), restored.getRole());
            assertEquals(original.getContent(), restored.getContent());
            assertNotNull(restored.getTimestamp());
        }

        @Test
        @DisplayName("assistantWithToolCalls round-trips with nested ToolCallEntry")
        void assistantWithToolCallsRoundTrip() throws Exception {
            TranscriptEntry.ToolCallEntry tc = new TranscriptEntry.ToolCallEntry(
                    "tc-1", "search", Map.of("q", "weather"));
            TranscriptEntry original = TranscriptEntry.assistantWithToolCalls("Looking up...", List.of(tc));

            String json = mapper.writeValueAsString(original);
            TranscriptEntry restored = mapper.readValue(json, TranscriptEntry.class);

            assertEquals(1, restored.getToolCalls().size());
            assertEquals("tc-1", restored.getToolCalls().get(0).getId());
            assertEquals("search", restored.getToolCalls().get(0).getName());
            assertEquals("weather", restored.getToolCalls().get(0).getArguments().get("q"));
        }

        @Test
        @DisplayName("toolResult round-trips with ToolResultEntry preserving isError")
        void toolResultRoundTrip() throws Exception {
            TranscriptEntry.ToolResultEntry tr = new TranscriptEntry.ToolResultEntry(
                    "tc-1", "calc", "42", false);
            TranscriptEntry original = TranscriptEntry.toolResult(tr);

            String json = mapper.writeValueAsString(original);
            TranscriptEntry restored = mapper.readValue(json, TranscriptEntry.class);

            assertNotNull(restored.getToolResult());
            assertEquals("tc-1", restored.getToolResult().getToolCallId());
            assertEquals("calc", restored.getToolResult().getName());
            assertFalse(restored.getToolResult().isError());
        }

        @Test
        @DisplayName("error toolResult preserves isError=true through round-trip")
        void errorToolResultRoundTrip() throws Exception {
            TranscriptEntry.ToolResultEntry tr = new TranscriptEntry.ToolResultEntry(
                    "tc-2", "broken", "timeout", true);
            TranscriptEntry original = TranscriptEntry.toolResult(tr);

            String json = mapper.writeValueAsString(original);
            TranscriptEntry restored = mapper.readValue(json, TranscriptEntry.class);

            assertTrue(restored.getToolResult().isError());
            assertEquals("timeout", restored.getToolResult().getResult());
        }

        @Test
        @DisplayName("systemEvent with metadata round-trips correctly")
        void systemEventRoundTrip() throws Exception {
            TranscriptEntry original = TranscriptEntry.systemEvent(
                    "compacted", Map.of("removed", 5));

            String json = mapper.writeValueAsString(original);
            TranscriptEntry restored = mapper.readValue(json, TranscriptEntry.class);

            assertEquals("system", restored.getType());
            assertEquals("compacted", restored.getContent());
            assertEquals(5, restored.getMetadata().get("removed"));
        }
    }

    @Nested
    @DisplayName("Timestamp handling")
    class TimestampHandling {

        @Test
        @DisplayName("null timestamp defaults to now")
        void nullTimestampDefaultsToNow() {
            Instant before = Instant.now();
            TranscriptEntry entry = new TranscriptEntry(
                    "message", null, "user", "test", null, null, null);
            Instant after = Instant.now();

            assertNotNull(entry.getTimestamp());
            assertFalse(entry.getTimestamp().isBefore(before));
            assertFalse(entry.getTimestamp().isAfter(after));
        }

        @Test
        @DisplayName("explicit timestamp is preserved")
        void explicitTimestampPreserved() {
            Instant ts = Instant.parse("2024-01-15T10:30:00Z");
            TranscriptEntry entry = new TranscriptEntry(
                    "message", ts, "user", "test", null, null, null);

            assertEquals(ts, entry.getTimestamp());
        }
    }
}
