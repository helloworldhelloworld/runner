package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SearchOptions")
class SearchOptionsTest {

    @Test
    void shouldBuildWithDefaults() {
        SearchOptions options = SearchOptions.defaults();

        assertEquals(10, options.getTopK());
        assertEquals(0.7f, options.getVectorWeight(), 0.01f);
        assertNotNull(options.getMemoryTypes());
        assertTrue(options.getMemoryTypes().containsAll(Set.of(MemoryType.values())));
        assertNull(options.getSessionId());
        assertEquals(700, options.getSnippetLength());
    }

    @Test
    void shouldBuildWithCustomValues() {
        SearchOptions options = SearchOptions.builder()
                .topK(5)
                .vectorWeight(0.3f)
                .memoryTypes(Set.of(MemoryType.DURABLE))
                .sessionId("sess-1")
                .snippetLength(500)
                .build();

        assertEquals(5, options.getTopK());
        assertEquals(0.3f, options.getVectorWeight(), 0.01f);
        assertEquals(Set.of(MemoryType.DURABLE), options.getMemoryTypes());
        assertEquals("sess-1", options.getSessionId());
        assertEquals(500, options.getSnippetLength());
    }

    @Test
    void shouldClampVectorWeightToRange() {
        SearchOptions above = SearchOptions.builder().vectorWeight(1.5f).build();
        SearchOptions below = SearchOptions.builder().vectorWeight(-0.5f).build();

        assertEquals(1.0f, above.getVectorWeight(), 0.01f);
        assertEquals(0.0f, below.getVectorWeight(), 0.01f);
    }

    @Test
    void shouldAcceptBoundaryVectorWeights() {
        SearchOptions zero = SearchOptions.builder().vectorWeight(0.0f).build();
        SearchOptions one = SearchOptions.builder().vectorWeight(1.0f).build();

        assertEquals(0.0f, zero.getVectorWeight(), 0.01f);
        assertEquals(1.0f, one.getVectorWeight(), 0.01f);
    }
}
