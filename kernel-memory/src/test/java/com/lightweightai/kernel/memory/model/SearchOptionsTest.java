package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.*;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SearchOptions} — builder pattern with validation.
 *
 * Covers: defaults, builder setters, vectorWeight clamping.
 */
@DisplayName("SearchOptions - 搜索配置")
class SearchOptionsTest {

    @Test
    @DisplayName("defaults — 所有默认值正确")
    void defaults_allCorrect() {
        SearchOptions opts = SearchOptions.defaults();

        assertEquals(10, opts.getTopK());
        assertEquals(0.7f, opts.getVectorWeight(), 0.001f);
        assertEquals(Set.of(MemoryType.values()), opts.getMemoryTypes());
        assertNull(opts.getSessionId());
        assertEquals(700, opts.getSnippetLength());
    }

    @Test
    @DisplayName("builder — 设置所有字段")
    void builder_setsAllFields() {
        SearchOptions opts = SearchOptions.builder()
            .topK(5)
            .vectorWeight(0.3f)
            .memoryTypes(Set.of(MemoryType.DURABLE))
            .sessionId("sess-1")
            .snippetLength(500)
            .build();

        assertEquals(5, opts.getTopK());
        assertEquals(0.3f, opts.getVectorWeight(), 0.001f);
        assertEquals(Set.of(MemoryType.DURABLE), opts.getMemoryTypes());
        assertEquals("sess-1", opts.getSessionId());
        assertEquals(500, opts.getSnippetLength());
    }

    @Nested
    @DisplayName("vectorWeight 范围限制")
    class VectorWeightClamping {

        @Test
        @DisplayName("超过 1.0 — 钳位到 1.0")
        void aboveOne_clampedToOne() {
            SearchOptions opts = SearchOptions.builder().vectorWeight(1.5f).build();
            assertEquals(1.0f, opts.getVectorWeight(), 0.001f);
        }

        @Test
        @DisplayName("低于 0.0 — 钳位到 0.0")
        void belowZero_clampedToZero() {
            SearchOptions opts = SearchOptions.builder().vectorWeight(-0.5f).build();
            assertEquals(0.0f, opts.getVectorWeight(), 0.001f);
        }

        @Test
        @DisplayName("边界值 0.0 — 保留")
        void exactZero_preserved() {
            SearchOptions opts = SearchOptions.builder().vectorWeight(0.0f).build();
            assertEquals(0.0f, opts.getVectorWeight(), 0.001f);
        }

        @Test
        @DisplayName("边界值 1.0 — 保留")
        void exactOne_preserved() {
            SearchOptions opts = SearchOptions.builder().vectorWeight(1.0f).build();
            assertEquals(1.0f, opts.getVectorWeight(), 0.001f);
        }
    }
}
