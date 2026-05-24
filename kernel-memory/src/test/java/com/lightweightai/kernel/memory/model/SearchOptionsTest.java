package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SearchOptions — memory search configuration with builder")
class SearchOptionsTest {

    @Test
    @DisplayName("defaults() provides sensible default values")
    void defaultsAreSensible() {
        SearchOptions opts = SearchOptions.defaults();

        assertEquals(10, opts.getTopK());
        assertEquals(0.7f, opts.getVectorWeight(), 0.001f);
        assertTrue(opts.getMemoryTypes().containsAll(Set.of(MemoryType.values())));
        assertNull(opts.getSessionId());
        assertEquals(700, opts.getSnippetLength());
    }

    @Test
    @DisplayName("builder overrides defaults")
    void builderOverrides() {
        SearchOptions opts = SearchOptions.builder()
                .topK(5)
                .vectorWeight(0.9f)
                .memoryTypes(Set.of(MemoryType.DURABLE))
                .sessionId("session-123")
                .snippetLength(300)
                .build();

        assertEquals(5, opts.getTopK());
        assertEquals(0.9f, opts.getVectorWeight(), 0.001f);
        assertEquals(Set.of(MemoryType.DURABLE), opts.getMemoryTypes());
        assertEquals("session-123", opts.getSessionId());
        assertEquals(300, opts.getSnippetLength());
    }

    @Test
    @DisplayName("vectorWeight is clamped to [0.0, 1.0]")
    void vectorWeightClamped() {
        SearchOptions tooHigh = SearchOptions.builder().vectorWeight(1.5f).build();
        assertEquals(1.0f, tooHigh.getVectorWeight(), 0.001f);

        SearchOptions tooLow = SearchOptions.builder().vectorWeight(-0.5f).build();
        assertEquals(0.0f, tooLow.getVectorWeight(), 0.001f);
    }

    @Test
    @DisplayName("vectorWeight at boundaries is preserved")
    void vectorWeightBoundaries() {
        SearchOptions zero = SearchOptions.builder().vectorWeight(0.0f).build();
        assertEquals(0.0f, zero.getVectorWeight(), 0.001f);

        SearchOptions one = SearchOptions.builder().vectorWeight(1.0f).build();
        assertEquals(1.0f, one.getVectorWeight(), 0.001f);
    }

    @Test
    @DisplayName("multiple memoryTypes are supported")
    void multipleMemoryTypes() {
        SearchOptions opts = SearchOptions.builder()
                .memoryTypes(Set.of(MemoryType.EPHEMERAL, MemoryType.SESSION))
                .build();

        assertEquals(2, opts.getMemoryTypes().size());
        assertTrue(opts.getMemoryTypes().contains(MemoryType.EPHEMERAL));
        assertTrue(opts.getMemoryTypes().contains(MemoryType.SESSION));
        assertFalse(opts.getMemoryTypes().contains(MemoryType.DURABLE));
    }
}
