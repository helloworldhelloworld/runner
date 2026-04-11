package com.lightweightai.web.config;

import com.lightweightai.kernel.memory.ConversationMemory;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.embedding.MockEmbeddingProvider;
import com.lightweightai.kernel.memory.file.FileMemoryManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FileMemoryAdapter}.
 *
 * Covers key paths:
 * - Session history delegation (addMessage / getHistory / clearSession)
 * - Persistent memory delegation (writeEphemeral / writeDurable / readDurable)
 * - Search result mapping (SearchResult -> MemorySearchResult)
 * - Integration: write then search
 */
class FileMemoryAdapterTest {

    @TempDir
    Path tempDir;

    private FileMemoryManager fileMemoryManager;
    private FileMemoryAdapter adapter;

    @BeforeEach
    void setUp() {
        fileMemoryManager = new FileMemoryManager(
            tempDir.resolve("agent"),
            "test-agent",
            new MockEmbeddingProvider(64));
        adapter = new FileMemoryAdapter(fileMemoryManager);
    }

    @AfterEach
    void tearDown() {
        if (fileMemoryManager != null) {
            fileMemoryManager.close();
        }
    }

    // ==================== Session history ====================

    @Test
    void addMessage_andGetHistory_returnsStoredMessages() {
        String sessionId = "session-1";
        adapter.addMessage(sessionId, Message.user("Hello"));
        adapter.addMessage(sessionId, Message.assistant("Hi there"));

        List<Message> history = adapter.getHistory(sessionId, 10);

        assertEquals(2, history.size());
        assertEquals("user", history.get(0).getRole());
        assertEquals("Hello", history.get(0).getContent());
        assertEquals("assistant", history.get(1).getRole());
        assertEquals("Hi there", history.get(1).getContent());
    }

    @Test
    void getHistory_respectsLimit() {
        String sessionId = "s";
        for (int i = 0; i < 5; i++) {
            adapter.addMessage(sessionId, Message.user("msg " + i));
        }

        List<Message> history = adapter.getHistory(sessionId, 2);
        // Only the most recent 2 are returned
        assertEquals(2, history.size());
    }

    @Test
    void getHistory_unknownSession_returnsEmpty() {
        List<Message> history = adapter.getHistory("missing", 10);
        assertNotNull(history);
        assertTrue(history.isEmpty());
    }

    @Test
    void clearSession_removesAllMessages() {
        String sessionId = "s";
        adapter.addMessage(sessionId, Message.user("a"));
        adapter.addMessage(sessionId, Message.user("b"));
        assertEquals(2, adapter.getHistory(sessionId, 10).size());

        adapter.clearSession(sessionId);

        assertTrue(adapter.getHistory(sessionId, 10).isEmpty());
    }

    @Test
    void addMessage_isolatedBetweenSessions() {
        adapter.addMessage("s1", Message.user("alpha"));
        adapter.addMessage("s2", Message.user("beta"));

        assertEquals(1, adapter.getHistory("s1", 10).size());
        assertEquals("alpha", adapter.getHistory("s1", 10).get(0).getContent());
        assertEquals(1, adapter.getHistory("s2", 10).size());
        assertEquals("beta", adapter.getHistory("s2", 10).get(0).getContent());
    }

    // ==================== Persistent memory ====================

    @Test
    void writeDurable_isPersistedAndReadable() {
        adapter.writeDurable("Preferences", "User likes Java");

        String durable = adapter.readDurable();
        assertTrue(durable.contains("User likes Java"));
        assertTrue(durable.contains("## Preferences"));
    }

    @Test
    void readTodayEphemeral_returnsEmptyByContract() {
        // Adapter documents that readTodayEphemeral always returns ""
        adapter.writeEphemeral("today's note");
        assertEquals("", adapter.readTodayEphemeral());
    }

    @Test
    void writeEphemeral_doesNotThrow() {
        // Just ensure the delegation path does not raise
        assertDoesNotThrow(() -> adapter.writeEphemeral("ephemeral note"));
    }

    // ==================== Search ====================

    @Test
    void search_afterWrite_returnsMatches() {
        adapter.writeDurable("Topic", "Machine learning uses neural networks");

        List<MemorySearchResult> results = adapter.search("neural networks");

        assertFalse(results.isEmpty(), "expected at least one match");
        MemorySearchResult top = results.get(0);
        assertNotNull(top.getContent());
        assertTrue(top.getScore() >= 0);
        assertNotNull(top.getSource());
    }

    @Test
    void search_noData_returnsEmpty() {
        List<MemorySearchResult> results = adapter.search("unknown query");
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    // ==================== Accessors ====================

    @Test
    void getConversationMemory_returnsNonNullInstance() {
        ConversationMemory cm = adapter.getConversationMemory();
        assertNotNull(cm);
        // Verify it is the same instance that handles addMessage
        adapter.addMessage("probe", Message.user("x"));
        assertFalse(cm.getRecentMessages("probe", 10).isEmpty());
    }
}
