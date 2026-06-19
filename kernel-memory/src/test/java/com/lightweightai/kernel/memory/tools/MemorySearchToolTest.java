package com.lightweightai.kernel.memory.tools;

import com.lightweightai.kernel.memory.file.FileMemoryManager;
import com.lightweightai.kernel.memory.model.MemoryChunk;
import com.lightweightai.kernel.memory.model.SearchOptions;
import com.lightweightai.kernel.memory.model.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemorySearchTool - memory search operations")
class MemorySearchToolTest {

    @Mock
    private FileMemoryManager memoryManager;

    private MemorySearchTool searchTool;

    @BeforeEach
    void setUp() {
        searchTool = new MemorySearchTool(memoryManager);
    }

    // ==================== Query validation ====================

    @Nested
    @DisplayName("Query parameter validation")
    class QueryValidation {

        @Test
        @DisplayName("Null query returns error result with success=false")
        void shouldReturnErrorWhenQueryIsNull() {
            Map<String, Object> params = new HashMap<>();
            params.put("query", null);

            MemorySearchTool.MemorySearchResult result = searchTool.execute(params);

            assertFalse(result.success());
            assertNotNull(result.error());
            assertEquals("Query parameter is required", result.error());
            assertTrue(result.matches().isEmpty());
            verifyNoInteractions(memoryManager);
        }

        @Test
        @DisplayName("Empty query returns error result")
        void shouldReturnErrorWhenQueryIsEmpty() {
            MemorySearchTool.MemorySearchResult result = searchTool.execute(Map.of("query", ""));

            assertFalse(result.success());
            assertEquals("Query parameter is required", result.error());
            assertTrue(result.matches().isEmpty());
            verifyNoInteractions(memoryManager);
        }

        @Test
        @DisplayName("Blank query returns error result")
        void shouldReturnErrorWhenQueryIsBlank() {
            MemorySearchTool.MemorySearchResult result = searchTool.execute(Map.of("query", "   "));

            assertFalse(result.success());
            assertEquals("Query parameter is required", result.error());
            verifyNoInteractions(memoryManager);
        }

        @Test
        @DisplayName("Missing query key returns error result")
        void shouldReturnErrorWhenQueryKeyMissing() {
            MemorySearchTool.MemorySearchResult result = searchTool.execute(Map.of());

            assertFalse(result.success());
            assertEquals("Query parameter is required", result.error());
            verifyNoInteractions(memoryManager);
        }
    }

    // ==================== Valid search ====================

    @Nested
    @DisplayName("Successful search operations")
    class SuccessfulSearch {

        @Test
        @DisplayName("Valid query returns mapped search results")
        void shouldReturnMappedResults() {
            MemoryChunk chunk = MemoryChunk.builder()
                .id("chunk-1")
                .content("Machine learning is a subset of AI")
                .sourceFile("notes.md")
                .hash("abc123")
                .createdAt(Instant.now())
                .build();

            SearchResult searchResult = SearchResult.builder()
                .chunk(chunk)
                .score(0.85f)
                .snippet("Machine learning is a subset...")
                .build();

            when(memoryManager.search(eq("machine learning"), any(SearchOptions.class)))
                .thenReturn(List.of(searchResult));

            MemorySearchTool.MemorySearchResult result = searchTool.execute(
                Map.of("query", "machine learning"));

            assertTrue(result.success());
            assertNull(result.error());
            assertEquals(1, result.matches().size());

            MemorySearchTool.MemoryMatch match = result.matches().get(0);
            assertEquals("Machine learning is a subset of AI", match.content());
            assertEquals("notes.md", match.source());
            assertEquals(0.85f, match.score(), 0.001f);
            assertEquals("Machine learning is a subset...", match.snippet());
        }

        @Test
        @DisplayName("Multiple results are all mapped correctly")
        void shouldMapMultipleResults() {
            MemoryChunk chunk1 = MemoryChunk.builder()
                .id("c1").content("Content 1").sourceFile("file1.md").hash("h1").build();
            MemoryChunk chunk2 = MemoryChunk.builder()
                .id("c2").content("Content 2").sourceFile("file2.md").hash("h2").build();

            SearchResult sr1 = SearchResult.builder()
                .chunk(chunk1).score(0.9f).snippet("Snippet 1").build();
            SearchResult sr2 = SearchResult.builder()
                .chunk(chunk2).score(0.7f).snippet("Snippet 2").build();

            when(memoryManager.search(eq("query"), any(SearchOptions.class)))
                .thenReturn(List.of(sr1, sr2));

            MemorySearchTool.MemorySearchResult result = searchTool.execute(
                Map.of("query", "query"));

            assertTrue(result.success());
            assertEquals(2, result.matches().size());
            assertEquals("Content 1", result.matches().get(0).content());
            assertEquals("file1.md", result.matches().get(0).source());
            assertEquals("Content 2", result.matches().get(1).content());
            assertEquals("file2.md", result.matches().get(1).source());
        }

