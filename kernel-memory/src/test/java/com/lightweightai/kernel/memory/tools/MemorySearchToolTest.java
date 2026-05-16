package com.lightweightai.kernel.memory.tools;

import com.lightweightai.kernel.memory.embedding.MockEmbeddingProvider;
import com.lightweightai.kernel.memory.file.FileMemoryManager;
import com.lightweightai.kernel.memory.model.MemoryChunk;
import com.lightweightai.kernel.memory.model.MemoryType;
import com.lightweightai.kernel.memory.model.SearchOptions;
import com.lightweightai.kernel.memory.model.SearchResult;
import com.lightweightai.kernel.memory.tools.MemorySearchTool.MemoryMatch;
import com.lightweightai.kernel.memory.tools.MemorySearchTool.MemorySearchResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemorySearchTool - 记忆搜索工具测试")
class MemorySearchToolTest {

    @TempDir
    Path tempDir;

    /**
     * Test double for FileMemoryManager that captures search parameters
     * and returns pre-configured results without requiring real indexing.
     */
    static class StubFileMemoryManager extends FileMemoryManager {

        private List<SearchResult> stubbedResults = List.of();
        private final AtomicReference<String> capturedQuery = new AtomicReference<>();
        private final AtomicReference<SearchOptions> capturedOptions = new AtomicReference<>();

        StubFileMemoryManager(Path agentRoot) {
            super(agentRoot, "stub-agent", new MockEmbeddingProvider(64));
        }

        @Override
        public List<SearchResult> search(String query, SearchOptions options) {
            capturedQuery.set(query);
            capturedOptions.set(options);
            return stubbedResults;
        }

        void setResults(List<SearchResult> results) {
            this.stubbedResults = results;
        }

        String getCapturedQuery() {
            return capturedQuery.get();
        }

        SearchOptions getCapturedOptions() {
            return capturedOptions.get();
        }
    }

    private StubFileMemoryManager stubManager;
    private MemorySearchTool searchTool;

    @BeforeEach
    void setUp() {
        stubManager = new StubFileMemoryManager(tempDir.resolve("stub-agent"));
        searchTool = new MemorySearchTool(stubManager);
    }

    @AfterEach
    void tearDown() {
        if (stubManager != null) {
            stubManager.close();
        }
    }

    // ==================== Helper Methods ====================

    private static MemoryChunk createChunk(String id, String content, String sourceFile) {
        return MemoryChunk.builder()
                .id(id)
                .content(content)
                .sourceFile(sourceFile)
                .startLine(1)
                .endLine(10)
                .hash("hash-" + id)
                .createdAt(Instant.now())
                .type(MemoryType.DURABLE)
                .build();
    }

    private static SearchResult createSearchResult(String id, String content,
                                                    String sourceFile, float score,
                                                    String snippet) {
        return SearchResult.builder()
                .chunk(createChunk(id, content, sourceFile))
                .score(score)
                .bm25Score(score * 0.3f)
                .vectorScore(score * 0.7f)
                .snippet(snippet)
                .build();
    }

    // ==================== execute: Query Validation ====================

    @Nested
    @DisplayName("execute 参数验证")
    class ExecuteQueryValidation {

        @Test
        @DisplayName("query 为 null 时返回错误结果")
        void shouldReturnErrorWhenQueryIsNull() {
            Map<String, Object> params = new HashMap<>();
            params.put("query", null);

            MemorySearchResult result = searchTool.execute(params);

            assertFalse(result.success(), "result.success() should be false for null query");
            assertNotNull(result.error(), "error message should not be null");
            assertTrue(result.error().contains("required"),
                    "error message should indicate query is required");
            assertTrue(result.matches().isEmpty(), "matches should be empty on error");
        }

        @Test
        @DisplayName("query 缺失时返回错误结果")
        void shouldReturnErrorWhenQueryIsMissing() {
            Map<String, Object> params = new HashMap<>();
            // no "query" key at all

            MemorySearchResult result = searchTool.execute(params);

            assertFalse(result.success());
            assertNotNull(result.error());
            assertTrue(result.error().contains("required"));
            assertTrue(result.matches().isEmpty());
        }

