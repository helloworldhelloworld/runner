package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SearchOptions")
class SearchOptionsTest {

    @Nested
    @DisplayName("defaults()")
    class DefaultsTests {

        @Test
        @DisplayName("returns default topK of 10")
        void defaultTopK() {
            SearchOptions opts = SearchOptions.defaults();
            assertEquals(10, opts.getTopK());
        }

        @Test
        @DisplayName("returns default vectorWeight of 0.7")
        void defaultVectorWeight() {
            SearchOptions opts = SearchOptions.defaults();
            assertEquals(0.7f, opts.getVectorWeight(), 0.001f);
        }

        @Test
        @DisplayName("returns all MemoryTypes by default")
        void defaultMemoryTypes() {
            SearchOptions opts = SearchOptions.defaults();
            Set<MemoryType> types = opts.getMemoryTypes();

            assertEquals(Set.of(MemoryType.values()), types);
            assertTrue(types.contains(MemoryType.EPHEMERAL));
            assertTrue(types.contains(MemoryType.DURABLE));
            assertTrue(types.contains(MemoryType.SESSION));
        }

        @Test
        @DisplayName("returns null sessionId by default")
        void defaultSessionId() {
            SearchOptions opts = SearchOptions.defaults();
            assertNull(opts.getSessionId());
        }

        @Test
        @DisplayName("returns default snippetLength of 700")
        void defaultSnippetLength() {
            SearchOptions opts = SearchOptions.defaults();
            assertEquals(700, opts.getSnippetLength());
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("builds with all custom values")
        void allCustomValues() {
            Set<MemoryType> types = Set.of(MemoryType.DURABLE, MemoryType.SESSION);
            SearchOptions opts = SearchOptions.builder()
                    .topK(20)
                    .vectorWeight(0.5f)
                    .memoryTypes(types)
                    .sessionId("sess-123")
                    .snippetLength(300)
                    .build();

            assertEquals(20, opts.getTopK());
            assertEquals(0.5f, opts.getVectorWeight(), 0.001f);
            assertEquals(types, opts.getMemoryTypes());
            assertEquals("sess-123", opts.getSessionId());
            assertEquals(300, opts.getSnippetLength());
        }

        @Test
        @DisplayName("clamps vectorWeight above 1.0 to 1.0")
        void vectorWeightClampedAbove() {
            SearchOptions opts = SearchOptions.builder()
                    .vectorWeight(1.5f)
                    .build();

            assertEquals(1.0f, opts.getVectorWeight(), 0.001f);
        }

        @Test
        @DisplayName("clamps vectorWeight below 0.0 to 0.0")
        void vectorWeightClampedBelow() {
            SearchOptions opts = SearchOptions.builder()
                    .vectorWeight(-0.3f)
                    .build();

            assertEquals(0.0f, opts.getVectorWeight(), 0.001f);
        }

        @Test
        @DisplayName("vectorWeight at boundary 0.0 stays 0.0")
        void vectorWeightAtZero() {
            SearchOptions opts = SearchOptions.builder()
                    .vectorWeight(0.0f)
                    .build();

            assertEquals(0.0f, opts.getVectorWeight(), 0.001f);
        }

        @Test
        @DisplayName("vectorWeight at boundary 1.0 stays 1.0")
        void vectorWeightAtOne() {
            SearchOptions opts = SearchOptions.builder()
                    .vectorWeight(1.0f)
                    .build();

            assertEquals(1.0f, opts.getVectorWeight(), 0.001f);
        }

        @Test
        @DisplayName("builder can set a single MemoryType")
        void singleMemoryType() {
            SearchOptions opts = SearchOptions.builder()
                    .memoryTypes(Set.of(MemoryType.EPHEMERAL))
                    .build();

            assertEquals(1, opts.getMemoryTypes().size());
            assertTrue(opts.getMemoryTypes().contains(MemoryType.EPHEMERAL));
        }

        @Test
        @DisplayName("builder produces independent instances")
        void builderIndependence() {
            SearchOptions.Builder builder = SearchOptions.builder();
            SearchOptions opts1 = builder.topK(5).build();
            SearchOptions opts2 = builder.topK(15).build();

            assertEquals(5, opts1.getTopK());
            assertEquals(15, opts2.getTopK());
        }
    }
}
