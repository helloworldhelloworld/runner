package com.lightweightai.kernel.memory.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemorySearchTool - hybrid search tool for AI agents")
class MemorySearchToolTest {

    // ==================== MemorySearchResult.toText ====================

    @Test
    @DisplayName("toText shows error for failed result")
    void toTextShowsError() {
        MemorySearchTool.MemorySearchResult result =
            new MemorySearchTool.MemorySearchResult(false, "Query parameter is required", List.of());

        String text = result.toText();
        assertTrue(text.startsWith("Error:"));
        assertTrue(text.contains("required"));
    }

    @Test
    @DisplayName("toText shows no matches message when empty")
    void toTextShowsNoMatches() {
        MemorySearchTool.MemorySearchResult result =
            new MemorySearchTool.MemorySearchResult(true, null, List.of());

        assertEquals("No matching memories found.", result.toText());
    }

    @Test
    @DisplayName("toText formats matches with index, source, and score")
    void toTextFormatsMatches() {
        MemorySearchTool.MemoryMatch match = new MemorySearchTool.MemoryMatch(
            "User likes coffee", "session-001.md", 0.9f, "User likes coffee in the morning"
        );
        MemorySearchTool.MemorySearchResult result =
            new MemorySearchTool.MemorySearchResult(true, null, List.of(match));

        String text = result.toText();
        assertTrue(text.contains("1 relevant"));
        assertTrue(text.contains("session-001.md"));
        assertTrue(text.contains("0.900"));
        assertTrue(text.contains("User likes coffee in the morning"));
    }

    @Test
    @DisplayName("toText formats multiple matches with sequential numbering")
    void toTextFormatsMultipleMatches() {
        List<MemorySearchTool.MemoryMatch> matches = List.of(
            new MemorySearchTool.MemoryMatch("content1", "file1.md", 0.95f, "snippet1"),
            new MemorySearchTool.MemoryMatch("content2", "file2.md", 0.80f, "snippet2"),
            new MemorySearchTool.MemoryMatch("content3", "file3.md", 0.65f, "snippet3")
        );
        MemorySearchTool.MemorySearchResult result =
            new MemorySearchTool.MemorySearchResult(true, null, matches);

        String text = result.toText();
        assertTrue(text.contains("3 relevant"));
        assertTrue(text.contains("1. [file1.md]"));
        assertTrue(text.contains("2. [file2.md]"));
        assertTrue(text.contains("3. [file3.md]"));
    }

    // ==================== MemorySearchResult record ====================

    @Test
    @DisplayName("MemorySearchResult success accessor")
    void resultAccessors() {
        MemorySearchTool.MemorySearchResult result =
            new MemorySearchTool.MemorySearchResult(true, null, List.of());

        assertTrue(result.success());
        assertNull(result.error());
        assertTrue(result.matches().isEmpty());
    }

    @Test
    @DisplayName("MemorySearchResult error accessor")
    void resultErrorAccessors() {
        MemorySearchTool.MemorySearchResult result =
            new MemorySearchTool.MemorySearchResult(false, "bad query", List.of());

        assertFalse(result.success());
        assertEquals("bad query", result.error());
    }

    // ==================== MemoryMatch record ====================

    @Test
    @DisplayName("MemoryMatch stores content, source, score, and snippet")
    void matchAccessors() {
        MemorySearchTool.MemoryMatch match = new MemorySearchTool.MemoryMatch(
            "full content", "source.md", 0.85f, "snippet text"
        );

        assertEquals("full content", match.content());
        assertEquals("source.md", match.source());
        assertEquals(0.85f, match.score(), 0.001f);
        assertEquals("snippet text", match.snippet());
    }

    // ==================== getToolSchema ====================

    @Test
    @DisplayName("getToolSchema returns valid tool definition")
    void getToolSchemaReturnsValidDefinition() {
        Map<String, Object> schema = MemorySearchTool.getToolSchema();

        assertEquals("memory_search", schema.get("name"));
        assertNotNull(schema.get("description"));
        assertNotNull(schema.get("input_schema"));

        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");
        assertEquals("object", inputSchema.get("type"));
        assertNotNull(inputSchema.get("properties"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) inputSchema.get("required");
        assertTrue(required.contains("query"));
    }

    // ==================== constants ====================

    @Test
    @DisplayName("tool name constant is memory_search")
    void toolNameConstant() {
        assertEquals("memory_search", MemorySearchTool.TOOL_NAME);
    }

    @Test
    @DisplayName("tool description is non-empty")
    void toolDescriptionNonEmpty() {
        assertNotNull(MemorySearchTool.TOOL_DESCRIPTION);
        assertFalse(MemorySearchTool.TOOL_DESCRIPTION.isBlank());
    }
}
