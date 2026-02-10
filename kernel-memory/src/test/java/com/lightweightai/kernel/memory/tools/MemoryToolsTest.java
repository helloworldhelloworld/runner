package com.lightweightai.kernel.memory.tools;

import com.lightweightai.kernel.memory.embedding.MockEmbeddingProvider;
import com.lightweightai.kernel.memory.file.FileMemoryManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MemoryToolsTest {

    @TempDir
    Path tempDir;

    private FileMemoryManager memoryManager;
    private MemoryToolkit toolkit;

    @BeforeEach
    void setUp() {
        Path agentRoot = tempDir.resolve("test-agent");
        MockEmbeddingProvider embeddingProvider = new MockEmbeddingProvider(64);
        memoryManager = new FileMemoryManager(agentRoot, "test-agent", embeddingProvider);
        toolkit = new MemoryToolkit(memoryManager);
    }

    @AfterEach
    void tearDown() {
        if (memoryManager != null) {
            memoryManager.close();
        }
    }

    // ==================== WriteMemoryTool ====================

    @Test
    void testWriteEphemeralMemory() {
        WriteMemoryTool.WriteMemoryResult result = toolkit.getWriteTool().execute(Map.of(
            "content", "User prefers dark mode",
            "type", "ephemeral"
        ));

        assertTrue(result.success());
        assertNull(result.error());
        assertTrue(result.message().contains("ephemeral"));

        // Verify it was saved
        String ephemeral = memoryManager.readTodayEphemeral();
        assertTrue(ephemeral.contains("User prefers dark mode"));
    }

    @Test
    void testWriteDurableMemory() {
        WriteMemoryTool.WriteMemoryResult result = toolkit.getWriteTool().execute(Map.of(
            "content", "Favorite color is blue",
            "type", "durable",
            "section", "User Preferences"
        ));

        assertTrue(result.success());
        assertTrue(result.message().contains("User Preferences"));

        // Verify it was saved
        String durable = memoryManager.readDurable();
        assertTrue(durable.contains("Favorite color is blue"));
        assertTrue(durable.contains("## User Preferences"));
    }

    @Test
    void testWriteDefaultType() {
        WriteMemoryTool.WriteMemoryResult result = toolkit.getWriteTool().execute(Map.of(
            "content", "Default type test"
        ));

        assertTrue(result.success());
        assertTrue(result.message().contains("ephemeral"));
    }

    @Test
    void testWriteEmptyContent() {
        WriteMemoryTool.WriteMemoryResult result = toolkit.getWriteTool().execute(Map.of(
            "content", ""
        ));

        assertFalse(result.success());
        assertTrue(result.error().contains("required"));
    }

    @Test
    void testWriteInvalidType() {
        WriteMemoryTool.WriteMemoryResult result = toolkit.getWriteTool().execute(Map.of(
            "content", "Test",
            "type", "invalid"
        ));

        assertFalse(result.success());
        assertTrue(result.error().contains("Invalid type"));
    }

    // ==================== MemorySearchTool ====================

    @Test
    void testSearchMemory() {
        // Write some content first
        memoryManager.writeDurable("Machine learning is a subset of artificial intelligence");

        MemorySearchTool.MemorySearchResult result = toolkit.getSearchTool().execute(Map.of(
            "query", "machine learning AI"
        ));

        assertTrue(result.success());
        assertFalse(result.matches().isEmpty());
    }

    @Test
    void testSearchWithTopK() {
        memoryManager.writeDurable("Document 1 about topic\n\nDocument 2 about topic\n\nDocument 3 about topic");

        MemorySearchTool.MemorySearchResult result = toolkit.getSearchTool().execute(Map.of(
            "query", "topic",
            "top_k", 2
        ));

        assertTrue(result.success());
        assertTrue(result.matches().size() <= 2);
    }

    @Test
    void testSearchEmptyQuery() {
        MemorySearchTool.MemorySearchResult result = toolkit.getSearchTool().execute(Map.of(
            "query", ""
        ));

        assertFalse(result.success());
        assertTrue(result.error().contains("required"));
    }

    @Test
    void testSearchNoResults() {
        MemorySearchTool.MemorySearchResult result = toolkit.getSearchTool().execute(Map.of(
            "query", "nonexistent content xyz"
        ));

        assertTrue(result.success());
        assertTrue(result.matches().isEmpty());
    }

    @Test
    void testSearchResultToText() {
        memoryManager.writeDurable("Important information about the project");

        MemorySearchTool.MemorySearchResult result = toolkit.getSearchTool().execute(Map.of(
            "query", "project information"
        ));

        String text = result.toText();
        assertTrue(text.contains("Found") || text.contains("No matching"));
    }

    // ==================== MemoryToolkit ====================

    @Test
    void testExecuteByName() {
        Object result = toolkit.execute(MemorySearchTool.TOOL_NAME, Map.of(
            "query", "test"
        ));

        assertTrue(result instanceof MemorySearchTool.MemorySearchResult);
    }

    @Test
    void testExecuteUnknownTool() {
        assertThrows(IllegalArgumentException.class, () ->
            toolkit.execute("unknown_tool", Map.of())
        );
    }

    @Test
    void testGetToolSchemas() {
        List<Map<String, Object>> schemas = toolkit.getToolSchemas();

        assertEquals(2, schemas.size());

        // Check search tool schema
        assertTrue(schemas.stream().anyMatch(s -> s.get("name").equals(MemorySearchTool.TOOL_NAME)));

        // Check write tool schema
        assertTrue(schemas.stream().anyMatch(s -> s.get("name").equals(WriteMemoryTool.TOOL_NAME)));
    }

    @Test
    void testGetExecutor() {
        var executor = toolkit.getExecutor();

        String result = executor.apply(new MemoryToolkit.ToolCall(
            MemorySearchTool.TOOL_NAME,
            Map.of("query", "test")
        ));

        assertNotNull(result);
    }

    @Test
    void testToolSchemaHasRequiredFields() {
        Map<String, Object> searchSchema = MemorySearchTool.getToolSchema();

        assertNotNull(searchSchema.get("name"));
        assertNotNull(searchSchema.get("description"));
        assertNotNull(searchSchema.get("input_schema"));

        Map<String, Object> writeSchema = WriteMemoryTool.getToolSchema();

        assertNotNull(writeSchema.get("name"));
        assertNotNull(writeSchema.get("description"));
        assertNotNull(writeSchema.get("input_schema"));
    }

    // ==================== Integration ====================

    @Test
    void testWriteThenSearch() {
        // Write content
        toolkit.getWriteTool().execute(Map.of(
            "content", "The user's favorite programming language is Java",
            "type", "durable",
            "section", "Preferences"
        ));

        // Search for it
        MemorySearchTool.MemorySearchResult result = toolkit.getSearchTool().execute(Map.of(
            "query", "favorite programming language"
        ));

        assertTrue(result.success());
        assertFalse(result.matches().isEmpty());
        assertTrue(result.matches().get(0).content().contains("Java"));
    }
}
