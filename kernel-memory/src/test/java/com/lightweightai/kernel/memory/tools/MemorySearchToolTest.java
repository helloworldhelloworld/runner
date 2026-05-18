package com.lightweightai.kernel.memory.tools;

import com.lightweightai.kernel.memory.embedding.MockEmbeddingProvider;
import com.lightweightai.kernel.memory.file.FileMemoryManager;
import com.lightweightai.kernel.memory.tools.MemorySearchTool.MemorySearchResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemorySearchTool - 记忆搜索工具")
class MemorySearchToolTest {

    @TempDir
    Path tempDir;

    private FileMemoryManager memoryManager;
    private MemorySearchTool searchTool;

    @BeforeEach
    void setUp() {
        Path agentRoot = tempDir.resolve("search-agent");
        MockEmbeddingProvider embeddingProvider = new MockEmbeddingProvider(64);
        memoryManager = new FileMemoryManager(agentRoot, "search-agent", embeddingProvider);
        searchTool = new MemorySearchTool(memoryManager);
    }

    @AfterEach
    void tearDown() {
        memoryManager.close();
    }

    // ==================== 搜索执行 ====================

    @Test
    @DisplayName("缺少 query 参数返回错误")
    void missingQueryReturnsError() {
        MemorySearchResult result = searchTool.execute(Map.of());
        assertFalse(result.success());
        assertNotNull(result.error());
        assertTrue(result.error().contains("required"));
    }

    @Test
    @DisplayName("空白 query 返回错误")
    void blankQueryReturnsError() {
        MemorySearchResult result = searchTool.execute(Map.of("query", "   "));
        assertFalse(result.success());
    }

    @Test
    @DisplayName("null query 返回错误")
    void nullQueryReturnsError() {
        MemorySearchResult result = searchTool.execute(Map.of("key", "val"));
        assertFalse(result.success());
    }

    @Test
    @DisplayName("有效查询空索引返回成功但无匹配")
    void emptyIndexReturnsNoMatches() {
        MemorySearchResult result = searchTool.execute(Map.of("query", "test query"));
        assertTrue(result.success());
        assertNull(result.error());
        assertTrue(result.matches().isEmpty());
    }

    @Test
    @DisplayName("写入数据后搜索可找到")
    void searchFindsIndexedContent() {
        memoryManager.writeDurable("My favorite color is blue");
        memoryManager.appendDurable("Weather", "The weather today is sunny");

        MemorySearchResult result = searchTool.execute(Map.of("query", "favorite color"));
        assertTrue(result.success());
    }

    @Test
    @DisplayName("top_k 参数生效")
    void topKParameter() {
        memoryManager.writeDurable("Document 1 about topic\n\nDocument 2 about topic\n\nDocument 3 about topic");

        MemorySearchResult result = searchTool.execute(Map.of(
            "query", "topic",
            "top_k", 2
        ));
        assertTrue(result.success());
        assertTrue(result.matches().size() <= 2);
    }

    @Test
    @DisplayName("vector_weight 参数被接受")
    void vectorWeightParameter() {
        memoryManager.writeDurable("Some content for testing");

        MemorySearchResult result = searchTool.execute(Map.of(
            "query", "content testing",
            "vector_weight", 0.3
        ));
        assertTrue(result.success());
    }

    // ==================== MemorySearchResult.toText ====================

    @Test
    @DisplayName("toText 错误场景")
    void toTextError() {
        MemorySearchResult result = new MemorySearchResult(false, "Query required", List.of());
        String text = result.toText();
        assertTrue(text.contains("Error:"));
        assertTrue(text.contains("Query required"));
    }

    @Test
    @DisplayName("toText 无匹配")
    void toTextNoMatches() {
        MemorySearchResult result = new MemorySearchResult(true, null, List.of());
        assertEquals("No matching memories found.", result.toText());
    }

    @Test
    @DisplayName("toText 有匹配结果")
    void toTextWithMatches() {
        MemorySearchResult result = new MemorySearchResult(true, null, List.of(
            new MemorySearchTool.MemoryMatch("content1", "source1.md", 0.95f, "snippet1"),
            new MemorySearchTool.MemoryMatch("content2", "source2.md", 0.80f, "snippet2")
        ));
        String text = result.toText();
        assertTrue(text.contains("Found 2 relevant memories"));
        assertTrue(text.contains("source1.md"));
        assertTrue(text.contains("source2.md"));
        assertTrue(text.contains("0.950"));
    }

    // ==================== Schema ====================

    @Test
    @DisplayName("getToolSchema 返回正确结构")
    void toolSchemaStructure() {
        Map<String, Object> schema = MemorySearchTool.getToolSchema();
        assertEquals("memory_search", schema.get("name"));
        assertNotNull(schema.get("description"));
        assertNotNull(schema.get("input_schema"));

        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");
        assertEquals("object", inputSchema.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) inputSchema.get("properties");
        assertTrue(props.containsKey("query"));
        assertTrue(props.containsKey("top_k"));
        assertTrue(props.containsKey("vector_weight"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) inputSchema.get("required");
        assertTrue(required.contains("query"));
    }

    // ==================== 常量 ====================

    @Test
    @DisplayName("工具名称和描述不为空")
    void toolNameAndDescription() {
        assertEquals("memory_search", MemorySearchTool.TOOL_NAME);
        assertNotNull(MemorySearchTool.TOOL_DESCRIPTION);
        assertFalse(MemorySearchTool.TOOL_DESCRIPTION.isEmpty());
    }
}
