package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SearchOptions — 记忆搜索选项")
class SearchOptionsTest {

    @Test
    @DisplayName("默认值正确")
    void defaultValues() {
        SearchOptions opts = SearchOptions.defaults();

        assertEquals(10, opts.getTopK());
        assertEquals(0.7f, opts.getVectorWeight(), 0.001f);
        assertEquals(Set.of(MemoryType.values()), opts.getMemoryTypes());
        assertNull(opts.getSessionId());
        assertEquals(700, opts.getSnippetLength());
    }

    @Test
    @DisplayName("Builder 设置所有字段")
    void builderSetsFields() {
        SearchOptions opts = SearchOptions.builder()
                .topK(5)
                .vectorWeight(0.3f)
                .memoryTypes(Set.of(MemoryType.DURABLE))
                .sessionId("sess-1")
                .snippetLength(200)
                .build();

        assertEquals(5, opts.getTopK());
        assertEquals(0.3f, opts.getVectorWeight(), 0.001f);
        assertEquals(Set.of(MemoryType.DURABLE), opts.getMemoryTypes());
        assertEquals("sess-1", opts.getSessionId());
        assertEquals(200, opts.getSnippetLength());
    }

    @Test
    @DisplayName("vectorWeight 上限截断到 1.0")
    void vectorWeightClampedHigh() {
        SearchOptions opts = SearchOptions.builder()
                .vectorWeight(2.0f)
                .build();

        assertEquals(1.0f, opts.getVectorWeight(), 0.001f);
    }

    @Test
    @DisplayName("vectorWeight 下限截断到 0.0")
    void vectorWeightClampedLow() {
        SearchOptions opts = SearchOptions.builder()
                .vectorWeight(-0.5f)
                .build();

        assertEquals(0.0f, opts.getVectorWeight(), 0.001f);
    }
}