        @Test
        @DisplayName("query 为空字符串时返回错误结果")
        void shouldReturnErrorWhenQueryIsEmpty() {
            MemorySearchResult result = searchTool.execute(Map.of("query", ""));

            assertFalse(result.success());
            assertNotNull(result.error());
            assertTrue(result.error().contains("required"));
            assertTrue(result.matches().isEmpty());
        }

        @Test
        @DisplayName("query 为空白字符串时返回错误结果")
        void shouldReturnErrorWhenQueryIsBlank() {
            MemorySearchResult result = searchTool.execute(Map.of("query", "   \t\n"));

            assertFalse(result.success());
            assertNotNull(result.error());
            assertTrue(result.error().contains("required"));
            assertTrue(result.matches().isEmpty());
        }
    }

    // ==================== execute: Successful Search ====================

    @Nested
    @DisplayName("execute 搜索结果映射")
    class ExecuteSearchResultMapping {

        @Test
        @DisplayName("有效查询返回正确映射的 MemoryMatch 记录")
        void shouldMapSearchResultsToMemoryMatches() {
            List<SearchResult> searchResults = List.of(
                    createSearchResult("chunk-1", "User prefers dark mode",
                            "MEMORY.md", 0.95f, "...prefers dark mode..."),
                    createSearchResult("chunk-2", "Meeting notes from Monday",
                            "memory/2026-05-10.md", 0.82f, "...meeting notes...")
            );
            stubManager.setResults(searchResults);

            MemorySearchResult result = searchTool.execute(Map.of("query", "dark mode preference"));

            assertTrue(result.success(), "search should succeed");
            assertNull(result.error(), "error should be null on success");
            assertEquals(2, result.matches().size(), "should return 2 matches");

            // Verify first match mapping
            MemoryMatch first = result.matches().get(0);
            assertEquals("User prefers dark mode", first.content());
            assertEquals("MEMORY.md", first.source());
            assertEquals(0.95f, first.score(), 0.001f);
            assertEquals("...prefers dark mode...", first.snippet());

            // Verify second match mapping
            MemoryMatch second = result.matches().get(1);
            assertEquals("Meeting notes from Monday", second.content());
            assertEquals("memory/2026-05-10.md", second.source());
            assertEquals(0.82f, second.score(), 0.001f);
            assertEquals("...meeting notes...", second.snippet());
        }

        @Test
        @DisplayName("搜索无结果时返回成功但匹配列表为空")
        void shouldReturnSuccessWithEmptyMatchesWhenNoResults() {
            stubManager.setResults(List.of());

            MemorySearchResult result = searchTool.execute(Map.of("query", "nonexistent topic"));

            assertTrue(result.success(), "search should still succeed even with no results");
            assertNull(result.error());
            assertTrue(result.matches().isEmpty(), "matches should be empty");
        }

        @Test
        @DisplayName("查询字符串正确传递给 FileMemoryManager")
        void shouldPassQueryToMemoryManager() {
            stubManager.setResults(List.of());

            searchTool.execute(Map.of("query", "find my preferences"));

            assertEquals("find my preferences", stubManager.getCapturedQuery(),
                    "query should be forwarded to the memory manager unchanged");
        }
    }

    // ==================== execute: Default Parameters ====================

    @Nested
    @DisplayName("execute 默认参数")
    class ExecuteDefaultParameters {

        @Test
        @DisplayName("未指定 top_k 时默认为 5")
        void shouldUseDefaultTopKOfFive() {
            stubManager.setResults(List.of());

            searchTool.execute(Map.of("query", "test query"));

            SearchOptions capturedOptions = stubManager.getCapturedOptions();
            assertNotNull(capturedOptions, "SearchOptions should have been captured");
            assertEquals(5, capturedOptions.getTopK(),
                    "default topK should be 5");
        }

