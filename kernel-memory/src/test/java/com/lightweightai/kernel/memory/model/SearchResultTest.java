package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SearchResult - 搜索结果")
class SearchResultTest {

    private MemoryChunk createChunk(String id) {
        return MemoryChunk.builder()
            .id(id).content("content of " + id).hash("hash-" + id)
            .sourceFile("File.java").startLine(1).endLine(10)
            .build();
    }

    @Test
    @DisplayName("Builder 构建完整结果")
    void builderFullBuild() {
        MemoryChunk chunk = createChunk("c1");
        SearchResult result = SearchResult.builder()
            .chunk(chunk)
            .score(0.95f)
            .bm25Score(0.8f)
            .vectorScore(0.99f)
            .snippet("matched text here")
            .build();

        assertEquals(chunk, result.getChunk());
        assertEquals(0.95f, result.getScore(), 0.001f);
        assertEquals(0.8f, result.getBm25Score(), 0.001f);
        assertEquals(0.99f, result.getVectorScore(), 0.001f);
        assertEquals("matched text here", result.getSnippet());
    }

    @Test
    @DisplayName("构造函数与 getter 一致")
    void constructorMatchesGetters() {
        MemoryChunk chunk = createChunk("c2");
        SearchResult result = new SearchResult(chunk, 0.85f, 0.7f, 0.9f, "snippet");

        assertSame(chunk, result.getChunk());
        assertEquals(0.85f, result.getScore(), 0.001f);
        assertEquals("snippet", result.getSnippet());
    }

    @Test
    @DisplayName("toString 包含 chunk id 和 score")
    void toStringContainsKeyInfo() {
        MemoryChunk chunk = createChunk("my-chunk");
        SearchResult result = new SearchResult(chunk, 0.5f, 0.3f, 0.7f, "some snippet here");

        String str = result.toString();
        assertTrue(str.contains("my-chunk"));
        assertTrue(str.contains("0.5"));
    }

    @Test
    @DisplayName("toString 处理 null snippet")
    void toStringHandlesNullSnippet() {
        MemoryChunk chunk = createChunk("c3");
        SearchResult result = new SearchResult(chunk, 0.5f, 0.3f, 0.7f, null);

        String str = result.toString();
        assertTrue(str.contains("null"));
    }
}
