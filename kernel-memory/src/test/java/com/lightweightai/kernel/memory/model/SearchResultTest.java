package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SearchResult — 混合搜索结果")
class SearchResultTest {

    private MemoryChunk sampleChunk() {
        return MemoryChunk.builder()
                .id("c1")
                .content("sample content")
                .sourceFile("test.md")
                .startLine(1)
                .endLine(5)
                .hash("hash1")
                .createdAt(Instant.now())
                .type(MemoryType.DURABLE)
                .build();
    }

    @Test
    @DisplayName("构造函数设置所有字段")
    void constructorSetsAllFields() {
        MemoryChunk chunk = sampleChunk();
        SearchResult result = new SearchResult(chunk, 0.85f, 0.7f, 0.9f, "...sample...");

        assertSame(chunk, result.getChunk());
        assertEquals(0.85f, result.getScore(), 0.001f);
        assertEquals(0.7f, result.getBm25Score(), 0.001f);
        assertEquals(0.9f, result.getVectorScore(), 0.001f);
        assertEquals("...sample...", result.getSnippet());
    }

    @Test
    @DisplayName("Builder 构造等价")
    void builderCreatesEquivalent() {
        MemoryChunk chunk = sampleChunk();
        SearchResult result = SearchResult.builder()
                .chunk(chunk)
                .score(0.85f)
                .bm25Score(0.7f)
                .vectorScore(0.9f)
                .snippet("...snippet...")
                .build();

        assertSame(chunk, result.getChunk());
        assertEquals(0.85f, result.getScore(), 0.001f);
        assertEquals("...snippet...", result.getSnippet());
    }

    @Test
    @DisplayName("toString 包含 chunk id 和 score")
    void toStringFormat() {
        SearchResult result = new SearchResult(sampleChunk(), 0.92f, 0.8f, 0.95f,
                "a long snippet that exceeds fifty characters to test truncation behavior");
        String str = result.toString();
        assertTrue(str.contains("c1"));
        assertTrue(str.contains("0.92"));
    }

    @Test
    @DisplayName("snippet 为 null 时 toString 不崩溃")
    void nullSnippetInToString() {
        SearchResult result = new SearchResult(sampleChunk(), 0.5f, 0.3f, 0.7f, null);
        assertDoesNotThrow(result::toString);
    }
}
