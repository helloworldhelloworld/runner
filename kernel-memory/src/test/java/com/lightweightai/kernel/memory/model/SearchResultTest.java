package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SearchResult")
class SearchResultTest {

    private MemoryChunk sampleChunk(String id) {
        return MemoryChunk.builder()
                .id(id)
                .content("sample content for " + id)
                .sourceFile("source.md")
                .startLine(1)
                .endLine(10)
                .hash("hash-" + id)
                .createdAt(Instant.parse("2025-03-01T00:00:00Z"))
                .type(MemoryType.DURABLE)
                .build();
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("stores all fields from constructor arguments")
        void allFieldsStored() {
            MemoryChunk chunk = sampleChunk("c1");
            SearchResult result = new SearchResult(chunk, 0.95f, 0.8f, 0.9f, "matched snippet");

            assertSame(chunk, result.getChunk());
            assertEquals(0.95f, result.getScore(), 0.001f);
            assertEquals(0.8f, result.getBm25Score(), 0.001f);
            assertEquals(0.9f, result.getVectorScore(), 0.001f);
            assertEquals("matched snippet", result.getSnippet());
        }

        @Test
        @DisplayName("allows null snippet")
        void nullSnippet() {
            MemoryChunk chunk = sampleChunk("c2");
            SearchResult result = new SearchResult(chunk, 0.5f, 0.3f, 0.7f, null);

            assertNull(result.getSnippet());
        }

        @Test
        @DisplayName("stores zero scores correctly")
        void zeroScores() {
            MemoryChunk chunk = sampleChunk("c3");
            SearchResult result = new SearchResult(chunk, 0.0f, 0.0f, 0.0f, "");

            assertEquals(0.0f, result.getScore(), 0.001f);
            assertEquals(0.0f, result.getBm25Score(), 0.001f);
            assertEquals(0.0f, result.getVectorScore(), 0.001f);
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("builds SearchResult with all fields via builder")
        void buildsAllFields() {
            MemoryChunk chunk = sampleChunk("b1");
            SearchResult result = SearchResult.builder()
                    .chunk(chunk)
                    .score(0.85f)
                    .bm25Score(0.6f)
                    .vectorScore(0.9f)
                    .snippet("builder snippet")
                    .build();

            assertSame(chunk, result.getChunk());
            assertEquals(0.85f, result.getScore(), 0.001f);
            assertEquals(0.6f, result.getBm25Score(), 0.001f);
            assertEquals(0.9f, result.getVectorScore(), 0.001f);
            assertEquals("builder snippet", result.getSnippet());
        }

        @Test
        @DisplayName("builder defaults scores to 0.0 when not set")
        void defaultScoresAreZero() {
            MemoryChunk chunk = sampleChunk("b2");
            SearchResult result = SearchResult.builder()
                    .chunk(chunk)
                    .build();

            assertEquals(0.0f, result.getScore(), 0.001f);
            assertEquals(0.0f, result.getBm25Score(), 0.001f);
            assertEquals(0.0f, result.getVectorScore(), 0.001f);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("includes chunk id and score")
        void containsIdAndScore() {
            MemoryChunk chunk = sampleChunk("ts-1");
            SearchResult result = new SearchResult(chunk, 0.75f, 0.5f, 0.8f, "a snippet here");

            String str = result.toString();
            assertTrue(str.contains("ts-1"), "should contain chunk id");
            assertTrue(str.contains("0.75"), "should contain score");
        }

        @Test
        @DisplayName("truncates long snippets in toString")
        void truncatesLongSnippet() {
            MemoryChunk chunk = sampleChunk("ts-2");
            String longSnippet = "A".repeat(100);
            SearchResult result = new SearchResult(chunk, 0.5f, 0.3f, 0.4f, longSnippet);

            String str = result.toString();
            // toString truncates snippet to min(50, length) + "..."
            assertTrue(str.contains("..."), "should contain ellipsis for truncated snippet");
            // The truncated portion should be at most 50 chars of the original
            assertFalse(str.contains(longSnippet), "should not contain full 100-char snippet");
        }

        @Test
        @DisplayName("handles null snippet in toString")
        void nullSnippetInToString() {
            MemoryChunk chunk = sampleChunk("ts-3");
            SearchResult result = new SearchResult(chunk, 0.5f, 0.3f, 0.4f, null);

            String str = result.toString();
            assertTrue(str.contains("null"), "should show 'null' for null snippet");
        }
    }
}
