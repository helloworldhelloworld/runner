package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SearchResult - 混合搜索结果")
class SearchResultTest {

    private MemoryChunk createTestChunk(String id) {
        return MemoryChunk.builder()
                .id(id)
                .content("Test content for " + id)
                .hash("hash-" + id)
                .build();
    }

    @Test
    @DisplayName("Builder 构建并验证所有分数字段")
    void builderSetsAllFields() {
        MemoryChunk chunk = createTestChunk("c1");
        SearchResult result = SearchResult.builder()
                .chunk(chunk)
                .score(0.85f)
                .bm25Score(0.7f)
                .vectorScore(0.95f)
                .snippet("...matching text...")
                .build();

        assertSame(chunk, result.getChunk());
        assertEquals(0.85f, result.getScore(), 0.001f);
        assertEquals(0.7f, result.getBm25Score(), 0.001f);
        assertEquals(0.95f, result.getVectorScore(), 0.001f);
        assertEquals("...matching text...", result.getSnippet());
    }

    @Test
    @DisplayName("直接构造函数设置所有字段")
    void directConstructor() {
        MemoryChunk chunk = createTestChunk("c2");
        SearchResult result = new SearchResult(chunk, 0.9f, 0.8f, 0.95f, "snippet");

        assertEquals(0.9f, result.getScore(), 0.001f);
        assertEquals("snippet", result.getSnippet());
    }

    @Test
    @DisplayName("toString 包含 chunk ID 和分数")
    void toStringContent() {
        MemoryChunk chunk = createTestChunk("chunk-42");
        SearchResult result = SearchResult.builder()
                .chunk(chunk)
                .score(0.75f)
                .snippet("some matched text here")
                .build();

        String s = result.toString();
        assertTrue(s.contains("chunk-42"));
        assertTrue(s.contains("0.75"));
    }

    @Test
    @DisplayName("null snippet 在 toString 中显示 null")
    void nullSnippetToString() {
        MemoryChunk chunk = createTestChunk("c3");
        SearchResult result = SearchResult.builder()
                .chunk(chunk)
                .score(0.5f)
                .build();

        String s = result.toString();
        assertTrue(s.contains("null"));
    }
}