        @Test
        @DisplayName("Empty results return success with empty matches")
        void shouldReturnEmptyMatchesWhenNoResults() {
            when(memoryManager.search(eq("unknown topic"), any(SearchOptions.class)))
                .thenReturn(List.of());

            MemorySearchTool.MemorySearchResult result = searchTool.execute(
                Map.of("query", "unknown topic"));

            assertTrue(result.success());
            assertNull(result.error());
            assertTrue(result.matches().isEmpty());
        }
    }

    // ==================== Search options ====================

    @Nested
    @DisplayName("SearchOptions parameter handling")
    class SearchOptionsHandling {

        @Test
        @DisplayName("Default top_k is 5 and vector_weight is 0.7")
        void shouldUseDefaultSearchOptions() {
            when(memoryManager.search(any(), any(SearchOptions.class)))
                .thenReturn(List.of());

            searchTool.execute(Map.of("query", "test"));

            ArgumentCaptor<SearchOptions> captor = ArgumentCaptor.forClass(SearchOptions.class);
            verify(memoryManager).search(eq("test"), captor.capture());

            SearchOptions options = captor.getValue();
            assertEquals(5, options.getTopK());
            assertEquals(0.7f, options.getVectorWeight(), 0.001f);
        }

        @Test
        @DisplayName("Custom top_k is passed to search options")
        void shouldUseCustomTopK() {
            when(memoryManager.search(any(), any(SearchOptions.class)))
                .thenReturn(List.of());

            searchTool.execute(Map.of("query", "test", "top_k", 10));

            ArgumentCaptor<SearchOptions> captor = ArgumentCaptor.forClass(SearchOptions.class);
            verify(memoryManager).search(eq("test"), captor.capture());

            assertEquals(10, captor.getValue().getTopK());
        }

        @Test
        @DisplayName("Custom vector_weight is passed to search options")
        void shouldUseCustomVectorWeight() {
            when(memoryManager.search(any(), any(SearchOptions.class)))
                .thenReturn(List.of());

            searchTool.execute(Map.of("query", "test", "vector_weight", 0.3));

            ArgumentCaptor<SearchOptions> captor = ArgumentCaptor.forClass(SearchOptions.class);
            verify(memoryManager).search(eq("test"), captor.capture());

            assertEquals(0.3f, captor.getValue().getVectorWeight(), 0.001f);
        }

        @Test
        @DisplayName("Both custom top_k and vector_weight are passed together")
        void shouldUseBothCustomOptions() {
            when(memoryManager.search(any(), any(SearchOptions.class)))
                .thenReturn(List.of());

            searchTool.execute(Map.of("query", "test", "top_k", 3, "vector_weight", 0.5));

            ArgumentCaptor<SearchOptions> captor = ArgumentCaptor.forClass(SearchOptions.class);
            verify(memoryManager).search(eq("test"), captor.capture());

            SearchOptions options = captor.getValue();
            assertEquals(3, options.getTopK());
            assertEquals(0.5f, options.getVectorWeight(), 0.001f);
        }

        @Test
        @DisplayName("Non-numeric top_k falls back to default 5")
        void shouldFallbackToDefaultForNonNumericTopK() {
            when(memoryManager.search(any(), any(SearchOptions.class)))
                .thenReturn(List.of());

            searchTool.execute(Map.of("query", "test", "top_k", "not-a-number"));

            ArgumentCaptor<SearchOptions> captor = ArgumentCaptor.forClass(SearchOptions.class);
            verify(memoryManager).search(eq("test"), captor.capture());

            assertEquals(5, captor.getValue().getTopK());
        }