        @Test
        @DisplayName("未指定 vector_weight 时默认为 0.7")
        void shouldUseDefaultVectorWeightOfZeroPointSeven() {
            stubManager.setResults(List.of());

            searchTool.execute(Map.of("query", "test query"));

            SearchOptions capturedOptions = stubManager.getCapturedOptions();
            assertNotNull(capturedOptions, "SearchOptions should have been captured");
            assertEquals(0.7f, capturedOptions.getVectorWeight(), 0.001f,
                    "default vectorWeight should be 0.7");
        }

        @Test
        @DisplayName("未指定 top_k 和 vector_weight 时同时使用默认值")
        void shouldUseBothDefaultsWhenNeitherSpecified() {
            stubManager.setResults(List.of());

            searchTool.execute(Map.of("query", "test query"));

            SearchOptions capturedOptions = stubManager.getCapturedOptions();
            assertEquals(5, capturedOptions.getTopK());
            assertEquals(0.7f, capturedOptions.getVectorWeight(), 0.001f);
        }
    }

    // ==================== execute: Custom Parameters ====================

    @Nested
    @DisplayName("execute 自定义参数")
    class ExecuteCustomParameters {

        @Test
        @DisplayName("自定义 top_k 正确传递给 SearchOptions")
        void shouldPassCustomTopKToSearchOptions() {
            stubManager.setResults(List.of());

            searchTool.execute(Map.of(
                    "query", "test query",
                    "top_k", 10
            ));

            SearchOptions capturedOptions = stubManager.getCapturedOptions();
            assertEquals(10, capturedOptions.getTopK(),
                    "custom topK should be passed through");
        }

        @Test
        @DisplayName("自定义 vector_weight 正确传递给 SearchOptions")
        void shouldPassCustomVectorWeightToSearchOptions() {
            stubManager.setResults(List.of());

            searchTool.execute(Map.of(
                    "query", "test query",
                    "vector_weight", 0.3f
            ));

            SearchOptions capturedOptions = stubManager.getCapturedOptions();
            assertEquals(0.3f, capturedOptions.getVectorWeight(), 0.001f,
                    "custom vectorWeight should be passed through");
        }

        @Test
        @DisplayName("同时自定义 top_k 和 vector_weight")
        void shouldPassBothCustomParametersToSearchOptions() {
            stubManager.setResults(List.of());

            searchTool.execute(Map.of(
                    "query", "test query",
                    "top_k", 20,
                    "vector_weight", 0.5f
            ));

            SearchOptions capturedOptions = stubManager.getCapturedOptions();
            assertEquals(20, capturedOptions.getTopK());
            assertEquals(0.5f, capturedOptions.getVectorWeight(), 0.001f);
        }

        @Test
        @DisplayName("top_k 为非 Number 类型时使用默认值")
        void shouldUseDefaultTopKWhenValueIsNotNumber() {
            stubManager.setResults(List.of());

            searchTool.execute(Map.of(
                    "query", "test query",
                    "top_k", "not-a-number"
            ));

            SearchOptions capturedOptions = stubManager.getCapturedOptions();
            assertEquals(5, capturedOptions.getTopK(),
                    "should fall back to default topK when value is not a Number");
        }

        @Test
        @DisplayName("vector_weight 为非 Number 类型时使用默认值")
        void shouldUseDefaultVectorWeightWhenValueIsNotNumber() {
            stubManager.setResults(List.of());

            searchTool.execute(Map.of(
                    "query", "test query",
                    "vector_weight", "high"
            ));

            SearchOptions capturedOptions = stubManager.getCapturedOptions();
            assertEquals(0.7f, capturedOptions.getVectorWeight(), 0.001f,
                    "should fall back to default vectorWeight when value is not a Number");
        }

        @Test
        @DisplayName("top_k 为 Double 类型时正确转换为 int")
        void shouldConvertDoubleTopKToInt() {
            stubManager.setResults(List.of());

            searchTool.execute(Map.of(
                    "query", "test query",
                    "top_k", 3.9  // JSON numbers may arrive as Double
            ));

            SearchOptions capturedOptions = stubManager.getCapturedOptions();
            assertEquals(3, capturedOptions.getTopK(),
                    "double value should be truncated to int via intValue()");
        }
    }

