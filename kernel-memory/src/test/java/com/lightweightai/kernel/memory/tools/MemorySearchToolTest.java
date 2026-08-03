package com.lightweightai.kernel.memory.tools;

import com.lightweightai.kernel.memory.embedding.MockEmbeddingProvider;
import com.lightweightai.kernel.memory.file.FileMemoryManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemorySearchTool")
class MemorySearchToolTest {

    @TempDir
    Path tempDir;

    private FileMemoryManager memoryManager;
    private MemorySearchTool searchTool;

    @BeforeEach
    void setUp() {
        Path agentRoot = tempDir.resolve("search-test-agent");
        MockEmbeddingProvider embeddingProvider = new MockEmbeddingProvider(64);
        memoryManager = new FileMemoryManager(agentRoot, "search-test-agent", embeddingProvider);
        searchTool = new MemorySearchTool(memoryManager);
    }

    @AfterEach
    void tearDown() {
        if (memoryManager != null) {
            memoryManager.close();
        }
    }

    @Nested
    @DisplayName("execute — validation")
    class ValidationTests {

        @Test
        @DisplayName("returns error when query is missing")
        void missingQuery() {
            MemorySearchTool.MemorySearchResult result = searchTool.execute(Map.of());

            assertFalse(result.success());
            assertNotNull(result.error());
            assertTrue(result.error().contains("required"));
            assertTrue(result.matches().isEmpty());
        }

        @Test
        @DisplayName("returns error when query is empty string")
        void emptyQuery() {
            MemorySearchTool.MemorySearchResult result = searchTool.execute(Map.of("query", ""));

            assertFalse(result.success());
            assertTrue(result.error().contains("required"));
        }

        @Test
        @DisplayName("returns error when query is blank (whitespace only)")
        void blankQuery() {
            MemorySearchTool.MemorySearchResult result = searchTool.execute(Map.of("query", "   "));

            assertFalse(result.success());
            assertTrue(result.error().contains("required"));
        }

        @Test
        @DisplayName("returns error when query is null")
        void nullQuery() {
            Map<String, Object> params = new HashMap<>();
            params.put("query", null);
            MemorySearchTool.MemorySearchResult result = searchTool.execute(params);

            assertFalse(result.success());
            assertTrue(result.error().contains("required"));
        }
    }

    @Nested
    @DisplayName("execute — search behavior")
    class SearchBehaviorTests {

        @Test
        @DisplayName("returns success with empty matches when no content stored")
        void noContent() {
            MemorySearchTool.MemorySearchResult result = searchTool.execute(Map.of(
                    "query", "anything"
            ));

            assertTrue(result.success());
            assertNull(result.error());
            assertTrue(result.matches().isEmpty());
        }

        @Test
        @DisplayName("finds stored durable content")
        void findsDurableContent() {
            memoryManager.writeDurable("Kubernetes orchestrates containerized applications");

            MemorySearchTool.MemorySearchResult result = searchTool.execute(Map.of(
                    "query", "Kubernetes containers"
            ));

            assertTrue(result.success());
            assertFalse(result.matches().isEmpty());
            MemorySearchTool.MemoryMatch firstMatch = result.matches().get(0);
            assertTrue(firstMatch.content().contains("Kubernetes"));
            assertNotNull(firstMatch.source());
            assertTrue(firstMatch.score() >= 0.0f);
        }

        @Test
        @DisplayName("respects top_k parameter")
        void respectsTopK() {
            memoryManager.writeDurable(
                    "Topic A content\n\nTopic B content\n\nTopic C content\n\nTopic D content");

            MemorySearchTool.MemorySearchResult result = searchTool.execute(Map.of(
                    "query", "topic content",
                    "top_k", 2
            ));

            assertTrue(result.success());
            assertTrue(result.matches().size() <= 2);
        }

        @Test
        @DisplayName("uses default top_k when not specified")
        void defaultTopK() {
            memoryManager.writeDurable("Some searchable content about programming");

            MemorySearchTool.MemorySearchResult result = searchTool.execute(Map.of(
                    "query", "programming"
            ));

            // Should succeed without top_k specified (defaults to 5)
            assertTrue(result.success());
        }

        @Test
        @DisplayName("accepts vector_weight parameter as Number")
        void vectorWeightParam() {
            memoryManager.writeDurable("Content for vector weight test");

            MemorySearchTool.MemorySearchResult result = searchTool.execute(Map.of(
                    "query", "vector weight",
                    "vector_weight", 0.3
            ));

            assertTrue(result.success());
        }

