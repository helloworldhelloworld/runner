package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SearchResult")
class SearchResultTest {

    private MemoryChunk createChunk(String id) {
        return MemoryChunk.builder()
                .id(id)
                .content("test content for " + id)
                .hash("hash-" + id)
                .sourceFile("source.md")
                .build();
    }

    @Test
    void shouldCreateWithConstructor() {
        MemoryChunk chunk = createChunk("c1");
        SearchResult result = new SearchResult(chunk, 0.85f, 0.6f, 0.9f, "matching snippet");

        assertSame(chunk, result.getChunk());
        assertEquals(0.85f, result.getScore(), 0.01f);
        assertEquals(0.6f, result.getBm25Score(), 0.01f);
        assertEquals(0.9f, result.getVectorScore(), 0.01f);
        assertEquals("matching snippet", result.getSnippet());
    }

    @Test
    void shouldCreateWithBuilder() {
        MemoryChunk chunk = createChunk("c2");
        SearchResult result = SearchResult.builder()
                .chunk(chunk)
                .score(0.75f)
                .bm25Score(0.5f)
                .vectorScore(0.8f)
                .snippet("builder snippet")
                .build();

        assertEquals(0.75f, result.getScore(), 0.01f);
        assertEquals("builder snippet", result.getSnippet());
    }

    @Test
    void shouldIncludeChunkIdInToString() {
        MemoryChunk chunk = createChunk("test-id");
        SearchResult result = new SearchResult(chunk, 0.9f, 0.5f, 0.8f, "some snippet text");

        String str = result.toString();
        assertTrue(str.contains("test-id"));
        assertTrue(str.contains("0.9"));
    }

    @Test
    void shouldTruncateLongSnippetInToString() {
        MemoryChunk chunk = createChunk("c3");
        String longSnippet = "a".repeat(100);
        SearchResult result = new SearchResult(chunk, 0.5f, 0.3f, 0.6f, longSnippet);

        String str = result.toString();
        // toString truncates to 50 chars + "..."
        assertTrue(str.contains("..."));
    }
}
