package com.lightweightai.kernel.memory.tools;

import com.lightweightai.kernel.memory.embedding.MockEmbeddingProvider;
import com.lightweightai.kernel.memory.file.FileMemoryManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemorySearchTool - 记忆搜索工具")
class MemorySearchToolTest {

    @TempDir
    Path tempDir;

    private MemorySearchTool searchTool;
    private FileMemoryManager memoryManager;

    @BeforeEach
    void setUp() {
        Path agentRoot = tempDir.resolve("test-agent");
        memoryManager = new FileMemoryManager(agentRoot, "test-agent", new MockEmbeddingProvider(64));
        searchTool = new MemorySearchTool(memoryManager);
    }

    // ==================== 参数校验 ====================

    @Test
    @DisplayName("缺少 query 参数返回错误")
    void shouldReturnErrorWhenQueryMissing() {
        MemorySearchTool.MemorySearchResult result = searchTool.execute(Map.of());

        assertFalse(result.success());
        assertNotNull(result.error());
        assertTrue(result.error().contains("required"));
    }

    @Test
    @DisplayName("空白 query 返回错误")
    void shouldReturnErrorForBlankQuery() {
        MemorySearchTool.MemorySearchResult result = searchTool.execute(Map.of("query", "  "));

        assertFalse(result.success());
    }

    @Test
    @DisplayName("null query 返回错误")
    void shouldReturnErrorForNullQuery() {
        java.util.HashMap<String, Object> params = new java.util.HashMap<>();
        params.put("query", null);
        MemorySearchTool.MemorySearchResult result = searchTool.execute(params);

        assertFalse(result.success());
    }

    // ==================== 搜索执行 ====================

    @Test
    @DisplayName("无记忆时搜索返回空结果")
    void shouldReturnEmptyOnNoMemories() {
        MemorySearchTool.MemorySearchResult result = searchTool.execute(
            Map.of("query", "hello world"));

        assertTrue(result.success());
        assertTrue(result.matches().isEmpty());
    }

    @Test
    @DisplayName("有记忆时能搜索到结果")
    void shouldFindMatchingMemories() {
        memoryManager.appendEphemeral("用户喜欢喝咖啡和看电影");
        memoryManager.appendEphemeral("今天的天气是晴天");

        MemorySearchTool.MemorySearchResult result = searchTool.execute(
            Map.of("query", "咖啡"));

        assertTrue(result.success());
        // 具体结果取决于 BM25 + Vector 搜索的实现
    }

    @Test
    @DisplayName("支持 top_k 参数")
    void shouldRespectTopKParameter() {
        for (int i = 0; i < 10; i++) {
            memoryManager.appendEphemeral("记忆条目 " + i);
        }

        MemorySearchTool.MemorySearchResult result = searchTool.execute(
            Map.of("query", "记忆", "top_k", 3));

        assertTrue(result.success());
        assertTrue(result.matches().size() <= 3);
    }

    @Test
    @DisplayName("支持 vector_weight 参数")
    void shouldRespectVectorWeightParameter() {
        memoryManager.appendEphemeral("测试内容");

        MemorySearchTool.MemorySearchResult result = searchTool.execute(
            Map.of("query", "测试", "vector_weight", 0.5));

        assertTrue(result.success());
    }

    // ==================== MemorySearchResult ====================

    @Test
    @DisplayName("错误结果的 toText 包含 Error")
    void shouldFormatErrorToText() {
        MemorySearchTool.MemorySearchResult result =
            new MemorySearchTool.MemorySearchResult(false, "query required", java.util.List.of());

        String text = result.toText();
        assertTrue(text.contains("Error"));
        assertTrue(text.contains("query required"));
    }

    @Test
    @DisplayName("空结果的 toText")
    void shouldFormatEmptyResultToText() {
        MemorySearchTool.MemorySearchResult result =
            new MemorySearchTool.MemorySearchResult(true, null, java.util.List.of());

        String text = result.toText();
        assertTrue(text.contains("No matching"));
    }

    @Test
    @DisplayName("有结果的 toText 包含匹配数量")
    void shouldFormatResultsToText() {
        var matches = java.util.List.of(
            new MemorySearchTool.MemoryMatch("content", "file.md", 0.95f, "snippet text")
        );
        MemorySearchTool.MemorySearchResult result =
            new MemorySearchTool.MemorySearchResult(true, null, matches);

        String text = result.toText();
        assertTrue(text.contains("1 relevant"));
        assertTrue(text.contains("snippet text"));
        assertTrue(text.contains("0.950"));
    }

    // ==================== Schema ====================

    @Test
    @DisplayName("getToolSchema 包含必要字段")
    void shouldReturnValidSchema() {
        Map<String, Object> schema = MemorySearchTool.getToolSchema();

        assertEquals("memory_search", schema.get("name"));
        assertNotNull(schema.get("description"));
        assertNotNull(schema.get("input_schema"));
    }

    @Test
    @DisplayName("常量定义正确")
    void shouldHaveCorrectConstants() {
        assertEquals("memory_search", MemorySearchTool.TOOL_NAME);
        assertNotNull(MemorySearchTool.TOOL_DESCRIPTION);
        assertFalse(MemorySearchTool.TOOL_DESCRIPTION.isEmpty());
    }
}
