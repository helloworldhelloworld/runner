package com.lightweightai.kernel.memory.tools;

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
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MemorySearchTool 单元测试
 *
 * 使用手写 stub FileMemoryManager 替代真实实现，
 * 覆盖搜索参数传递、结果映射、错误处理、schema 结构等关键路径。
 */
class MemorySearchToolTest {

    private CapturingMemoryManager capturingManager;
    private MemorySearchTool searchTool;

    @BeforeEach
    void setUp() {
        capturingManager = new CapturingMemoryManager();
        searchTool = new MemorySearchTool(capturingManager);
    }

    @AfterEach
    void tearDown() {
        capturingManager.close();
    }

    // ==================== 参数校验 ====================

    @Test
    @DisplayName("query 为 null 时返回失败结果")
    void shouldFailWhenQueryIsNull() {
        MemorySearchResult result = searchTool.execute(Map.of());
        assertFalse(result.success());
        assertEquals("Query parameter is required", result.error());
        assertTrue(result.matches().isEmpty());
    }

    @Test
    @DisplayName("query 为空白时返回失败结果")
    void shouldFailWhenQueryIsBlank() {
        MemorySearchResult result = searchTool.execute(Map.of("query", "   "));
        assertFalse(result.success());
        assertEquals("Query parameter is required", result.error());
    }

    // ==================== 默认参数 ====================

    @Test
    @DisplayName("使用默认 top_k=5 和 vector_weight=0.7")
    void shouldUseDefaultParameters() {
        capturingManager.stubbedResults = List.of();
        searchTool.execute(Map.of("query", "test"));

        assertEquals("test", capturingManager.capturedQuery);
        assertEquals(5, capturingManager.capturedOptions.getTopK());
        assertEquals(0.7f, capturingManager.capturedOptions.getVectorWeight(), 0.001f);
    }

    // ==================== 自定义参数 ====================

    @Test
    @DisplayName("自定义 top_k 和 vector_weight 正确传递给 FileMemoryManager")
    void shouldPassCustomParameters() {
        capturingManager.stubbedResults = List.of();
        Map<String, Object> params = new HashMap<>();
        params.put("query", "custom search");
        params.put("top_k", 10);
        params.put("vector_weight", 0.5);

        searchTool.execute(params);

        assertEquals("custom search", capturingManager.capturedQuery);
        assertEquals(10, capturingManager.capturedOptions.getTopK());
        assertEquals(0.5f, capturingManager.capturedOptions.getVectorWeight(), 0.001f);
    }

    // ==================== 结果映射 ====================

    @Test
    @DisplayName("搜索结果正确映射为 MemoryMatch 列表")
    void shouldMapSearchResultsToMatches() {
        MemoryChunk chunk = MemoryChunk.builder()
                .id("chunk-1")
                .content("Hello world content")
                .sourceFile("memory/2024-01-01.md")
                .startLine(1).endLine(5)
                .hash("abc123")
                .createdAt(Instant.now())
                .type(MemoryType.EPHEMERAL)
                .build();

        SearchResult sr = SearchResult.builder()
                .chunk(chunk)
                .score(0.85f)
                .bm25Score(0.3f)
                .vectorScore(0.55f)
                .snippet("Hello world...")
                .build();

        capturingManager.stubbedResults = List.of(sr);

        MemorySearchResult result = searchTool.execute(Map.of("query", "hello"));

        assertTrue(result.success());
        assertNull(result.error());
        assertEquals(1, result.matches().size());

        MemoryMatch match = result.matches().get(0);
        assertEquals("Hello world content", match.content());
        assertEquals("memory/2024-01-01.md", match.source());
        assertEquals(0.85f, match.score(), 0.001f);
        assertEquals("Hello world...", match.snippet());
    }

    @Test
    @DisplayName("无搜索结果时返回空列表")
    void shouldReturnEmptyMatchesWhenNoResults() {
        capturingManager.stubbedResults = List.of();

        MemorySearchResult result = searchTool.execute(Map.of("query", "nothing"));
        assertTrue(result.success());
        assertTrue(result.matches().isEmpty());
    }

    // ==================== toText ====================

    @Test
    @DisplayName("失败结果的 toText 包含错误信息")
    void shouldFormatErrorInToText() {
        MemorySearchResult result = searchTool.execute(Map.of());
        String text = result.toText();
        assertTrue(text.startsWith("Error:"));
        assertTrue(text.contains("Query parameter is required"));
    }

    @Test
    @DisplayName("空结果的 toText 返回 no matches 提示")
    void shouldFormatNoMatchesInToText() {
        capturingManager.stubbedResults = List.of();
        MemorySearchResult result = searchTool.execute(Map.of("query", "nothing"));
        assertEquals("No matching memories found.", result.toText());
    }

    @Test
    @DisplayName("有结果的 toText 包含条目数和 score")
    void shouldFormatMatchesInToText() {
        MemoryChunk chunk = MemoryChunk.builder()
                .id("c1").content("test content").sourceFile("test.md")
                .startLine(1).endLine(2).hash("h1").type(MemoryType.DURABLE)
                .build();
        capturingManager.stubbedResults = List.of(
                SearchResult.builder().chunk(chunk).score(0.92f).snippet("snippet text").build()
        );

        MemorySearchResult result = searchTool.execute(Map.of("query", "test"));
        String text = result.toText();

        assertTrue(text.contains("Found 1 relevant memories"));
        assertTrue(text.contains("0.920"));
        assertTrue(text.contains("snippet text"));
        assertTrue(text.contains("test.md"));
    }

    // ==================== Schema ====================

    @Test
    @DisplayName("getToolSchema 包含必要的字段定义")
    @SuppressWarnings("unchecked")
    void shouldProvideCorrectToolSchema() {
        Map<String, Object> schema = MemorySearchTool.getToolSchema();

        assertEquals("memory_search", schema.get("name"));
        assertNotNull(schema.get("description"));

        Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");
        assertEquals("object", inputSchema.get("type"));

        Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
        assertTrue(properties.containsKey("query"));
        assertTrue(properties.containsKey("top_k"));
        assertTrue(properties.containsKey("vector_weight"));

        List<String> required = (List<String>) inputSchema.get("required");
        assertTrue(required.contains("query"));
    }

    @Test
    @DisplayName("TOOL_NAME 和 TOOL_DESCRIPTION 不为空")
    void shouldHaveToolConstants() {
        assertEquals("memory_search", MemorySearchTool.TOOL_NAME);
        assertNotNull(MemorySearchTool.TOOL_DESCRIPTION);
        assertFalse(MemorySearchTool.TOOL_DESCRIPTION.isBlank());
    }

    // ==================== Stub ====================

    /**
     * 捕获参数的 FileMemoryManager stub — 不做真正的文件/索引操作。
     * 继承 FileMemoryManager 但覆盖 search 方法。
     */
    private static class CapturingMemoryManager extends FileMemoryManager {
        String capturedQuery;
        SearchOptions capturedOptions;
        List<SearchResult> stubbedResults = List.of();

        CapturingMemoryManager() {
            super(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "memory-test-" + System.nanoTime()),
                    "test-agent",
                    new com.lightweightai.kernel.memory.embedding.MockEmbeddingProvider());
        }

        @Override
        public List<SearchResult> search(String query, SearchOptions options) {
            this.capturedQuery = query;
            this.capturedOptions = options;
            return stubbedResults;
        }
    }
}
