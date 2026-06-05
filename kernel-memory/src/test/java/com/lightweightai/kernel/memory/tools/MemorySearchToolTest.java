package com.lightweightai.kernel.memory.tools;

import com.lightweightai.kernel.memory.embedding.MockEmbeddingProvider;
import com.lightweightai.kernel.memory.file.FileMemoryManager;
import com.lightweightai.kernel.memory.model.MemoryChunk;
import com.lightweightai.kernel.memory.model.MemoryType;
import com.lightweightai.kernel.memory.model.SearchOptions;
import com.lightweightai.kernel.memory.model.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemorySearchTool — hybrid search tool for AI agents")
class MemorySearchToolTest {

    private StubFileMemoryManager stubManager;
    private MemorySearchTool searchTool;

    @BeforeEach
    void setUp() {
        stubManager = new StubFileMemoryManager();
        searchTool = new MemorySearchTool(stubManager);
    }

    @Test
    @DisplayName("tool name and schema constants are correct")
    void toolMetadata() {
        assertEquals("memory_search", MemorySearchTool.TOOL_NAME);
        assertFalse(MemorySearchTool.TOOL_DESCRIPTION.isEmpty());

        Map<String, Object> schema = MemorySearchTool.getToolSchema();
        assertEquals("memory_search", schema.get("name"));
        assertNotNull(schema.get("input_schema"));
    }

    @Test
    @DisplayName("execute with valid query returns success with matches")
    void executeWithValidQuery() {
        stubManager.setResults(List.of(
                new SearchResult(chunk("user prefers dark theme", "prefs.md"),
                        0.85f, 0.5f, 0.9f, "user prefers dark theme")));

        MemorySearchTool.MemorySearchResult result = searchTool.execute(Map.of("query", "theme preference"));

        assertTrue(result.success());
        assertNull(result.error());
        assertEquals(1, result.matches().size());
        assertEquals("prefs.md", result.matches().get(0).source());
        assertEquals(0.85f, result.matches().get(0).score(), 0.001f);
    }

    @Test
    @DisplayName("execute with null query returns error")
    void executeNullQuery() {
        MemorySearchTool.MemorySearchResult result = searchTool.execute(Map.of());
        assertFalse(result.success());
        assertTrue(result.error().contains("required"));
    }

    @Test
    @DisplayName("execute with blank query returns error")
    void executeBlankQuery() {
        MemorySearchTool.MemorySearchResult result = searchTool.execute(Map.of("query", "   "));
        assertFalse(result.success());
    }

    @Test
    @DisplayName("execute with no results returns empty matches")
    void executeNoResults() {
        stubManager.setResults(List.of());
        MemorySearchTool.MemorySearchResult result = searchTool.execute(Map.of("query", "nonexistent"));
        assertTrue(result.success());
        assertTrue(result.matches().isEmpty());
    }

    @Test
    @DisplayName("toText formats results correctly")
    void toTextFormatting() {
        stubManager.setResults(List.of(
                new SearchResult(chunk("content", "source.md"),
                        0.9f, 0.6f, 0.95f, "snippet text")));

        MemorySearchTool.MemorySearchResult result = searchTool.execute(Map.of("query", "test"));
        String text = result.toText();

        assertTrue(text.contains("Found 1 relevant memories"));
        assertTrue(text.contains("source.md"));
        assertTrue(text.contains("0.900"));
        assertTrue(text.contains("snippet text"));
    }

    @Test
    @DisplayName("toText for error shows error message")
    void toTextError() {
        MemorySearchTool.MemorySearchResult result = searchTool.execute(Map.of());
        assertTrue(result.toText().startsWith("Error:"));
    }

    @Test
    @DisplayName("toText for empty results shows 'no matching'")
    void toTextEmpty() {
        stubManager.setResults(List.of());
        MemorySearchTool.MemorySearchResult result = searchTool.execute(Map.of("query", "nothing"));
        assertTrue(result.toText().contains("No matching memories found"));
    }

    @Test
    @DisplayName("optional parameters (top_k, vector_weight) are captured")
    void optionalParams() {
        stubManager.setResults(List.of());
        searchTool.execute(Map.of("query", "test", "top_k", 10, "vector_weight", 0.3));

        assertNotNull(stubManager.lastOptions);
        assertEquals(10, stubManager.lastOptions.getTopK());
        assertEquals(0.3f, stubManager.lastOptions.getVectorWeight(), 0.001f);
    }

    @Test
    @DisplayName("schema contains required fields")
    void schemaStructure() {
        Map<String, Object> schema = MemorySearchTool.getToolSchema();
        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");
        assertEquals("object", inputSchema.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) inputSchema.get("properties");
        assertTrue(props.containsKey("query"));
        assertTrue(props.containsKey("top_k"));
        assertTrue(props.containsKey("vector_weight"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) inputSchema.get("required");
        assertTrue(required.contains("query"));
    }

    private static MemoryChunk chunk(String content, String source) {
        return new MemoryChunk("id-" + content.hashCode(), content, source, 0, 0,
                Integer.toHexString(content.hashCode()), Instant.now(), MemoryType.DURABLE);
    }

    private static class StubFileMemoryManager extends FileMemoryManager {
        private List<SearchResult> results = List.of();
        SearchOptions lastOptions;

        StubFileMemoryManager() {
            super(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"),
                            "test-memory-" + System.nanoTime()),
                    "test-agent",
                    new MockEmbeddingProvider());
        }

        void setResults(List<SearchResult> results) {
            this.results = results;
        }

        @Override
        public List<SearchResult> search(String query, SearchOptions options) {
            this.lastOptions = options;
            return results;
        }
    }
}
