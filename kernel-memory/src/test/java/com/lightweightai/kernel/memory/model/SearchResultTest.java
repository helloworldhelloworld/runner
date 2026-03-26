package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SearchResult - 搜索结果模型")
class SearchResultTest {

    private MemoryChunk createChunk(String id) {
        return MemoryChunk.builder()
                .id(id).content("content").hash("h1").build();
    }

    @Test
    @DisplayName("builder 创建完整对象")
    void shouldBuildCompleteResult() {
        MemoryChunk chunk = createChunk("c-1");
        SearchResult result = SearchResult.builder()
                .chunk(chunk)
                .score(0.95f)
                .bm25Score(0.8f)
                .vectorScore(0.9f)
                .snippet("This is a test snippet")
                .build();

        assertSame(chunk, result.getChunk());
        assertEquals(0.95f, result.getScore(), 0.001f);
        assertEquals(0.8f, result.getBm25Score(), 0.001f);
        assertEquals(0.9f, result.getVectorScore(), 0.001f);
        assertEquals("This is a test snippet", result.getSnippet());
    }

    @Test
    @DisplayName("构造函数创建对象")
    void shouldCreateViaConstructor() {
        MemoryChunk chunk = createChunk("c-2");
        SearchResult result = new SearchResult(chunk, 0.85f, 0.7f, 0.9f, "snippet text");

        assertEquals(0.85f, result.getScore(), 0.001f);
        assertEquals("snippet text", result.getSnippet());
    }

    @Test
    @DisplayName("toString 截断长 snippet 到 50 字符")
    void shouldTruncateSnippetInToString() {
        MemoryChunk chunk = createChunk("c-3");
        String longSnippet = "A".repeat(100);
        SearchResult result = SearchResult.builder()
                .chunk(chunk).score(0.5f).snippet(longSnippet).build();

        String str = result.toString();
        assertTrue(str.contains("c-3"));
        // Snippet truncated to 50 chars + "..."
        assertTrue(str.contains("AAAAAAAAAA"));
    }

    @Test
    @DisplayName("toString 处理 null snippet")
    void shouldHandleNullSnippetInToString() {
        MemoryChunk chunk = createChunk("c-4");
        SearchResult result = SearchResult.builder()
                .chunk(chunk).score(0.5f).snippet(null).build();

        String str = result.toString();
        assertTrue(str.contains("null"));
    }

    @Test
    @DisplayName("短 snippet 不截断")
    void shouldNotTruncateShortSnippet() {
        MemoryChunk chunk = createChunk("c-5");
        SearchResult result = SearchResult.builder()
                .chunk(chunk).score(0.5f).snippet("short").build();

        String str = result.toString();
        assertTrue(str.contains("short"));
    }
}
