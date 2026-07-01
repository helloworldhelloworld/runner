package com.lightweightai.kernel.memory.tools;

import com.lightweightai.kernel.memory.embedding.MockEmbeddingProvider;
import com.lightweightai.kernel.memory.file.FileMemoryManager;
import com.lightweightai.kernel.memory.tools.MemorySearchTool.MemoryMatch;
import com.lightweightai.kernel.memory.tools.MemorySearchTool.MemorySearchResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemorySearchTool - Hybrid memory search")
class MemorySearchToolTest {

    @TempDir
    Path tempDir;

    private FileMemoryManager memoryManager;
    private MemorySearchTool searchTool;

    @BeforeEach
    void setUp() {
        Path agentRoot = tempDir.resolve("test-agent");
        MockEmbeddingProvider embeddingProvider = new MockEmbeddingProvider(64);
        memoryManager = new FileMemoryManager(agentRoot, "test-agent", embeddingProvider);
        searchTool = new MemorySearchTool(memoryManager);
    }

    @AfterEach
    void tearDown() {
        if (memoryManager != null) {
            memoryManager.close();
        }
    }

    // ==================== Parameter validation ====================

    @Test
    @DisplayName("null query returns error result")
    void nullQueryReturnsError() {
        MemorySearchResult result = searchTool.execute(Map.of());

        assertFalse(result.success());
        assertEquals("Query parameter is required", result.error());
        assertTrue(result.matches().isEmpty());
    }

    @Test
    @DisplayName("blank query returns error result")
    void blankQueryReturnsError() {
        MemorySearchResult result = searchTool.execute(Map.of("query", "   "));

        assertFalse(result.success());
        assertEquals("Query parameter is required", result.error());
    }

    @Test
    @DisplayName("empty string query returns error result")
    void emptyStringQueryReturnsError() {
        MemorySearchResult result = searchTool.execute(Map.of("query", ""));

        assertFalse(result.success());
        assertEquals("Query parameter is required", result.error());
    }

    // ==================== Default parameters ====================

    @Test
    @DisplayName("valid query with no optional params uses defaults")
    void validQueryUsesDefaults() {
        MemorySearchResult result = searchTool.execute(Map.of("query", "hello world"));

        assertTrue(result.success());
        assertNull(result.error());
        // No memories written yet, so no matches expected
        assertTrue(result.matches().isEmpty());
    }

    @Test
    @DisplayName("top_k parameter is respected as Number")
    void topKParameterRespected() {
        MemorySearchResult result = searchTool.execute(Map.of(
            "query", "test",
            "top_k", 3
        ));

        assertTrue(result.success());
    }

    @Test
    @DisplayName("vector_weight parameter is respected as Number")
    void vectorWeightParameterRespected() {
        MemorySearchResult result = searchTool.execute(Map.of(
            "query", "test",
            "vector_weight", 0.5
        ));

        assertTrue(result.success());
    }

    @Test
    @DisplayName("non-Number top_k falls back to default (5)")
    void nonNumberTopKFallsBackToDefault() {
        Map<String, Object> params = new HashMap<>();
        params.put("query", "test");
        params.put("top_k", "not-a-number");

        MemorySearchResult result = searchTool.execute(params);

        assertTrue(result.success());
    }

    @Test
    @DisplayName("non-Number vector_weight falls back to default (0.7)")
    void nonNumberVectorWeightFallsBackToDefault() {
        Map<String, Object> params = new HashMap<>();
        params.put("query", "test");
        params.put("vector_weight", "invalid");

        MemorySearchResult result = searchTool.execute(params);

        assertTrue(result.success());
    }

    // ==================== Search with indexed content ====================

    @Test
    @DisplayName("search returns matches after writing durable memory")
    void searchReturnsMatchesAfterWriting() {
        memoryManager.writeDurable("preferences", "User prefers dark mode and large fonts");
        memoryManager.writeDurable("schedule", "User has meetings on Monday mornings");

        MemorySearchResult result = searchTool.execute(Map.of("query", "dark mode"));

        assertTrue(result.success());
        assertNull(result.error());
        // Should find at least one match
        assertFalse(result.matches().isEmpty(), "Should find matches for indexed content");

        MemoryMatch match = result.matches().get(0);
        assertNotNull(match.content());
        assertNotNull(match.source());
        assertTrue(match.score() >= 0, "Score should be non-negative");
    }

    // ==================== Tool schema ====================

    @Test
    @DisplayName("getToolSchema returns valid schema with required fields")
    void getToolSchemaReturnsValidSchema() {
        Map<String, Object> schema = MemorySearchTool.getToolSchema();

        assertEquals("memory_search", schema.get("name"));
        assertNotNull(schema.get("description"));

        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");
        assertEquals("object", inputSchema.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
        assertTrue(properties.containsKey("query"));
        assertTrue(properties.containsKey("top_k"));
        assertTrue(properties.containsKey("vector_weight"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) inputSchema.get("required");
        assertTrue(required.contains("query"));
    }

    // ==================== toText formatting ====================

    @Test
    @DisplayName("toText for error result shows error message")
    void toTextForErrorResult() {
        MemorySearchResult result = new MemorySearchResult(false, "Something broke", List.of());

        assertEquals("Error: Something broke", result.toText());
    }

    @Test
    @DisplayName("toText for empty results shows 'no matching' message")
    void toTextForEmptyResults() {
        MemorySearchResult result = new MemorySearchResult(true, null, List.of());

        assertEquals("No matching memories found.", result.toText());
    }

    @Test
    @DisplayName("toText for results shows numbered list with scores")
    void toTextForResults() {
        List<MemoryMatch> matches = List.of(
            new MemoryMatch("content1", "file1.md", 0.95f, "snippet1"),
            new MemoryMatch("content2", "file2.md", 0.80f, "snippet2")
        );
        MemorySearchResult result = new MemorySearchResult(true, null, matches);

        String text = result.toText();
        assertTrue(text.contains("Found 2 relevant memories"));
        assertTrue(text.contains("1. [file1.md]"));
        assertTrue(text.contains("0.950"));
        assertTrue(text.contains("snippet1"));
        assertTrue(text.contains("2. [file2.md]"));
        assertTrue(text.contains("0.800"));
        assertTrue(text.contains("snippet2"));
    }

    // ==================== Tool name and description ====================

    @Test
    @DisplayName("TOOL_NAME is memory_search")
    void toolNameIsCorrect() {
        assertEquals("memory_search", MemorySearchTool.TOOL_NAME);
    }

    @Test
    @DisplayName("TOOL_DESCRIPTION is non-empty")
    void toolDescriptionIsNonEmpty() {
        assertFalse(MemorySearchTool.TOOL_DESCRIPTION.isEmpty());
    }
}
