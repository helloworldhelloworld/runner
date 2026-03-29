package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SearchResult - hybrid search result")
class SearchResultTest {

    private MemoryChunk createChunk(String id) {
        return MemoryChunk.builder()
            .id(id)
            .content("test content")
            .hash("hash-" + id)
            .build();
    }

    @Test
    void constructor_setsAllFields() {
        MemoryChunk chunk = createChunk("c1");
        SearchResult result = new SearchResult(chunk, 0.95f, 0.8f, 0.9f, "matched snippet");

        assertSame(chunk, result.getChunk());
        assertEquals(0.95f, result.getScore(), 0.001f);
        assertEquals(0.8f, result.getBm25Score(), 0.001f);
        assertEquals(0.9f, result.getVectorScore(), 0.001f);
        assertEquals("matched snippet", result.getSnippet());
    }

    @Test
    void builder_setsAllFields() {
        MemoryChunk chunk = createChunk("c2");
        SearchResult result = SearchResult.builder()
            .chunk(chunk)
            .score(0.85f)
            .bm25Score(0.7f)
            .vectorScore(0.9f)
            .snippet("some text")
            .build();

        assertEquals(0.85f, result.getScore(), 0.001f);
        assertEquals("some text", result.getSnippet());
    }

    @Test
    void toString_containsChunkIdAndScore() {
        MemoryChunk chunk = createChunk("result-1");
        SearchResult result = new SearchResult(chunk, 0.75f, 0.5f, 0.8f, "snippet text here");

        String str = result.toString();
        assertTrue(str.contains("result-1"));
        assertTrue(str.contains("0.75"));
    }

    @Test
    void toString_truncatesLongSnippet() {
        MemoryChunk chunk = createChunk("c3");
        String longSnippet = "a".repeat(100);
        SearchResult result = new SearchResult(chunk, 0.5f, 0.3f, 0.7f, longSnippet);

        String str = result.toString();
        assertTrue(str.contains("..."));
    }
}