        @Test
        @DisplayName("Non-numeric vector_weight falls back to default 0.7")
        void shouldFallbackToDefaultForNonNumericVectorWeight() {
            when(memoryManager.search(any(), any(SearchOptions.class)))
                .thenReturn(List.of());

            searchTool.execute(Map.of("query", "test", "vector_weight", "invalid"));

            ArgumentCaptor<SearchOptions> captor = ArgumentCaptor.forClass(SearchOptions.class);
            verify(memoryManager).search(eq("test"), captor.capture());

            assertEquals(0.7f, captor.getValue().getVectorWeight(), 0.001f);
        }
    }

    // ==================== Tool schema ====================

    @Nested
    @DisplayName("Tool schema definition")
    class ToolSchema {

        @Test
        @DisplayName("Schema has correct name and description")
        void shouldHaveCorrectNameAndDescription() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();

            assertEquals("memory_search", schema.get("name"));
            assertEquals(MemorySearchTool.TOOL_DESCRIPTION, schema.get("description"));
        }

        @Test
        @DisplayName("Schema input_schema defines query, top_k, vector_weight properties")
        @SuppressWarnings("unchecked")
        void shouldDefineCorrectProperties() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();
            Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");

            assertEquals("object", inputSchema.get("type"));

            Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
            assertNotNull(properties.get("query"));
            assertNotNull(properties.get("top_k"));
            assertNotNull(properties.get("vector_weight"));

            Map<String, Object> queryProp = (Map<String, Object>) properties.get("query");
            assertEquals("string", queryProp.get("type"));

            Map<String, Object> topKProp = (Map<String, Object>) properties.get("top_k");
            assertEquals("integer", topKProp.get("type"));

            Map<String, Object> vectorWeightProp = (Map<String, Object>) properties.get("vector_weight");
            assertEquals("number", vectorWeightProp.get("type"));
        }

        @Test
        @DisplayName("Schema marks query as required")
        @SuppressWarnings("unchecked")
        void shouldMarkQueryAsRequired() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();
            Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");
            List<String> required = (List<String>) inputSchema.get("required");

            assertNotNull(required);
            assertTrue(required.contains("query"));
            assertEquals(1, required.size());
        }
    }

    // ==================== toText formatting ====================

    @Nested
    @DisplayName("MemorySearchResult toText formatting")
    class ToTextFormatting {

        @Test
        @DisplayName("Error result toText starts with 'Error:'")
        void shouldFormatErrorResult() {
            MemorySearchTool.MemorySearchResult result =
                new MemorySearchTool.MemorySearchResult(false, "Query parameter is required", List.of());

            String text = result.toText();

            assertTrue(text.startsWith("Error:"));
            assertTrue(text.contains("Query parameter is required"));
        }

        @Test
        @DisplayName("Empty matches toText returns no-match message")
        void shouldFormatEmptyResults() {
            MemorySearchTool.MemorySearchResult result =
                new MemorySearchTool.MemorySearchResult(true, null, List.of());

            assertEquals("No matching memories found.", result.toText());
        }

        @Test
        @DisplayName("Results toText includes count, source, score, and snippet")
        void shouldFormatResultsWithDetails() {
            List<MemorySearchTool.MemoryMatch> matches = List.of(
                new MemorySearchTool.MemoryMatch("content1", "notes.md", 0.85f, "snippet one"),
                new MemorySearchTool.MemoryMatch("content2", "journal.md", 0.72f, "snippet two")
            );
            MemorySearchTool.MemorySearchResult result =
                new MemorySearchTool.MemorySearchResult(true, null, matches);

            String text = result.toText();

            assertTrue(text.contains("Found 2 relevant memories"));
            assertTrue(text.contains("1. [notes.md]"));
            assertTrue(text.contains("(score: 0.850)"));
            assertTrue(text.contains("snippet one"));
            assertTrue(text.contains("2. [journal.md]"));
            assertTrue(text.contains("(score: 0.720)"));
            assertTrue(text.contains("snippet two"));
        }
    }

    // ==================== Constants ====================

    @Test
    @DisplayName("TOOL_NAME constant is 'memory_search'")
    void shouldHaveCorrectToolName() {
        assertEquals("memory_search", MemorySearchTool.TOOL_NAME);
    }

    @Test
    @DisplayName("TOOL_DESCRIPTION is non-empty")
    void shouldHaveNonEmptyDescription() {
        assertNotNull(MemorySearchTool.TOOL_DESCRIPTION);
        assertFalse(MemorySearchTool.TOOL_DESCRIPTION.isBlank());
    }
}
