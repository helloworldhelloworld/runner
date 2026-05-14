package com.lightweightai.kernel.memory.tools;

import com.lightweightai.kernel.memory.embedding.MockEmbeddingProvider;
import com.lightweightai.kernel.memory.file.FileMemoryManager;
import com.lightweightai.kernel.memory.tools.MemorySearchTool.MemoryMatch;
import com.lightweightai.kernel.memory.tools.MemorySearchTool.MemorySearchResult;
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

@DisplayName("MemorySearchTool - Memory search operations")
class MemorySearchToolTest {

    // ==================== getToolSchema() ====================

    @Nested
    @DisplayName("getToolSchema")
    class GetToolSchemaTests {

        @Test
        @DisplayName("Schema has required top-level fields: name, description, input_schema")
        void schemaHasRequiredTopLevelFields() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();

            assertEquals(MemorySearchTool.TOOL_NAME, schema.get("name"));
            assertEquals(MemorySearchTool.TOOL_DESCRIPTION, schema.get("description"));
            assertNotNull(schema.get("input_schema"), "input_schema must be present");
        }

        @Test
        @DisplayName("Schema name is 'memory_search'")
        void schemaNameIsMemorySearch() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();

            assertEquals("memory_search", schema.get("name"));
        }

        @Test
        @DisplayName("input_schema type is 'object'")
        @SuppressWarnings("unchecked")
        void inputSchemaTypeIsObject() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();
            Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");

            assertEquals("object", inputSchema.get("type"));
        }

        @Test
        @DisplayName("input_schema defines query, top_k, and vector_weight properties")
        @SuppressWarnings("unchecked")
        void inputSchemaDefinesExpectedProperties() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();
            Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");
            Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");

            assertNotNull(properties.get("query"), "query property must be defined");
            assertNotNull(properties.get("top_k"), "top_k property must be defined");
            assertNotNull(properties.get("vector_weight"), "vector_weight property must be defined");
        }

        @Test
        @DisplayName("query property has type 'string'")
        @SuppressWarnings("unchecked")
        void queryPropertyHasTypeString() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();
            Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");
            Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
            Map<String, Object> queryProp = (Map<String, Object>) properties.get("query");

            assertEquals("string", queryProp.get("type"));
        }

        @Test
        @DisplayName("top_k property has type 'integer'")
        @SuppressWarnings("unchecked")
        void topKPropertyHasTypeInteger() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();
            Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");
            Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
            Map<String, Object> topKProp = (Map<String, Object>) properties.get("top_k");

            assertEquals("integer", topKProp.get("type"));
        }

        @Test
        @DisplayName("vector_weight property has type 'number'")
        @SuppressWarnings("unchecked")
        void vectorWeightPropertyHasTypeNumber() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();
            Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");
            Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
            Map<String, Object> vectorWeightProp = (Map<String, Object>) properties.get("vector_weight");

            assertEquals("number", vectorWeightProp.get("type"));
        }

        @Test
        @DisplayName("query is marked as required")
        @SuppressWarnings("unchecked")
        void queryIsRequired() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();
            Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");
            List<String> required = (List<String>) inputSchema.get("required");

            assertNotNull(required, "required list must be present");
            assertTrue(required.contains("query"), "query must be in required list");
        }

        @Test
        @DisplayName("Each property has a description")
        @SuppressWarnings("unchecked")
        void eachPropertyHasDescription() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();
            Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");
            Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");

            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                Map<String, Object> prop = (Map<String, Object>) entry.getValue();
                assertNotNull(prop.get("description"),
                        "Property '" + entry.getKey() + "' must have a description");
                assertFalse(((String) prop.get("description")).isBlank(),
                        "Property '" + entry.getKey() + "' description must not be blank");
            }
        }
    }

    // ==================== MemorySearchResult.toText() ====================

    @Nested
    @DisplayName("MemorySearchResult.toText")
    class MemorySearchResultToTextTests {

        @Test
        @DisplayName("Error result returns 'Error: <message>'")
        void errorResultReturnsErrorMessage() {
            MemorySearchResult result = new MemorySearchResult(
                    false, "Query parameter is required", List.of());

            String text = result.toText();

            assertEquals("Error: Query parameter is required", text);
        }

        @Test
        @DisplayName("Empty matches returns no-matching message")
        void emptyMatchesReturnsNoMatchingMessage() {
            MemorySearchResult result = new MemorySearchResult(
                    true, null, List.of());

            String text = result.toText();

            assertEquals("No matching memories found.", text);
        }

        @Test
        @DisplayName("Success with matches includes count and formatted entries")
        void successWithMatchesFormatsCorrectly() {
            List<MemoryMatch> matches = List.of(
                    new MemoryMatch("content1", "source1.md", 0.95f, "snippet one"),
                    new MemoryMatch("content2", "source2.md", 0.82f, "snippet two")
            );
            MemorySearchResult result = new MemorySearchResult(true, null, matches);

            String text = result.toText();

            assertTrue(text.startsWith("Found 2 relevant memories:"),
                    "Should start with count header, got: " + text);
            assertTrue(text.contains("1. [source1.md] (score: 0.950)"),
                    "Should contain first entry formatted");
            assertTrue(text.contains("snippet one"),
                    "Should contain first snippet");
            assertTrue(text.contains("2. [source2.md] (score: 0.820)"),
                    "Should contain second entry formatted");
            assertTrue(text.contains("snippet two"),
                    "Should contain second snippet");
        }

        @Test
        @DisplayName("Single match shows 'Found 1 relevant memories'")
        void singleMatchShowsCorrectCount() {
            List<MemoryMatch> matches = List.of(
                    new MemoryMatch("content", "file.md", 0.9f, "a snippet")
            );
            MemorySearchResult result = new MemorySearchResult(true, null, matches);

            String text = result.toText();

            assertTrue(text.contains("Found 1 relevant memories:"));
            assertTrue(text.contains("1. [file.md] (score: 0.900)"));
            assertTrue(text.contains("a snippet"));
        }
    }

    // ==================== MemoryMatch record ====================

    @Nested
    @DisplayName("MemoryMatch record")
    class MemoryMatchTests {

        @Test
        @DisplayName("MemoryMatch stores and returns all fields")
        void memoryMatchStoresAllFields() {
            MemoryMatch match = new MemoryMatch(
                    "the content", "notes.md", 0.75f, "a snippet");

            assertEquals("the content", match.content());
            assertEquals("notes.md", match.source());
            assertEquals(0.75f, match.score(), 0.001f);
            assertEquals("a snippet", match.snippet());
        }

        @Test
        @DisplayName("MemoryMatch equality based on all fields")
        void memoryMatchEquality() {
            MemoryMatch match1 = new MemoryMatch("c", "s", 0.5f, "snip");
            MemoryMatch match2 = new MemoryMatch("c", "s", 0.5f, "snip");

            assertEquals(match1, match2);
            assertEquals(match1.hashCode(), match2.hashCode());
        }

        @Test
        @DisplayName("MemoryMatch inequality for different content")
        void memoryMatchInequalityDifferentContent() {
            MemoryMatch match1 = new MemoryMatch("c1", "s", 0.5f, "snip");
            MemoryMatch match2 = new MemoryMatch("c2", "s", 0.5f, "snip");

            assertNotEquals(match1, match2);
        }
    }

    // ==================== MemorySearchResult record ====================

    @Nested
    @DisplayName("MemorySearchResult record")
    class MemorySearchResultRecordTests {

        @Test
        @DisplayName("Success result has correct fields")
        void successResultHasCorrectFields() {
            List<MemoryMatch> matches = List.of(
                    new MemoryMatch("c", "s", 0.9f, "snip"));
            MemorySearchResult result = new MemorySearchResult(true, null, matches);

            assertTrue(result.success());
            assertNull(result.error());
            assertEquals(1, result.matches().size());
        }

        @Test
        @DisplayName("Error result has correct fields")
        void errorResultHasCorrectFields() {
            MemorySearchResult result = new MemorySearchResult(
                    false, "something went wrong", List.of());

            assertFalse(result.success());
            assertEquals("something went wrong", result.error());
            assertTrue(result.matches().isEmpty());
        }
    }

    // ==================== execute() with null/blank query ====================

    @Nested
    @DisplayName("execute - query validation")
    class ExecuteQueryValidationTests {

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

        @Test
        @DisplayName("Null query returns error result")
        void nullQueryReturnsError() {
            Map<String, Object> params = new HashMap<>();
            params.put("query", null);

            MemorySearchResult result = searchTool.execute(params);

            assertFalse(result.success());
            assertNotNull(result.error());
            assertTrue(result.error().toLowerCase().contains("required"),
                    "Error message should indicate query is required, got: " + result.error());
        }

        @Test
        @DisplayName("Missing query key returns error result")
        void missingQueryKeyReturnsError() {
            Map<String, Object> params = new HashMap<>();

            MemorySearchResult result = searchTool.execute(params);

            assertFalse(result.success());
            assertNotNull(result.error());
        }

        @Test
        @DisplayName("Empty query string returns error result")
        void emptyQueryReturnsError() {
            MemorySearchResult result = searchTool.execute(Map.of("query", ""));

            assertFalse(result.success());
            assertNotNull(result.error());
            assertTrue(result.error().toLowerCase().contains("required"));
        }

        @Test
        @DisplayName("Blank query string returns error result")
        void blankQueryReturnsError() {
            MemorySearchResult result = searchTool.execute(Map.of("query", "   "));

            assertFalse(result.success());
            assertNotNull(result.error());
            assertTrue(result.error().toLowerCase().contains("required"));
        }

        @Test
        @DisplayName("Null query error result toText starts with Error:")
        void nullQueryErrorToTextFormatsCorrectly() {
            Map<String, Object> params = new HashMap<>();
            params.put("query", null);

            MemorySearchResult result = searchTool.execute(params);

            assertTrue(result.toText().startsWith("Error:"),
                    "Error toText should start with 'Error:', got: " + result.toText());
        }
    }

    // ==================== execute() with valid query ====================

    @Nested
    @DisplayName("execute - valid queries with real FileMemoryManager")
    class ExecuteWithFileMemoryManagerTests {

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

        @Test
        @DisplayName("Search with no indexed content returns empty matches")
        void searchEmptyMemoryReturnsEmptyMatches() {
            MemorySearchResult result = searchTool.execute(Map.of(
                    "query", "anything at all"));

            assertTrue(result.success());
            assertTrue(result.matches().isEmpty());
        }

        @Test
        @DisplayName("Search finds indexed durable content")
        void searchFindsDurableContent() {
            memoryManager.writeDurable("Java is a statically typed programming language");

            MemorySearchResult result = searchTool.execute(Map.of(
                    "query", "Java programming language"));

            assertTrue(result.success());
            assertFalse(result.matches().isEmpty(),
                    "Should find at least one match for indexed content");
        }

        @Test
        @DisplayName("Search result matches have required fields populated")
        void searchResultMatchesHaveRequiredFields() {
            memoryManager.writeDurable("Important project notes about deadlines");

            MemorySearchResult result = searchTool.execute(Map.of(
                    "query", "project deadlines"));

            assertTrue(result.success());
            if (!result.matches().isEmpty()) {
                MemoryMatch firstMatch = result.matches().get(0);
                assertNotNull(firstMatch.content(), "content must not be null");
                assertNotNull(firstMatch.source(), "source must not be null");
                assertNotNull(firstMatch.snippet(), "snippet must not be null");
                assertTrue(firstMatch.score() >= 0f, "score must be non-negative");
            }
        }

        @Test
        @DisplayName("Search respects top_k parameter")
        void searchRespectsTopK() {
            memoryManager.writeDurable(
                    "First topic about cats.\n\n" +
                    "Second topic about dogs.\n\n" +
                    "Third topic about birds.");

            MemorySearchResult result = searchTool.execute(Map.of(
                    "query", "animals",
                    "top_k", 1));

            assertTrue(result.success());
            assertTrue(result.matches().size() <= 1,
                    "Should return at most 1 result when top_k=1");
        }

        @Test
        @DisplayName("Search accepts vector_weight parameter without error")
        void searchAcceptsVectorWeightParameter() {
            memoryManager.writeDurable("Content for vector weight test");

            MemorySearchResult result = searchTool.execute(Map.of(
                    "query", "vector weight test",
                    "vector_weight", 0.3f));

            assertTrue(result.success(),
                    "Search with custom vector_weight should succeed");
        }

        @Test
        @DisplayName("Default top_k is 5 when not specified")
        void defaultTopKIsFive() {
            // Write enough content to potentially exceed 5 results
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 20; i++) {
                sb.append("## Section ").append(i).append("\n\n");
                sb.append("Content about topic number ").append(i).append("\n\n");
            }
            memoryManager.writeDurable(sb.toString());

            MemorySearchResult result = searchTool.execute(Map.of(
                    "query", "topic"));

            assertTrue(result.success());
            assertTrue(result.matches().size() <= 5,
                    "Default top_k of 5 should limit results, got " + result.matches().size());
        }
    }

    // ==================== TOOL_NAME and TOOL_DESCRIPTION constants ====================

    @Test
    @DisplayName("TOOL_NAME constant is 'memory_search'")
    void toolNameConstant() {
        assertEquals("memory_search", MemorySearchTool.TOOL_NAME);
    }

    @Test
    @DisplayName("TOOL_DESCRIPTION is non-empty and descriptive")
    void toolDescriptionIsNonEmpty() {
        assertNotNull(MemorySearchTool.TOOL_DESCRIPTION);
        assertFalse(MemorySearchTool.TOOL_DESCRIPTION.isBlank());
        assertTrue(MemorySearchTool.TOOL_DESCRIPTION.length() > 20,
                "Description should be substantive");
    }
}
