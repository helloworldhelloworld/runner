package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SearchResult} — hybrid search result combining BM25 and vector scores.
 *
 * Covers: builder, field access, toString.
 */
@DisplayName("SearchResult - 混合搜索结果")
class SearchResultTest {

    private MemoryChunk sampleChunk() {
        return MemoryChunk.builder()
            .id("chunk-1")
            .content("test content for search")
            .hash("abc")
            .type(MemoryType.EPHEMERAL)
            .build();
    }

    @Test
    @DisplayName("Builder — 所有字段正确设置")
    void builder_allFields() {
        MemoryChunk chunk = sampleChunk();
        SearchResult result = SearchResult.builder()
            .chunk(chunk)
            .score(0.85f)
            .bm25Score(0.7f)
            .vectorScore(0.9f)
            .snippet("...test content...")
            .build();

        assertSame(chunk, result.getChunk());
        assertEquals(0.85f, result.getScore(), 0.001f);
        assertEquals(0.7f, result.getBm25Score(), 0.001f);
        assertEquals(0.9f, result.getVectorScore(), 0.001f);
        assertEquals("...test content...", result.getSnippet());
    }

    @Test
    @DisplayName("构造函数 — 直接创建")
    void constructor_directCreation() {
        MemoryChunk chunk = sampleChunk();
        SearchResult result = new SearchResult(chunk, 0.5f, 0.3f, 0.6f, "snippet");

        assertEquals(0.5f, result.getScore(), 0.001f);
        assertEquals("snippet", result.getSnippet());
    }

    @Test
    @DisplayName("toString — 包含 chunk ID 和 snippet 预览")
    void toString_includesKeyInfo() {
        SearchResult result = SearchResult.builder()
            .chunk(sampleChunk())
            .score(0.9f)
            .snippet("This is a long snippet that should be truncated in toString output for readability")
            .build();

        String str = result.toString();
        assertTrue(str.contains("chunk-1"));
        assertTrue(str.contains("0.9"));
    }

    @Test
    @DisplayName("null snippet 不导致 toString 崩溃")
    void toString_nullSnippet_doesNotThrow() {
        SearchResult result = SearchResult.builder()
            .chunk(sampleChunk())
            .score(0.5f)
            .snippet(null)
            .build();

        assertDoesNotThrow(() -> result.toString());
    }
}