        @Test
        @DisplayName("ignores non-Number top_k and uses default")
        void nonNumericTopK() {
            memoryManager.writeDurable("Content for type coercion test");

            MemorySearchTool.MemorySearchResult result = searchTool.execute(Map.of(
                    "query", "type coercion",
                    "top_k", "not-a-number"
            ));

            // Should succeed with default top_k, not throw
            assertTrue(result.success());
        }

        @Test
        @DisplayName("ignores non-Number vector_weight and uses default")
        void nonNumericVectorWeight() {
            memoryManager.writeDurable("Content for weight test");

            MemorySearchTool.MemorySearchResult result = searchTool.execute(Map.of(
                    "query", "weight test",
                    "vector_weight", "invalid"
            ));

            assertTrue(result.success());
        }

        @Test
        @DisplayName("MemoryMatch record carries content, source, score, snippet")
        void matchRecordFields() {
            memoryManager.writeDurable("Detailed info about machine learning algorithms");

            MemorySearchTool.MemorySearchResult result = searchTool.execute(Map.of(
                    "query", "machine learning"
            ));

            assertTrue(result.success());
            if (!result.matches().isEmpty()) {
                MemorySearchTool.MemoryMatch match = result.matches().get(0);
                assertNotNull(match.content(), "match content should not be null");
                assertNotNull(match.source(), "match source should not be null");
                // score is a float, just verify it's non-negative
                assertTrue(match.score() >= 0.0f, "score should be non-negative");
            }
        }
    }

    @Nested
    @DisplayName("MemorySearchResult.toText()")
    class ToTextTests {

        @Test
        @DisplayName("error result produces Error: prefix")
        void errorToText() {
            MemorySearchTool.MemorySearchResult result =
                    new MemorySearchTool.MemorySearchResult(false, "something went wrong", List.of());

            String text = result.toText();
            assertEquals("Error: something went wrong", text);
        }

        @Test
        @DisplayName("empty matches produces no-results message")
        void emptyMatchesToText() {
            MemorySearchTool.MemorySearchResult result =
                    new MemorySearchTool.MemorySearchResult(true, null, List.of());

            String text = result.toText();
            assertEquals("No matching memories found.", text);
        }

        @Test
        @DisplayName("results produce numbered list with source, score, and snippet")
        void resultsToText() {
            List<MemorySearchTool.MemoryMatch> matches = List.of(
                    new MemorySearchTool.MemoryMatch("content1", "file1.md", 0.95f, "snippet one"),
                    new MemorySearchTool.MemoryMatch("content2", "file2.md", 0.80f, "snippet two")
            );
            MemorySearchTool.MemorySearchResult result =
                    new MemorySearchTool.MemorySearchResult(true, null, matches);

            String text = result.toText();
            assertTrue(text.contains("Found 2 relevant memories"));
            assertTrue(text.contains("1. [file1.md]"));
            assertTrue(text.contains("0.950"));
            assertTrue(text.contains("snippet one"));
            assertTrue(text.contains("2. [file2.md]"));
            assertTrue(text.contains("0.800"));
            assertTrue(text.contains("snippet two"));
        }
    }

    @Nested
    @DisplayName("getToolSchema()")
    class SchemaTests {

        @Test
        @DisplayName("schema contains required name, description, and input_schema")
        void schemaStructure() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();

            assertEquals("memory_search", schema.get("name"));
            assertNotNull(schema.get("description"));
            assertTrue(((String) schema.get("description")).length() > 10,
                    "description should be meaningful, not empty");

            @SuppressWarnings("unchecked")
            Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");
            assertNotNull(inputSchema);
            assertEquals("object", inputSchema.get("type"));
        }

        @Test
        @DisplayName("input_schema declares query, top_k, and vector_weight properties")
        void schemaProperties() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();

            @SuppressWarnings("unchecked")
            Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");

            assertNotNull(properties.get("query"));
            assertNotNull(properties.get("top_k"));
            assertNotNull(properties.get("vector_weight"));
        }

        @Test
        @DisplayName("input_schema marks query as required")
        void queryIsRequired() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();

            @SuppressWarnings("unchecked")
            Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");
            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) inputSchema.get("required");

            assertTrue(required.contains("query"));
        }
    }

    @Nested
    @DisplayName("Constants")
    class ConstantsTests {

        @Test
        @DisplayName("TOOL_NAME is memory_search")
        void toolName() {
            assertEquals("memory_search", MemorySearchTool.TOOL_NAME);
        }

        @Test
        @DisplayName("TOOL_DESCRIPTION is non-empty")
        void toolDescription() {
            assertNotNull(MemorySearchTool.TOOL_DESCRIPTION);
            assertFalse(MemorySearchTool.TOOL_DESCRIPTION.isEmpty());
        }
    }
}
