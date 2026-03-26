package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SearchOptions - 搜索选项")
class SearchOptionsTest {

    @Test
    @DisplayName("defaults() 返回合理默认值")
    void shouldReturnSensibleDefaults() {
        SearchOptions opts = SearchOptions.defaults();

        assertEquals(10, opts.getTopK());
        assertEquals(0.7f, opts.getVectorWeight(), 0.001f);
        assertEquals(Set.of(MemoryType.values()), opts.getMemoryTypes());
        assertNull(opts.getSessionId());
        assertEquals(700, opts.getSnippetLength());
    }

    @Test
    @DisplayName("builder 设置所有字段")
    void shouldSetAllFieldsViaBuilder() {
        SearchOptions opts = SearchOptions.builder()
                .topK(5)
                .vectorWeight(0.5f)
                .memoryTypes(Set.of(MemoryType.DURABLE))
                .sessionId("sess-1")
                .snippetLength(300)
                .build();

        assertEquals(5, opts.getTopK());
        assertEquals(0.5f, opts.getVectorWeight(), 0.001f);
        assertEquals(Set.of(MemoryType.DURABLE), opts.getMemoryTypes());
        assertEquals("sess-1", opts.getSessionId());
        assertEquals(300, opts.getSnippetLength());
    }

    @Test
    @DisplayName("vectorWeight 超过 1 被钳制为 1")
    void shouldClampVectorWeightAboveOne() {
        SearchOptions opts = SearchOptions.builder().vectorWeight(1.5f).build();
        assertEquals(1.0f, opts.getVectorWeight(), 0.001f);
    }

    @Test
    @DisplayName("vectorWeight 低于 0 被钳制为 0")
    void shouldClampVectorWeightBelowZero() {
        SearchOptions opts = SearchOptions.builder().vectorWeight(-0.5f).build();
        assertEquals(0.0f, opts.getVectorWeight(), 0.001f);
    }

    @Test
    @DisplayName("vectorWeight 边界值 0 和 1")
    void shouldAcceptBoundaryValues() {
        SearchOptions zero = SearchOptions.builder().vectorWeight(0f).build();
        assertEquals(0.0f, zero.getVectorWeight(), 0.001f);

        SearchOptions one = SearchOptions.builder().vectorWeight(1f).build();
        assertEquals(1.0f, one.getVectorWeight(), 0.001f);
    }
}
