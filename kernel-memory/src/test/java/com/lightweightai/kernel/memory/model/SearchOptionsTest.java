package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SearchOptions - 搜索参数")
class SearchOptionsTest {

    @Test
    @DisplayName("defaults 包含合理默认值")
    void defaultsHaveReasonableValues() {
        SearchOptions opts = SearchOptions.defaults();

        assertEquals(10, opts.getTopK());
        assertEquals(0.7f, opts.getVectorWeight(), 0.001f);
        assertEquals(Set.of(MemoryType.values()), opts.getMemoryTypes());
        assertNull(opts.getSessionId());
        assertEquals(700, opts.getSnippetLength());
    }

    @Test
    @DisplayName("Builder 设置所有字段")
    void builderSetsAllFields() {
        SearchOptions opts = SearchOptions.builder()
            .topK(5)
            .vectorWeight(0.3f)
            .memoryTypes(Set.of(MemoryType.DURABLE))
            .sessionId("s1")
            .snippetLength(200)
            .build();

        assertEquals(5, opts.getTopK());
        assertEquals(0.3f, opts.getVectorWeight(), 0.001f);
        assertEquals(Set.of(MemoryType.DURABLE), opts.getMemoryTypes());
        assertEquals("s1", opts.getSessionId());
        assertEquals(200, opts.getSnippetLength());
    }

    @Test
    @DisplayName("vectorWeight 被 clamp 到 [0, 1]")
    void vectorWeightClamped() {
        SearchOptions high = SearchOptions.builder().vectorWeight(2.0f).build();
        assertEquals(1.0f, high.getVectorWeight(), 0.001f);

        SearchOptions low = SearchOptions.builder().vectorWeight(-0.5f).build();
        assertEquals(0.0f, low.getVectorWeight(), 0.001f);
    }
}
