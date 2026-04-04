package com.lightweightai.kernel.memory.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemorySearchTool")
class MemorySearchToolTest {

    @Nested
    @DisplayName("Tool Schema")
    class SchemaTests {

        @Test
        void shouldHaveCorrectToolName() {
            assertEquals("memory_search", MemorySearchTool.TOOL_NAME);
        }

        @Test
        void shouldHaveDescription() {
            assertNotNull(MemorySearchTool.TOOL_DESCRIPTION);
            assertFalse(MemorySearchTool.TOOL_DESCRIPTION.isEmpty());
        }

        @Test
        @SuppressWarnings("unchecked")
        void shouldGenerateValidSchema() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();

            assertEquals("memory_search", schema.get("name"));
            assertNotNull(schema.get("description"));
            assertNotNull(schema.get("input_schema"));

            Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");
            assertEquals("object", inputSchema.get("type"));
            assertNotNull(inputSchema.get("properties"));

            Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
            assertTrue(properties.containsKey("query"));
            assertTrue(properties.containsKey("top_k"));
            assertTrue(properties.containsKey("vector_weight"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void shouldRequireQueryParameter() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();
            Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");
            List<String> required = (List<String>) inputSchema.get("required");

            assertTrue(required.contains("query"));
        }
    }

    @Nested
    @DisplayName("MemorySearchResult")
    class ResultTests {

        @Test
        void shouldFormatErrorResult() {
            MemorySearchTool.MemorySearchResult result =
                    new MemorySearchTool.MemorySearchResult(false, "Query parameter is required", List.of());

            assertFalse(result.success());
            assertEquals("Query parameter is required", result.error());
            assertTrue(result.toText().contains("Error"));
        }

        @Test
        void shouldFormatEmptyResult() {
            MemorySearchTool.MemorySearchResult result =
                    new MemorySearchTool.MemorySearchResult(true, null, List.of());

            assertTrue(result.success());
            assertTrue(result.toText().contains("No matching memories"));
        }

        @Test
        void shouldFormatMatchResults() {
            List<MemorySearchTool.MemoryMatch> matches = List.of(
                    new MemorySearchTool.MemoryMatch("content1", "file1.md", 0.95f, "snippet1"),
                    new MemorySearchTool.MemoryMatch("content2", "file2.md", 0.80f, "snippet2")
            );
            MemorySearchTool.MemorySearchResult result =
                    new MemorySearchTool.MemorySearchResult(true, null, matches);

            assertTrue(result.success());
            String text = result.toText();
            assertTrue(text.contains("Found 2 relevant memories"));
            assertTrue(text.contains("file1.md"));
            assertTrue(text.contains("snippet1"));
            assertTrue(text.contains("0.950"));
        }
    }

    @Nested
    @DisplayName("MemoryMatch")
    class MatchTests {

        @Test
        void shouldStoreAllFields() {
            MemorySearchTool.MemoryMatch match =
                    new MemorySearchTool.MemoryMatch("full content", "source.md", 0.85f, "snippet text");

            assertEquals("full content", match.content());
            assertEquals("source.md", match.source());
            assertEquals(0.85f, match.score(), 0.01f);
            assertEquals("snippet text", match.snippet());
        }
    }
}
