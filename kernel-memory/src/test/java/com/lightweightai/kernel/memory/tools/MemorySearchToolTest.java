package com.lightweightai.kernel.memory.tools;

import com.lightweightai.kernel.memory.embedding.MockEmbeddingProvider;
import com.lightweightai.kernel.memory.file.FileMemoryManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemorySearchTool — search execution and result formatting")
class MemorySearchToolTest {

    @TempDir
    Path tempDir;

    private MemorySearchTool tool;
    private FileMemoryManager memoryManager;

    @BeforeEach
    void setUp() {
        memoryManager = new FileMemoryManager(tempDir, "test-agent", new MockEmbeddingProvider());
        tool = new MemorySearchTool(memoryManager);
    }

    @Test
    @DisplayName("missing query returns error result")
    void missingQueryReturnsError() {
        MemorySearchTool.MemorySearchResult result = tool.execute(Map.of());

        assertFalse(result.success());
        assertEquals("Query parameter is required", result.error());
        assertTrue(result.matches().isEmpty());
    }

    @Test
    @DisplayName("blank query returns error result")
    void blankQueryReturnsError() {
        MemorySearchTool.MemorySearchResult result = tool.execute(Map.of("query", "   "));

        assertFalse(result.success());
        assertEquals("Query parameter is required", result.error());
    }

    @Test
    @DisplayName("valid query on empty memory returns success with no matches")
    void emptyMemoryReturnsNoMatches() {
        MemorySearchTool.MemorySearchResult result = tool.execute(Map.of("query", "anything"));

        assertTrue(result.success());
        assertNull(result.error());
        assertTrue(result.matches().isEmpty());
    }

    @Test
    @DisplayName("search finds content written to durable memory")
    void searchFindsDurableContent() {
        memoryManager.writeDurable("# User Preferences\n\nThe user likes dark theme and Java programming.");

        MemorySearchTool.MemorySearchResult result = tool.execute(Map.of("query", "dark theme"));

        assertTrue(result.success());
        assertFalse(result.matches().isEmpty(), "should find at least one match for indexed content");
    }

    @Test
    @DisplayName("search finds content in ephemeral memory")
    void searchFindsEphemeralContent() {
        memoryManager.appendEphemeral("Today the user mentioned they have a meeting at 3pm.");

        MemorySearchTool.MemorySearchResult result = tool.execute(Map.of("query", "meeting"));

        assertTrue(result.success());
    }

    @Test
    @DisplayName("custom top_k limits results")
    void customTopKLimitsResults() {
        for (int i = 0; i < 10; i++) {
            memoryManager.appendEphemeral("Important memory entry number " + i + " about testing");
        }

        MemorySearchTool.MemorySearchResult result = tool.execute(
                Map.of("query", "important memory testing", "top_k", 3));

        assertTrue(result.success());
        assertTrue(result.matches().size() <= 3,
                "should return at most top_k results, got " + result.matches().size());
    }

    // ==================== Result Formatting ====================

    @Test
    @DisplayName("toText() formats error result")
    void toTextFormatsError() {
        MemorySearchTool.MemorySearchResult result =
                new MemorySearchTool.MemorySearchResult(false, "bad query", List.of());

        assertEquals("Error: bad query", result.toText());
    }

    @Test
    @DisplayName("toText() formats empty results")
    void toTextFormatsEmpty() {
        MemorySearchTool.MemorySearchResult result =
                new MemorySearchTool.MemorySearchResult(true, null, List.of());

        assertEquals("No matching memories found.", result.toText());
    }

    @Test
    @DisplayName("toText() formats matches with scores and snippets")
    void toTextFormatsMatches() {
        var matches = List.of(
                new MemorySearchTool.MemoryMatch("content", "file.md", 0.9f, "relevant text")
        );
        MemorySearchTool.MemorySearchResult result =
                new MemorySearchTool.MemorySearchResult(true, null, matches);

        String text = result.toText();
        assertTrue(text.contains("Found 1 relevant memories"));
        assertTrue(text.contains("file.md"));
        assertTrue(text.contains("0.900"));
        assertTrue(text.contains("relevant text"));
    }

    // ==================== Schema ====================

    @Test
    @DisplayName("getToolSchema() returns valid schema with required query field")
    void toolSchemaValid() {
        Map<String, Object> schema = MemorySearchTool.getToolSchema();

        assertEquals("memory_search", schema.get("name"));
        assertNotNull(schema.get("description"));

        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");
        assertNotNull(inputSchema);
        assertEquals("object", inputSchema.get("type"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) inputSchema.get("required");
        assertTrue(required.contains("query"));
    }

    @Test
    @DisplayName("TOOL_NAME constant is memory_search")
    void toolNameConstant() {
        assertEquals("memory_search", MemorySearchTool.TOOL_NAME);
    }
}
