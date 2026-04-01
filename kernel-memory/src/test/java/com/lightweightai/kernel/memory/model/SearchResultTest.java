package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SearchResult - 搜索结果")
class SearchResultTest {

    private MemoryChunk createChunk(String id) {
        return MemoryChunk.builder()
                .id(id).content("test content").hash("hash1")
                .createdAt(Instant.now()).type(MemoryType.DURABLE)
                .build();
    }

    @Test
    @DisplayName("构造函数")
    void construction() {
        MemoryChunk chunk = createChunk("c1");
        SearchResult result = new SearchResult(chunk, 0.9f, 0.8f, 0.95f, "matched text");

        assertEquals(chunk, result.getChunk());
        assertEquals(0.9f, result.getScore(), 0.01f);
        assertEquals(0.8f, result.getBm25Score(), 0.01f);
        assertEquals(0.95f, result.getVectorScore(), 0.01f);
        assertEquals("matched text", result.getSnippet());
    }

    @Test
    @DisplayName("Builder 构建")
    void builder() {
        MemoryChunk chunk = createChunk("c2");
        SearchResult result = SearchResult.builder()
                .chunk(chunk)
                .score(0.85f)
                .bm25Score(0.7f)
                .vectorScore(0.9f)
                .snippet("search snippet")
                .build();

        assertEquals(chunk, result.getChunk());
        assertEquals(0.85f, result.getScore(), 0.01f);
        assertEquals("search snippet", result.getSnippet());
    }

    @Test
    @DisplayName("toString 包含关键信息")
    void toStringOutput() {
        MemoryChunk chunk = createChunk("c3");
        SearchResult result = new SearchResult(chunk, 0.9f, 0.8f, 0.95f, "a short snippet");
        String str = result.toString();
        assertTrue(str.contains("c3"));
        assertTrue(str.contains("0.9"));
    }

    @Test
    @DisplayName("toString snippet 为 null 不抛异常")
    void toStringNullSnippet() {
        MemoryChunk chunk = createChunk("c4");
        SearchResult result = new SearchResult(chunk, 0.5f, 0.3f, 0.7f, null);
        assertDoesNotThrow(() -> result.toString());
    }
}