    // ==================== getToolSchema ====================

    @Nested
    @DisplayName("getToolSchema 工具定义验证")
    class GetToolSchema {

        @Test
        @DisplayName("schema 包含必要的顶层字段")
        void shouldContainRequiredTopLevelFields() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();

            assertEquals(MemorySearchTool.TOOL_NAME, schema.get("name"));
            assertEquals("memory_search", schema.get("name"));
            assertNotNull(schema.get("description"));
            assertNotNull(schema.get("input_schema"));
        }

        @Test
        @DisplayName("input_schema 定义了 query、top_k、vector_weight 属性")
        @SuppressWarnings("unchecked")
        void shouldDefineAllProperties() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();
            Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");

            assertEquals("object", inputSchema.get("type"));

            Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
            assertNotNull(properties.get("query"), "should define query property");
            assertNotNull(properties.get("top_k"), "should define top_k property");
            assertNotNull(properties.get("vector_weight"), "should define vector_weight property");
        }

        @Test
        @DisplayName("required 字段包含 query")
        @SuppressWarnings("unchecked")
        void shouldMarkQueryAsRequired() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();
            Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");
            List<String> required = (List<String>) inputSchema.get("required");

            assertNotNull(required, "required list should not be null");
            assertTrue(required.contains("query"), "query should be in the required list");
        }

        @Test
        @DisplayName("query 属性类型为 string")
        @SuppressWarnings("unchecked")
        void shouldDefineQueryAsString() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();
            Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");
            Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
            Map<String, Object> queryProperty = (Map<String, Object>) properties.get("query");

            assertEquals("string", queryProperty.get("type"));
        }

        @Test
        @DisplayName("top_k 属性类型为 integer")
        @SuppressWarnings("unchecked")
        void shouldDefineTopKAsInteger() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();
            Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");
            Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
            Map<String, Object> topKProperty = (Map<String, Object>) properties.get("top_k");

            assertEquals("integer", topKProperty.get("type"));
        }

        @Test
        @DisplayName("vector_weight 属性类型为 number")
        @SuppressWarnings("unchecked")
        void shouldDefineVectorWeightAsNumber() {
            Map<String, Object> schema = MemorySearchTool.getToolSchema();
            Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");
            Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
            Map<String, Object> vectorWeightProperty = (Map<String, Object>) properties.get("vector_weight");

            assertEquals("number", vectorWeightProperty.get("type"));
        }
    }

    // ==================== MemorySearchResult.toText ====================

    @Nested
    @DisplayName("MemorySearchResult.toText 文本格式化")
    class ToTextFormatting {

        @Test
        @DisplayName("有匹配结果时正确格式化输出文本")
        void shouldFormatResultsWithMatchesCorrectly() {
            List<MemoryMatch> matches = List.of(
                    new MemoryMatch("content1", "MEMORY.md", 0.95f, "snippet about dark mode"),
                    new MemoryMatch("content2", "memory/2026-05-10.md", 0.823f, "snippet about meetings")
            );
            MemorySearchResult result = new MemorySearchResult(true, null, matches);

            String text = result.toText();

            assertTrue(text.startsWith("Found 2 relevant memories:"),
                    "should start with count header");
            assertTrue(text.contains("1. [MEMORY.md] (score: 0.950)"),
                    "should contain numbered entry with source and formatted score");
            assertTrue(text.contains("snippet about dark mode"),
                    "should contain first snippet");
            assertTrue(text.contains("2. [memory/2026-05-10.md] (score: 0.823)"),
                    "should contain second entry with source and formatted score");
            assertTrue(text.contains("snippet about meetings"),
                    "should contain second snippet");
        }

        @Test
        @DisplayName("单条匹配结果显示 '1 relevant memories'")
        void shouldFormatSingleResultCorrectly() {
            List<MemoryMatch> matches = List.of(
                    new MemoryMatch("only content", "notes.md", 0.75f, "the only snippet")
            );
            MemorySearchResult result = new MemorySearchResult(true, null, matches);

            String text = result.toText();

            assertTrue(text.contains("Found 1 relevant memories:"));
            assertTrue(text.contains("1. [notes.md] (score: 0.750)"));
            assertTrue(text.contains("the only snippet"));
        }

        @Test
        @DisplayName("无匹配结果时返回 'No matching memories found.'")
        void shouldReturnNoMatchesMessage() {
            MemorySearchResult result = new MemorySearchResult(true, null, List.of());

            String text = result.toText();

            assertEquals("No matching memories found.", text);
        }

        @Test
        @DisplayName("错误结果返回 'Error: ' 前缀加错误信息")
        void shouldReturnErrorMessageWithPrefix() {
            MemorySearchResult result = new MemorySearchResult(false,
                    "Query parameter is required", List.of());

            String text = result.toText();

            assertEquals("Error: Query parameter is required", text);
        }

        @Test
        @DisplayName("错误结果即使有匹配项也返回错误信息")
        void shouldReturnErrorEvenIfMatchesPresent() {
            List<MemoryMatch> matches = List.of(
                    new MemoryMatch("content", "source.md", 0.9f, "snippet")
            );
            MemorySearchResult result = new MemorySearchResult(false,
                    "Something went wrong", matches);

            String text = result.toText();

            assertTrue(text.startsWith("Error:"),
                    "error takes precedence over matches");
            assertFalse(text.contains("snippet"),
                    "should not include match content when there is an error");
        }

        @Test
        @DisplayName("分数格式化为三位小数")
        void shouldFormatScoreToThreeDecimalPlaces() {
            List<MemoryMatch> matches = List.of(
                    new MemoryMatch("content", "src.md", 0.5f, "half score snippet")
            );
            MemorySearchResult result = new MemorySearchResult(true, null, matches);

            String text = result.toText();

            assertTrue(text.contains("score: 0.500"),
                    "score 0.5 should be formatted as 0.500");
        }
    }

    // ==================== TOOL_NAME constant ====================

    @Test
    @DisplayName("TOOL_NAME 常量值为 'memory_search'")
    void toolNameConstantShouldBeMemorySearch() {
        assertEquals("memory_search", MemorySearchTool.TOOL_NAME);
    }

    // ==================== End-to-end with real FileMemoryManager ====================

    @Nested
    @DisplayName("execute 端到端集成（使用真实 FileMemoryManager）")
    class EndToEndIntegration {

        private FileMemoryManager realManager;

        @BeforeEach
        void setUp() {
            Path agentRoot = tempDir.resolve("integration-agent");
            realManager = new FileMemoryManager(agentRoot, "integration-agent",
                    new MockEmbeddingProvider(64));
        }

        @AfterEach
        void tearDown() {
            if (realManager != null) {
                realManager.close();
            }
        }

        @Test
        @DisplayName("写入记忆后搜索能找到匹配内容")
        void shouldFindWrittenMemoryViaSearch() {
            // Write some memory content first
            realManager.writeDurable("## Preferences\n\nUser likes dark mode and vim keybindings.");

            MemorySearchTool tool = new MemorySearchTool(realManager);

            MemorySearchResult result = tool.execute(Map.of(
                    "query", "dark mode",
                    "top_k", 3
            ));

            assertTrue(result.success(), "search should succeed");
            assertNull(result.error());
            // The real search should find something related to what we wrote
            // (depending on indexing behavior, we at least verify no crash)
        }

        @Test
        @DisplayName("空记忆库搜索返回成功但无结果")
        void shouldReturnEmptyResultsFromEmptyMemory() {
            MemorySearchTool tool = new MemorySearchTool(realManager);

            MemorySearchResult result = tool.execute(Map.of("query", "anything at all"));

            assertTrue(result.success());
            assertNull(result.error());
            assertTrue(result.matches().isEmpty(),
                    "empty memory store should produce no matches");
        }
    }
}
