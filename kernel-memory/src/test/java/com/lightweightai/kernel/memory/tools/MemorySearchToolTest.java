package com.lightweightai.kernel.memory.tools;

import com.lightweightai.kernel.memory.file.FileMemoryManager;
import com.lightweightai.kernel.memory.model.MemoryChunk;
import com.lightweightai.kernel.memory.model.MemoryType;
import com.lightweightai.kernel.memory.model.SearchOptions;
import com.lightweightai.kernel.memory.model.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemorySearchTool - memory search execution and result formatting")
class MemorySearchToolTest {

    @TempDir
    Path tempDir;

    private MemorySearchTool searchTool;
    private StubMemoryManager stubManager;

    @BeforeEach
    void setUp() {
        stubManager = new StubMemoryManager(tempDir);
        searchTool = new MemorySearchTool(stubManager);
    }

    @Nested
    @DisplayName("Parameter validation")
    class ParameterValidation {

        @Test
        @DisplayName("null query returns error result")
        void nullQuery() {
            var result = searchTool.execute(Map.of());

            assertFalse(result.success());
            assertNotNull(result.error());
            assertTrue(result.error().contains("required"));
        }

        @Test
        @DisplayName("blank query returns error result")
        void blankQuery() {
            var result = searchTool.execute(Map.of("query", "  "));

            assertFalse(result.success());
        }
    }

    @Nested
    @DisplayName("Successful search")
    class SuccessfulSearch {

        @Test
        @DisplayName("executes search with default parameters")
        void defaultParams() {
            stubManager.setResults(List.of(
                    searchResult("hello world", "chat.jsonl", 0.9f, "hello snippet")
            ));

            var result = searchTool.execute(Map.of("query", "hello"));

            assertTrue(result.success());
            assertNull(result.error());
            assertEquals(1, result.matches().size());
            assertEquals("hello world", result.matches().get(0).content());
            assertEquals("chat.jsonl", result.matches().get(0).source());
            assertEquals(0.9f, result.matches().get(0).score(), 0.01f);
        }

        @Test
        @DisplayName("passes custom top_k and vector_weight to search options")
        void customParams() {
            stubManager.setResults(List.of());

            searchTool.execute(Map.of(
                    "query", "test",
                    "top_k", 10,
                    "vector_weight", 0.3
            ));

            SearchOptions captured = stubManager.lastOptions;
            assertNotNull(captured);
            assertEquals(10, captured.getTopK());
            assertEquals(0.3f, captured.getVectorWeight(), 0.01f);
        }

        @Test
        @DisplayName("empty results returns success with empty matches")
        void emptyResults() {
            stubManager.setResults(List.of());

            var result = searchTool.execute(Map.of("query", "nothing"));

            assertTrue(result.success());
            assertTrue(result.matches().isEmpty());
        }
    }

    @Nested
    @DisplayName("Result formatting")
    class ResultFormatting {

        @Test
        @DisplayName("toText formats matches with numbered list")
        void toTextFormat() {
            stubManager.setResults(List.of(
                    searchResult("content1", "file1.md", 0.95f, "snippet1"),
                    searchResult("content2", "file2.md", 0.80f, "snippet2")
            ));

            var result = searchTool.execute(Map.of("query", "test"));
            String text = result.toText();

            assertTrue(text.contains("2 relevant memories"));
            assertTrue(text.contains("1."));
            assertTrue(text.contains("2."));
            assertTrue(text.contains("file1.md"));
            assertTrue(text.contains("0.950"));
        }

        @Test
        @DisplayName("toText for error result shows error message")
        void toTextError() {
            var result = searchTool.execute(Map.of());
            String text = result.toText();

            assertTrue(text.startsWith("Error:"));
        }

        @Test
        @DisplayName("toText for empty results shows no matches message")
        void toTextNoMatches() {
            stubManager.setResults(List.of());

            var result = searchTool.execute(Map.of("query", "nothing"));
            String text = result.toText();

            assertTrue(text.contains("No matching memories"));
        }
    }

    @Nested
    @DisplayName("Tool schema")
    class Schema {

        @Test
        @DisplayName("getToolSchema returns valid schema with name and properties")
        void validSchema() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();

            assertEquals("memory_search", schema.get("name"));
            assertNotNull(schema.get("description"));
            assertNotNull(schema.get("input_schema"));

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
    }

    // ==================== Test helpers ====================

    private static SearchResult searchResult(String content, String source, float score, String snippet) {
        MemoryChunk chunk = new MemoryChunk(
                "id-" + content.hashCode(), content, source,
                0, 0, "hash-" + content.hashCode(),
                Instant.now(), MemoryType.EPHEMERAL);
        return new SearchResult(chunk, score, score * 0.3f, score * 0.7f, snippet);
    }

    private static class StubMemoryManager extends FileMemoryManager {
        private List<SearchResult> results = List.of();
        SearchOptions lastOptions;

        StubMemoryManager(Path tempDir) {
            super(tempDir, "test-agent", null);
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
