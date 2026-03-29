package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SearchOptions - memory search configuration")
class SearchOptionsTest {

    @Test
    void defaults_hasReasonableValues() {
        SearchOptions opts = SearchOptions.defaults();

        assertEquals(10, opts.getTopK());
        assertEquals(0.7f, opts.getVectorWeight(), 0.001f);
        assertNotNull(opts.getMemoryTypes());
        assertEquals(Set.of(MemoryType.values()), opts.getMemoryTypes());
        assertNull(opts.getSessionId());
        assertEquals(700, opts.getSnippetLength());
    }

    @Test
    void builder_setsAllFields() {
        SearchOptions opts = SearchOptions.builder()
            .topK(5)
            .vectorWeight(0.3f)
            .memoryTypes(Set.of(MemoryType.DURABLE))
            .sessionId("session-1")
            .snippetLength(500)
            .build();

        assertEquals(5, opts.getTopK());
        assertEquals(0.3f, opts.getVectorWeight(), 0.001f);
        assertEquals(Set.of(MemoryType.DURABLE), opts.getMemoryTypes());
        assertEquals("session-1", opts.getSessionId());
        assertEquals(500, opts.getSnippetLength());
    }

    @Test
    void vectorWeight_clampedToZero() {
        SearchOptions opts = SearchOptions.builder()
            .vectorWeight(-0.5f)
            .build();

        assertEquals(0f, opts.getVectorWeight(), 0.001f);
    }

    @Test
    void vectorWeight_clampedToOne() {
        SearchOptions opts = SearchOptions.builder()
            .vectorWeight(1.5f)
            .build();

        assertEquals(1f, opts.getVectorWeight(), 0.001f);
    }
}
