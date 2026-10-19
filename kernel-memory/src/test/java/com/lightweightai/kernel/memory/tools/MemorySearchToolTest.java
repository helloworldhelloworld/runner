package com.lightweightai.kernel.memory.tools;

import com.lightweightai.kernel.memory.embedding.MockEmbeddingProvider;
import com.lightweightai.kernel.memory.file.FileMemoryManager;
import com.lightweightai.kernel.memory.model.MemoryChunk;
import com.lightweightai.kernel.memory.model.MemoryType;
import com.lightweightai.kernel.memory.model.SearchOptions;
import com.lightweightai.kernel.memory.model.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemorySearchTool — 记忆搜索工具")
class MemorySearchToolTest {

    @TempDir
    Path tempDir;

    private StubMemoryManager memoryManager;
    private MemorySearchTool tool;

    @BeforeEach
    void setUp() {
        memoryManager = new StubMemoryManager(tempDir.resolve("stub-agent-" + System.nanoTime()));
        tool = new MemorySearchTool(memoryManager);
    }

    @Test
    @DisplayName("query 为空时返回错误")
    void emptyQueryReturnsError() {
        MemorySearchTool.MemorySearchResult result = tool.execute(Map.of("query", ""));
        assertFalse(result.success());
        assertNotNull(result.error());
    }

    @Test
    @DisplayName("缺少 query 参数时返回错误")
    void missingQueryReturnsError() {
        MemorySearchTool.MemorySearchResult result = tool.execute(Map.of());
        assertFalse(result.success());
    }

    @Test
    @DisplayName("正常搜索返回结果")
    void normalSearchReturnsResults() {
        memoryManager.setResults(List.of(
                makeSearchResult("chunk-1", "Hello world", "test.md", 0.95f, "...Hello...")
        ));

        MemorySearchTool.MemorySearchResult result = tool.execute(Map.of("query", "Hello"));

        assertTrue(result.success());
        assertNull(result.error());
        assertEquals(1, result.matches().size());
        assertEquals("Hello world", result.matches().get(0).content());
        assertEquals(0.95f, result.matches().get(0).score(), 0.001f);
    }

    @Test
    @DisplayName("top_k 参数传递到 SearchOptions")
    void topKParameterPassed() {
        memoryManager.setResults(List.of());

        tool.execute(Map.of("query", "test", "top_k", 3));

        assertEquals(3, memoryManager.lastOptions.getTopK());
    }

    @Test
    @DisplayName("vector_weight 参数传递到 SearchOptions")
    void vectorWeightParameterPassed() {
        memoryManager.setResults(List.of());

        tool.execute(Map.of("query", "test", "vector_weight", 0.5));

        assertEquals(0.5f, memoryManager.lastOptions.getVectorWeight(), 0.01f);
    }

    @Test
    @DisplayName("无结果时返回空 matches")
    void noResultsReturnsEmptyMatches() {
        memoryManager.setResults(List.of());

        MemorySearchTool.MemorySearchResult result = tool.execute(Map.of("query", "nothing"));

        assertTrue(result.success());
        assertTrue(result.matches().isEmpty());
    }

    @Test
    @DisplayName("toText 格式化输出 — 有结果")
    void toTextWithResults() {
        memoryManager.setResults(List.of(
                makeSearchResult("c1", "content", "src.md", 0.8f, "snippet")
        ));

        MemorySearchTool.MemorySearchResult result = tool.execute(Map.of("query", "q"));

        String text = result.toText();
        assertTrue(text.contains("1 relevant"));
        assertTrue(text.contains("src.md"));
        assertTrue(text.contains("0.800"));
    }

    @Test
    @DisplayName("toText 格式化输出 — 无结果")
    void toTextNoResults() {
        memoryManager.setResults(List.of());

        MemorySearchTool.MemorySearchResult result = tool.execute(Map.of("query", "q"));

        assertTrue(result.toText().contains("No matching"));
    }

    @Test
    @DisplayName("toText 格式化输出 — 错误")
    void toTextOnError() {
        MemorySearchTool.MemorySearchResult result = tool.execute(Map.of());
        assertTrue(result.toText().contains("Error"));
    }

    @Test
    @DisplayName("getToolSchema 包含名称和描述")
    void toolSchema() {
        Map<String, Object> schema = MemorySearchTool.getToolSchema();

        assertEquals("memory_search", schema.get("name"));
        assertNotNull(schema.get("description"));
        assertNotNull(schema.get("input_schema"));
    }

    // ==================== Helpers ====================

    private SearchResult makeSearchResult(String id, String content, String source, float score, String snippet) {
        MemoryChunk chunk = MemoryChunk.builder()
                .id(id)
                .content(content)
                .sourceFile(source)
                .startLine(1)
                .endLine(1)
                .hash("h")
                .createdAt(Instant.now())
                .type(MemoryType.DURABLE)
                .build();
        return new SearchResult(chunk, score, score * 0.5f, score * 0.8f, snippet);
    }

    private static class StubMemoryManager extends FileMemoryManager {
        private List<SearchResult> results = List.of();
        SearchOptions lastOptions;

        StubMemoryManager(Path agentRoot) {
            super(agentRoot, "stub-agent", new MockEmbeddingProvider(64));
        }

        void setResults(List<SearchResult> results) {
            this.results = results;
        }

        @Override
        public List<SearchResult> search(String query, SearchOptions options) {
            this.lastOptions = options;
            return results;
        }
    }
}
