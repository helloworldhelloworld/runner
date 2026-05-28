package com.lightweightai.kernel.memory.tools;

import com.lightweightai.kernel.memory.embedding.MockEmbeddingProvider;
import com.lightweightai.kernel.memory.file.FileMemoryManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemorySearchTool")
class MemorySearchToolTest {

    @TempDir
    Path tempDir;

    private MemorySearchTool tool;
    private FileMemoryManager memoryManager;

    @BeforeEach
    void setUp() {
        Path agentRoot = tempDir.resolve("search-test-agent");
        memoryManager = new FileMemoryManager(agentRoot, "search-test", new MockEmbeddingProvider(64));
        tool = new MemorySearchTool(memoryManager);
    }

    @AfterEach
    void tearDown() {
        if (memoryManager != null) {
            memoryManager.close();
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
    @DisplayName("returns success with empty matches for unknown query on empty index")
    void emptyResultsForUnknownQuery() {
        var result = tool.execute(Map.of("query", "completely unknown topic xyz"));

        assertTrue(result.success());
        assertNull(result.error());
        assertTrue(result.matches().isEmpty());
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
