package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SearchResult - 混合搜索结果")
class SearchResultTest {

    @Test
    @DisplayName("Builder 构建并获取所有字段")
    void builderFullConstruction() {
        MemoryChunk chunk = MemoryChunk.builder()
            .id("c1").content("test").hash("h1").build();

        SearchResult result = SearchResult.builder()
            .chunk(chunk)
            .score(0.95f)
            .bm25Score(0.8f)
            .vectorScore(0.9f)
            .snippet("test snippet")
            .build();

        assertSame(chunk, result.getChunk());
        assertEquals(0.95f, result.getScore(), 0.001f);
        assertEquals(0.8f, result.getBm25Score(), 0.001f);
        assertEquals(0.9f, result.getVectorScore(), 0.001f);
        assertEquals("test snippet", result.getSnippet());
    }

    @Test
    @DisplayName("toString 包含 chunk id 和 score")
    void toStringContainsFields() {
        MemoryChunk chunk = MemoryChunk.builder()
            .id("chunk-42").content("x").hash("h").build();

        SearchResult result = SearchResult.builder()
            .chunk(chunk)
            .score(0.85f)
            .snippet("some content here")
            .build();

        String str = result.toString();
        assertTrue(str.contains("chunk-42"));
        assertTrue(str.contains("0.85"));
    }

    @Test
    @DisplayName("长 snippet 在 toString 中被截断")
    void longSnippetTruncatedInToString() {
        MemoryChunk chunk = MemoryChunk.builder()
            .id("c1").content("x").hash("h").build();

        String longSnippet = "a".repeat(100);
        SearchResult result = SearchResult.builder()
            .chunk(chunk)
            .snippet(longSnippet)
            .build();

        String str = result.toString();
        assertTrue(str.contains("..."));
    }
}
