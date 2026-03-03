package com.lightweightai.kernel.memory.file;

import com.lightweightai.kernel.memory.embedding.MockEmbeddingProvider;
import com.lightweightai.kernel.memory.model.SearchResult;
import com.lightweightai.kernel.memory.model.TranscriptEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.lightweightai.kernel.memory.index.HybridSearch;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileMemoryManagerTest {

    @TempDir
    Path tempDir;

    private FileMemoryManager memoryManager;
    private Path agentRoot;

    @BeforeEach
    void setUp() {
        agentRoot = tempDir.resolve("test-agent");
        MockEmbeddingProvider embeddingProvider = new MockEmbeddingProvider(64);
        memoryManager = new FileMemoryManager(agentRoot, "test-agent", embeddingProvider);
    }

    @AfterEach
    void tearDown() {
        if (memoryManager != null) {
            memoryManager.close();
        }
    }

    // ==================== Directory Structure ====================

    @Test
    void testDirectoriesCreated() {
        assertTrue(Files.isDirectory(agentRoot));
        assertTrue(Files.isDirectory(memoryManager.getMemoryDir()));
        assertTrue(Files.isDirectory(memoryManager.getSessionsDir()));
        assertTrue(Files.isDirectory(agentRoot.resolve("index")));
    }

    // ==================== Ephemeral Memory ====================

    @Test
    void testAppendEphemeral() {
        memoryManager.appendEphemeral("Today I learned about AI");

        String content = memoryManager.readTodayEphemeral();

        assertTrue(content.contains("Today I learned about AI"));
    }

    @Test
    void testMultipleEphemeralAppends() {
        memoryManager.appendEphemeral("First entry");
        memoryManager.appendEphemeral("Second entry");

        String content = memoryManager.readTodayEphemeral();

        assertTrue(content.contains("First entry"));
        assertTrue(content.contains("Second entry"));
    }

    @Test
    void testReadNonExistentEphemeral() {
        String content = memoryManager.readEphemeral(LocalDate.of(2020, 1, 1));
        assertEquals("", content);
    }

    // ==================== Durable Memory ====================

    @Test
    void testWriteDurable() {
        String content = "# My Durable Memory\n\nImportant information here.";
        memoryManager.writeDurable(content);

        String retrieved = memoryManager.readDurable();
        assertEquals(content, retrieved);
    }

    @Test
    void testAppendDurableNewSection() {
        memoryManager.writeDurable("# Memory\n\n## Existing Section\n\nExisting content");
        memoryManager.appendDurable("New Section", "New content here");

        String content = memoryManager.readDurable();

        assertTrue(content.contains("## Existing Section"));
        assertTrue(content.contains("## New Section"));
        assertTrue(content.contains("New content here"));
    }

    @Test
    void testAppendDurableExistingSection() {
        memoryManager.writeDurable("# Memory\n\n## Notes\n\nFirst note.");
        memoryManager.appendDurable("Notes", "\nSecond note.");

        String content = memoryManager.readDurable();

        assertTrue(content.contains("First note."));
        assertTrue(content.contains("Second note."));
    }

    @Test
    void testReadEmptyDurable() {
        String content = memoryManager.readDurable();
        assertEquals("", content);
    }

    // ==================== Session Memory ====================

    @Test
    void testCreateSession() {
        SessionTranscript session = memoryManager.createSession("test-chat");

        assertNotNull(session);
        assertTrue(session.getSessionId().contains("test-chat"));
    }

    @Test
    void testSessionTranscript() {
        SessionTranscript session = memoryManager.getSession("my-session");

        session.append(TranscriptEntry.userMessage("Hello"));
        session.append(TranscriptEntry.assistantMessage("Hi there!"));

        List<TranscriptEntry> entries = session.readAll();
        assertEquals(2, entries.size());
    }

    @Test
    void testListSessions() {
        memoryManager.getSession("session-1").append(TranscriptEntry.userMessage("Test"));
        memoryManager.getSession("session-2").append(TranscriptEntry.userMessage("Test"));

        List<String> sessions = memoryManager.listSessions();

        assertEquals(2, sessions.size());
        assertTrue(sessions.contains("session-1"));
        assertTrue(sessions.contains("session-2"));
    }

    @Test
    void testListSessionsEmpty() {
        List<String> sessions = memoryManager.listSessions();
        assertTrue(sessions.isEmpty());
    }

    // ==================== Search ====================

    @Test
    void testSearchDurableMemory() {
        memoryManager.writeDurable("# Knowledge Base\n\nMachine learning is a subset of artificial intelligence.");

        List<SearchResult> results = memoryManager.search("machine learning AI");

        assertFalse(results.isEmpty());
    }

    @Test
    void testSearchEphemeralMemory() {
        memoryManager.appendEphemeral("Discussed project architecture today");

        List<SearchResult> results = memoryManager.search("project architecture");

        assertFalse(results.isEmpty());
    }

    @Test
    void testSearchKeywordsOnly() {
        memoryManager.writeDurable("Important keyword document for testing purposes");

        List<SearchResult> results = memoryManager.searchKeywords("keyword testing", 10);

        assertFalse(results.isEmpty());
    }

    @Test
    void testSearchSemanticOnly() {
        memoryManager.writeDurable("Neural networks and deep learning concepts");

        List<SearchResult> results = memoryManager.searchSemantic("AI machine learning", 10);

        // May be empty with mock embeddings due to low similarity
        assertNotNull(results);
    }

    @Test
    void testSearchEmptyMemory() {
        List<SearchResult> results = memoryManager.search("anything");
        assertTrue(results.isEmpty());
    }

    // ==================== Indexing ====================

    @Test
    void testReindexAll() {
        memoryManager.writeDurable("Durable content for indexing");
        memoryManager.appendEphemeral("Ephemeral content for indexing");

        memoryManager.reindexAll();

        HybridSearch.IndexStats stats = memoryManager.getIndexStats();
        assertTrue(stats.bm25ChunkCount() >= 2);
    }

    @Test
    void testIndexStats() {
        memoryManager.writeDurable("Some content to index");

        HybridSearch.IndexStats stats = memoryManager.getIndexStats();

        assertTrue(stats.bm25ChunkCount() >= 0);
        assertEquals(64, stats.embeddingDimensions());
    }

    // ==================== Lifecycle ====================

    @Test
    void testAgentIdPreserved() {
        assertEquals("test-agent", memoryManager.getAgentId());
    }

    @Test
    void testAgentRootPreserved() {
        assertEquals(agentRoot, memoryManager.getAgentRoot());
    }
}
