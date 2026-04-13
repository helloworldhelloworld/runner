package com.lightweightai.kernel.memory.tools;

import com.lightweightai.kernel.memory.file.FileMemoryManager;
import com.lightweightai.kernel.memory.model.SearchOptions;
import com.lightweightai.kernel.memory.model.SearchResult;
import com.lightweightai.kernel.memory.model.MemoryChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemorySearchTool - hybrid search tool for AI agents")
class MemorySearchToolTest {

    @Mock
    private FileMemoryManager memoryManager;

    private MemorySearchTool tool;

    @BeforeEach
    void setUp() {
        tool = new MemorySearchTool(memoryManager);
    }

    // ==================== execute ====================

    @Test
    @DisplayName("execute with valid query returns search results")
    void executeWithValidQuery() {
        MemoryChunk chunk = MemoryChunk.builder()
            .content("User likes coffee")
            .sourceFile("session-001.md")
            .build();
        SearchResult searchResult = SearchResult.builder()
            .chunk(chunk)
            .score(0.85f)
            .snippet("User likes coffee in the morning")
            .build();
        when(memoryManager.search(eq("coffee"), any(SearchOptions.class)))
            .thenReturn(List.of(searchResult));

        MemorySearchTool.MemorySearchResult result = tool.execute(Map.of("query", "coffee"));

        assertTrue(result.success());
        assertNull(result.error());
        assertEquals(1, result.matches().size());
        assertEquals("User likes coffee", result.matches().get(0).content());
        assertEquals("session-001.md", result.matches().get(0).source());
        assertEquals(0.85f, result.matches().get(0).score(), 0.001f);
    }

    @Test
    @DisplayName("execute with null query returns error")
    void executeWithNullQueryReturnsError() {
        MemorySearchTool.MemorySearchResult result = tool.execute(Map.of());

        assertFalse(result.success());
        assertNotNull(result.error());
        assertTrue(result.error().contains("required"));
    }

    @Test
    @DisplayName("execute with blank query returns error")
    void executeWithBlankQueryReturnsError() {
        MemorySearchTool.MemorySearchResult result = tool.execute(Map.of("query", "   "));

        assertFalse(result.success());
    }

    @Test
    @DisplayName("execute passes top_k parameter to search options")
    void executePassesTopK() {
        when(memoryManager.search(anyString(), any(SearchOptions.class)))
            .thenReturn(List.of());

        tool.execute(Map.of("query", "test", "top_k", 10));

        ArgumentCaptor<SearchOptions> captor = ArgumentCaptor.forClass(SearchOptions.class);
        verify(memoryManager).search(eq("test"), captor.capture());
        assertEquals(10, captor.getValue().getTopK());
    }

    @Test
    @DisplayName("execute passes vector_weight parameter")
    void executePassesVectorWeight() {
        when(memoryManager.search(anyString(), any(SearchOptions.class)))
            .thenReturn(List.of());

        tool.execute(Map.of("query", "test", "vector_weight", 0.5));

        ArgumentCaptor<SearchOptions> captor = ArgumentCaptor.forClass(SearchOptions.class);
        verify(memoryManager).search(eq("test"), captor.capture());
        assertEquals(0.5f, captor.getValue().getVectorWeight(), 0.001f);
    }

    @Test
    @DisplayName("execute uses defaults when optional params missing")
    void executeUsesDefaults() {
        when(memoryManager.search(anyString(), any(SearchOptions.class)))
            .thenReturn(List.of());

        tool.execute(Map.of("query", "test"));

        ArgumentCaptor<SearchOptions> captor = ArgumentCaptor.forClass(SearchOptions.class);
        verify(memoryManager).search(eq("test"), captor.capture());
        assertEquals(5, captor.getValue().getTopK());
        assertEquals(0.7f, captor.getValue().getVectorWeight(), 0.001f);
    }

    // ==================== toText ====================

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
    @DisplayName("toText shows no matches message")
    void toTextShowsNoMatches() {
        MemorySearchTool.MemorySearchResult result =
            new MemorySearchTool.MemorySearchResult(true, null, List.of());

        assertEquals("No matching memories found.", result.toText());
    }

    @Test
    @DisplayName("toText formats matches with index and score")
    void toTextFormatsMatches() {
        MemorySearchTool.MemoryMatch match = new MemorySearchTool.MemoryMatch(
            "content", "file.md", 0.9f, "snippet text"
        );
        MemorySearchTool.MemorySearchResult result =
            new MemorySearchTool.MemorySearchResult(true, null, List.of(match));

        String text = result.toText();
        assertTrue(text.contains("1 relevant"));
        assertTrue(text.contains("file.md"));
        assertTrue(text.contains("0.900"));
        assertTrue(text.contains("snippet text"));
    }

    // ==================== getToolSchema ====================

    @Test
    @DisplayName("getToolSchema returns valid tool definition")
    void getToolSchemaReturnsValidDefinition() {
        Map<String, Object> schema = MemorySearchTool.getToolSchema();

        assertEquals("memory_search", schema.get("name"));
        assertNotNull(schema.get("description"));
        assertNotNull(schema.get("input_schema"));
    }

    @Test
    @DisplayName("tool name constant matches schema name")
    void toolNameMatchesSchema() {
        assertEquals("memory_search", MemorySearchTool.TOOL_NAME);
    }
}
