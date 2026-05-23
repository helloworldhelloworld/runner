package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SearchResult - 混合搜索结果")
class SearchResultTest {

    @Test
    @DisplayName("Builder 设置所有字段并正确返回")
    void builderSetsAllFields() {
        MemoryChunk chunk = createChunk("c1", "test content");
        SearchResult result = SearchResult.builder()
                .chunk(chunk)
                .score(0.85f)
                .bm25Score(0.7f)
                .vectorScore(0.95f)
                .snippet("...test content...")
                .build();

        assertSame(chunk, result.getChunk());
        assertEquals(0.85f, result.getScore(), 0.001f);
        assertEquals(0.7f, result.getBm25Score(), 0.001f);
        assertEquals(0.95f, result.getVectorScore(), 0.001f);
        assertEquals("...test content...", result.getSnippet());
    }

    @Test
    @DisplayName("直接构造传入所有参数")
    void directConstruction() {
        MemoryChunk chunk = createChunk("c2", "hello");
        SearchResult result = new SearchResult(chunk, 0.9f, 0.8f, 0.95f, "hello world");

        assertEquals(0.9f, result.getScore(), 0.001f);
        assertEquals("hello world", result.getSnippet());
    }

    @Test
    @DisplayName("toString 包含 chunk id、score 和 snippet 前缀")
    void toStringContainsKeyInfo() {
        MemoryChunk chunk = createChunk("chunk-99", "some long content here for display");
        SearchResult result = SearchResult.builder()
                .chunk(chunk)
                .score(0.75f)
                .snippet("some long content here for display")
                .build();

        String str = result.toString();
        assertTrue(str.contains("chunk-99"));
        assertTrue(str.contains("0.75"));
    }

    @Test
    @DisplayName("toString snippet 为 null 时安全处理")
    void toStringWithNullSnippet() {
        MemoryChunk chunk = createChunk("c3", "text");
        SearchResult result = SearchResult.builder()
                .chunk(chunk)
                .score(0.5f)
                .snippet(null)
                .build();

        String str = result.toString();
        assertTrue(str.contains("null"));
    }

    @Test
    @DisplayName("混合分数 = BM25 + Vector 加权后的结果可以正确携带")
    void hybridScoresCarriedCorrectly() {
        MemoryChunk chunk = createChunk("c4", "test");
        float bm25 = 0.3f;
        float vector = 0.9f;
        float hybrid = 0.7f * vector + 0.3f * bm25;

        SearchResult result = SearchResult.builder()
                .chunk(chunk)
                .score(hybrid)
                .bm25Score(bm25)
                .vectorScore(vector)
                .build();

        assertEquals(hybrid, result.getScore(), 0.001f);
        assertEquals(bm25, result.getBm25Score(), 0.001f);
        assertEquals(vector, result.getVectorScore(), 0.001f);
    }

    private MemoryChunk createChunk(String id, String content) {
        return MemoryChunk.builder()
                .id(id)
                .content(content)
                .hash("hash-" + id)
                .build();
    }
}
