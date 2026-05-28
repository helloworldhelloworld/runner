package com.lightweightai.kernel.memory.tools;

import com.lightweightai.kernel.memory.embedding.MockEmbeddingProvider;
import com.lightweightai.kernel.memory.file.FileMemoryManager;
import com.lightweightai.kernel.memory.model.SearchOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemorySearchTool")
class MemorySearchToolTest {

    private MemorySearchTool tool;
    private FileMemoryManager memoryManager;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("memory-search-test");
        memoryManager = new FileMemoryManager(tempDir, "test-agent", new MockEmbeddingProvider());
        tool = new MemorySearchTool(memoryManager);
    }

    @AfterEach
    void tearDown() throws IOException {
        memoryManager.close();
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        }
    }

    @Test
    @DisplayName("returns error when query is null")
    void returnsErrorOnNullQuery() {
        var result = tool.execute(Map.of());

        assertFalse(result.success());
        assertNotNull(result.error());
        assertTrue(result.error().contains("required"));
    }

    @Test
    @DisplayName("returns error when query is blank")
    void returnsErrorOnBlankQuery() {
        var result = tool.execute(Map.of("query", "   "));

        assertFalse(result.success());
    }

    @Test
    @DisplayName("returns success with empty matches for unknown query")
    void emptyResultsForUnknownQuery() {
        var result = tool.execute(Map.of("query", "completely unknown topic xyz"));

        assertTrue(result.success());
        assertNull(result.error());
        assertTrue(result.matches().isEmpty());
    }

    @Test
    @DisplayName("searches after writing durable memory")
    void searchesAfterWritingMemory() throws IOException {
        Path durableFile = tempDir.resolve("MEMORY.md");
        Files.writeString(durableFile, "# User Preferences\n\nThe user loves Java and Spring Boot.\n");

        memoryManager.reindexAll();

        var result = tool.execute(Map.of("query", "Java"));

        assertTrue(result.success());
        assertFalse(result.matches().isEmpty(), "should find at least one match for 'Java'");
        assertTrue(result.matches().get(0).content().contains("Java"));
    }

    @Test
    @DisplayName("toText() for empty results says no matching memories")
    void toTextEmpty() {
        var result = new MemorySearchTool.MemorySearchResult(true, null, List.of());
        assertEquals("No matching memories found.", result.toText());
    }

    @Test
    @DisplayName("toText() for error shows error message")
    void toTextError() {
        var result = new MemorySearchTool.MemorySearchResult(false, "something broke", List.of());
        assertEquals("Error: something broke", result.toText());
    }

    @Test
    @DisplayName("getToolSchema returns valid schema with required fields")
    void toolSchemaValid() {
        Map<String, Object> schema = MemorySearchTool.getToolSchema();

        assertEquals("memory_search", schema.get("name"));
        assertNotNull(schema.get("description"));
        assertNotNull(schema.get("input_schema"));
    }
}
