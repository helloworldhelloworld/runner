package com.lightweightai.kernel.memory.file;

import com.lightweightai.kernel.memory.model.TranscriptEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class SessionTranscriptTest {

    @TempDir
    Path tempDir;

    private Path sessionsDir;

    @BeforeEach
    void setUp() {
        sessionsDir = tempDir.resolve("sessions");
    }

    @Test
    void testCreateNewSession() {
        SessionTranscript transcript = new SessionTranscript(sessionsDir, "test-session");

        assertFalse(transcript.exists());
        assertEquals("test-session", transcript.getSessionId());
        assertTrue(transcript.getSessionFile().toString().endsWith("test-session.jsonl"));
    }

    @Test
    void testAppendAndRead() {
        SessionTranscript transcript = new SessionTranscript(sessionsDir, "test-append");

        // Append entries
        transcript.append(TranscriptEntry.userMessage("Hello"));
        transcript.append(TranscriptEntry.assistantMessage("Hi there!"));

        assertTrue(transcript.exists());

        // Read back
        List<TranscriptEntry> entries = transcript.readAll();
        assertEquals(2, entries.size());

        assertEquals("message", entries.get(0).getType());
        assertEquals("user", entries.get(0).getRole());
        assertEquals("Hello", entries.get(0).getContent());

        assertEquals("message", entries.get(1).getType());
        assertEquals("assistant", entries.get(1).getRole());
        assertEquals("Hi there!", entries.get(1).getContent());
    }

    @Test
    void testAppendToolCall() {
        SessionTranscript transcript = new SessionTranscript(sessionsDir, "test-tools");

        List<TranscriptEntry.ToolCallEntry> toolCalls = List.of(
            new TranscriptEntry.ToolCallEntry("call_1", "get_weather", Map.of("city", "Tokyo"))
        );
        transcript.append(TranscriptEntry.assistantWithToolCalls("Let me check the weather", toolCalls));

        TranscriptEntry.ToolResultEntry result = new TranscriptEntry.ToolResultEntry(
            "call_1", "get_weather", "Sunny, 25°C", false
        );
        transcript.append(TranscriptEntry.toolResult(result));

        List<TranscriptEntry> entries = transcript.readAll();
        assertEquals(2, entries.size());

        // Check tool call
        assertNotNull(entries.get(0).getToolCalls());
        assertEquals(1, entries.get(0).getToolCalls().size());
        assertEquals("get_weather", entries.get(0).getToolCalls().get(0).getName());

        // Check tool result
        assertNotNull(entries.get(1).getToolResult());
        assertEquals("call_1", entries.get(1).getToolResult().getToolCallId());
        assertFalse(entries.get(1).getToolResult().isError());
    }

    @Test
    void testStreamEntries() {
        SessionTranscript transcript = new SessionTranscript(sessionsDir, "test-stream");

        // Add multiple entries
        for (int i = 0; i < 10; i++) {
            transcript.append(TranscriptEntry.userMessage("Message " + i));
        }

        // Stream and count
        try (Stream<TranscriptEntry> stream = transcript.stream()) {
            long count = stream.count();
            assertEquals(10, count);
        }
    }

    @Test
    void testGetEntryCount() {
        SessionTranscript transcript = new SessionTranscript(sessionsDir, "test-count");

        assertEquals(0, transcript.getEntryCount());

        transcript.append(TranscriptEntry.userMessage("One"));
        transcript.append(TranscriptEntry.userMessage("Two"));
        transcript.append(TranscriptEntry.userMessage("Three"));

        assertEquals(3, transcript.getEntryCount());
    }

    @Test
    void testGetFileSize() {
        SessionTranscript transcript = new SessionTranscript(sessionsDir, "test-size");

        assertEquals(0, transcript.getFileSize());

        transcript.append(TranscriptEntry.userMessage("Some content here"));

        assertTrue(transcript.getFileSize() > 0);
    }

    @Test
    void testNeedsIndexing() {
        SessionTranscript transcript = new SessionTranscript(sessionsDir, "test-indexing");

        // Initially doesn't need indexing
        assertFalse(transcript.needsIndexing(0, 0));

        // Add entries
        for (int i = 0; i < 60; i++) {
            transcript.append(TranscriptEntry.userMessage("Message " + i));
        }

        // Now should need indexing (> 50 messages)
        assertTrue(transcript.needsIndexing(0, 0));

        // After indexing, shouldn't need it again
        long currentCount = transcript.getEntryCount();
        long currentSize = transcript.getFileSize();
        assertFalse(transcript.needsIndexing(currentSize, currentCount));
    }

    @Test
    void testGenerateSlug() {
        SessionTranscript transcript = new SessionTranscript(sessionsDir, "test-slug");

        // Empty session
        assertEquals("empty-session", transcript.generateSlug());

        // With content
        transcript.append(TranscriptEntry.userMessage("How do I fix the authentication bug?"));

        String slug = transcript.generateSlug();
        assertTrue(slug.contains("how"));
        assertTrue(slug.contains("fix"));
        assertFalse(slug.contains("?"));  // Special chars removed
    }

    @Test
    void testCreateWithDatePrefix() {
        SessionTranscript transcript = SessionTranscript.create(sessionsDir, "my-chat");

        String sessionId = transcript.getSessionId();
        assertTrue(sessionId.matches("\\d{4}-\\d{2}-\\d{2}-my-chat"));
    }

    @Test
    void testTimestampPreserved() throws InterruptedException {
        SessionTranscript transcript = new SessionTranscript(sessionsDir, "test-timestamp");

        transcript.append(TranscriptEntry.userMessage("Test"));
        Thread.sleep(10);

        List<TranscriptEntry> entries = transcript.readAll();
        assertNotNull(entries.get(0).getTimestamp());
    }

    @Test
    void testReadEmptyFile() {
        SessionTranscript transcript = new SessionTranscript(sessionsDir, "nonexistent");

        List<TranscriptEntry> entries = transcript.readAll();
        assertTrue(entries.isEmpty());
    }

    @Test
    void testSystemEvent() {
        SessionTranscript transcript = new SessionTranscript(sessionsDir, "test-system");

        transcript.append(TranscriptEntry.systemEvent(
            "Session started",
            Map.of("model", "claude-3", "temperature", 0.7)
        ));

        List<TranscriptEntry> entries = transcript.readAll();
        assertEquals(1, entries.size());
        assertEquals("system", entries.get(0).getType());
        assertNotNull(entries.get(0).getMetadata());
        assertEquals("claude-3", entries.get(0).getMetadata().get("model"));
    }
}
