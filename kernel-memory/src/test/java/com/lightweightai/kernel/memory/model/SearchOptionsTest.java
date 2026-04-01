package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SearchOptions - 搜索选项")
class SearchOptionsTest {

    @Test
    @DisplayName("默认值")
    void defaults() {
        SearchOptions opts = SearchOptions.defaults();

        assertEquals(10, opts.getTopK());
        assertEquals(0.7f, opts.getVectorWeight(), 0.01f);
        assertEquals(Set.of(MemoryType.values()), opts.getMemoryTypes());
        assertNull(opts.getSessionId());
        assertEquals(700, opts.getSnippetLength());
    }

    @Test
    @DisplayName("Builder 自定义所有字段")
    void builderCustom() {
        SearchOptions opts = SearchOptions.builder()
                .topK(5)
                .vectorWeight(0.3f)
                .memoryTypes(Set.of(MemoryType.DURABLE))
                .sessionId("session-1")
                .snippetLength(500)
                .build();

        assertEquals(5, opts.getTopK());
        assertEquals(0.3f, opts.getVectorWeight(), 0.01f);
        assertEquals(Set.of(MemoryType.DURABLE), opts.getMemoryTypes());
        assertEquals("session-1", opts.getSessionId());
        assertEquals(500, opts.getSnippetLength());
    }

    @Test
    @DisplayName("vectorWeight 被 clamp 到 [0, 1]")
    void vectorWeightClamped() {
        SearchOptions high = SearchOptions.builder().vectorWeight(1.5f).build();
        assertEquals(1.0f, high.getVectorWeight(), 0.01f);

        SearchOptions low = SearchOptions.builder().vectorWeight(-0.5f).build();
        assertEquals(0.0f, low.getVectorWeight(), 0.01f);
    }
}
