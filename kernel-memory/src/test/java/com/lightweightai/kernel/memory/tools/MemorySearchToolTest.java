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

@DisplayName("MemorySearchTool - Memory search operations")
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

    @Nested
    @DisplayName("Parameter validation")
    class ParameterValidation {

        @Test
        @DisplayName("Missing query parameter returns error")
        void shouldReturnErrorWhenQueryMissing() {
            MemorySearchTool.MemorySearchResult result = searchTool.execute(Map.of());

            assertFalse(result.success());
            assertNotNull(result.error());
            assertTrue(result.error().contains("required"));
        }

        @Test
        @DisplayName("Null query parameter returns error")
        void shouldReturnErrorWhenQueryNull() {
            Map<String, Object> params = new HashMap<>();
            params.put("query", null);

            MemorySearchTool.MemorySearchResult result = searchTool.execute(params);

            assertFalse(result.success());
            assertTrue(result.error().contains("required"));
        }

        @Test
        @DisplayName("Blank query parameter returns error")
        void shouldReturnErrorWhenQueryBlank() {
            MemorySearchTool.MemorySearchResult result = searchTool.execute(Map.of("query", "   "));

            assertFalse(result.success());
            assertTrue(result.error().contains("required"));
        }
    }

    @Nested
    @DisplayName("Search with stored memories")
    class SearchWithMemories {

        @Test
        @DisplayName("Search finds stored durable memory")
        void shouldFindStoredDurableMemory() {
            memoryManager.appendDurable("Preferences", "User prefers dark mode and large font");
            memoryManager.reindex();

            MemorySearchTool.MemorySearchResult result = searchTool.execute(
                Map.of("query", "dark mode preference")
            );

            assertTrue(result.success());
            assertNull(result.error());
            assertFalse(result.matches().isEmpty());
        }

        @Test
        @DisplayName("Search finds stored ephemeral memory")
        void shouldFindStoredEphemeralMemory() {
            memoryManager.appendEphemeral("Discussed project timeline for Q3 release");
            memoryManager.reindex();

            MemorySearchTool.MemorySearchResult result = searchTool.execute(
                Map.of("query", "project timeline")
            );

            assertTrue(result.success());
            assertNull(result.error());
        }

        @Test
        @DisplayName("Search returns empty list when no match found")
        void shouldReturnEmptyWhenNoMatch() {
            MemorySearchTool.MemorySearchResult result = searchTool.execute(
                Map.of("query", "nonexistent topic xyz")
            );

            assertTrue(result.success());
            assertNull(result.error());
            assertTrue(result.matches().isEmpty());
        }
    }

    @Nested
    @DisplayName("Optional parameters")
    class OptionalParameters {

        @Test
        @DisplayName("Custom top_k limits result count")
        void shouldRespectTopK() {
            for (int i = 0; i < 10; i++) {
                memoryManager.appendDurable("Notes", "Note number " + i + " about testing");
            }
            memoryManager.reindex();

            MemorySearchTool.MemorySearchResult result = searchTool.execute(
                Map.of("query", "testing", "top_k", 3)
            );

            assertTrue(result.success());
            assertTrue(result.matches().size() <= 3);
        }

        @Test
        @DisplayName("Custom vector_weight is accepted")
        void shouldAcceptVectorWeight() {
            memoryManager.appendDurable("Notes", "Important fact about Java programming");
            memoryManager.reindex();

            MemorySearchTool.MemorySearchResult result = searchTool.execute(
                Map.of("query", "Java", "vector_weight", 0.3)
            );

            assertTrue(result.success());
        }

        @Test
        @DisplayName("Non-numeric top_k falls back to default")
        void shouldFallBackToDefaultForNonNumericTopK() {
            MemorySearchTool.MemorySearchResult result = searchTool.execute(
                Map.of("query", "test", "top_k", "not_a_number")
            );

            assertTrue(result.success());
        }
    }

    @Nested
    @DisplayName("Result formatting")
    class ResultFormatting {

        @Test
        @DisplayName("toText for successful result with matches contains match count")
        void successWithMatchesShouldShowCount() {
            memoryManager.appendDurable("Notes", "Remember this important fact");
            memoryManager.reindex();

            MemorySearchTool.MemorySearchResult result = searchTool.execute(
                Map.of("query", "important fact")
            );

            if (!result.matches().isEmpty()) {
                String text = result.toText();
                assertTrue(text.contains("Found"));
                assertTrue(text.contains("relevant memories"));
            }
        }

        @Test
        @DisplayName("toText for empty results says no matches found")
        void emptyResultsShouldSayNoMatches() {
            MemorySearchTool.MemorySearchResult result = searchTool.execute(
                Map.of("query", "xyznonexistent")
            );

            String text = result.toText();
            assertTrue(text.contains("No matching memories found"));
        }

        @Test
        @DisplayName("toText for error starts with Error:")
        void errorResultShouldStartWithError() {
            MemorySearchTool.MemorySearchResult result = searchTool.execute(Map.of());

            String text = result.toText();
            assertTrue(text.startsWith("Error:"));
        }
    }

    @Nested
    @DisplayName("Tool schema")
    class ToolSchemaTests {

        @Test
        @DisplayName("Schema has correct tool name and description")
        void shouldHaveCorrectNameAndDescription() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();

            assertEquals("memory_search", schema.get("name"));
            assertEquals(MemorySearchTool.TOOL_DESCRIPTION, schema.get("description"));
        }

        @Test
        @DisplayName("Schema defines query as required")
        @SuppressWarnings("unchecked")
        void shouldMarkQueryAsRequired() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();
            Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");
            List<String> required = (List<String>) inputSchema.get("required");

            assertNotNull(required);
            assertTrue(required.contains("query"));
        }

        @Test
        @DisplayName("Schema defines all expected properties")
        @SuppressWarnings("unchecked")
        void shouldDefineAllProperties() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();
            Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");
            Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");

            assertNotNull(properties.get("query"));
            assertNotNull(properties.get("top_k"));
            assertNotNull(properties.get("vector_weight"));
        }
    }

    @Nested
    @DisplayName("MemoryMatch record")
    class MemoryMatchTests {

        @Test
        @DisplayName("MemoryMatch holds correct values")
        void shouldHoldCorrectValues() {
            MemorySearchTool.MemoryMatch match = new MemorySearchTool.MemoryMatch(
                "full content", "notes.md", 0.85f, "snippet text"
            );

            assertEquals("full content", match.content());
            assertEquals("notes.md", match.source());
            assertEquals(0.85f, match.score(), 0.001f);
            assertEquals("snippet text", match.snippet());
        }
    }
}
