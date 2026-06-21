package com.lightweightai.kernel.memory.index;

import com.lightweightai.kernel.memory.embedding.MockEmbeddingProvider;
import com.lightweightai.kernel.memory.model.MemoryChunk;
import com.lightweightai.kernel.memory.model.MemoryType;
import com.lightweightai.kernel.memory.model.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("VectorIndex - Edge Cases")
class VectorIndexEdgeCaseTest {

    private VectorIndex vectorIndex;
    private MockEmbeddingProvider embeddingProvider;

    @BeforeEach
    void setUp() {
        embeddingProvider = new MockEmbeddingProvider(64);
        vectorIndex = new VectorIndex(embeddingProvider);
    }

    // ==================== cosineSimilarity edge cases ====================

    @Nested
    @DisplayName("cosineSimilarity static method")
    class CosineSimilarityEdgeCases {

        @Test
        @DisplayName("Zero vector returns 0 (not NaN or Infinity)")
        void zeroVectorReturnsZero() {
            float[] zero = {0, 0, 0, 0};
            float[] nonZero = {1, 2, 3, 4};

            float result = VectorIndex.cosineSimilarity(zero, nonZero);

            assertEquals(0f, result);
            assertFalse(Float.isNaN(result));
            assertFalse(Float.isInfinite(result));
        }

        @Test
        @DisplayName("Both zero vectors returns 0 (not NaN)")
        void bothZeroVectorsReturnsZero() {
            float[] zeroA = {0, 0, 0};
            float[] zeroB = {0, 0, 0};

            float result = VectorIndex.cosineSimilarity(zeroA, zeroB);

            assertEquals(0f, result);
            assertFalse(Float.isNaN(result));
        }

        @Test
        @DisplayName("Mismatched dimensions throws IllegalArgumentException")
        void mismatchedDimensionsThrows() {
            float[] a = {1, 2, 3};
            float[] b = {1, 2};

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> VectorIndex.cosineSimilarity(a, b));

            assertEquals("Vectors must have same dimension", ex.getMessage());
        }

        @Test
        @DisplayName("Single-element vectors compute correctly")
        void singleElementVectors() {
            // Same direction
            assertEquals(1.0f, VectorIndex.cosineSimilarity(new float[]{3}, new float[]{7}), 0.001f);
            // Opposite direction
            assertEquals(-1.0f, VectorIndex.cosineSimilarity(new float[]{3}, new float[]{-7}), 0.001f);
        }

        @Test
        @DisplayName("Very large float values (near Float.MAX_VALUE) compute without throwing")
        void veryLargeFloatValues() {
            float large = Float.MAX_VALUE / 100;
            float[] a = {large, large};
            float[] b = {large, large};

            float result = VectorIndex.cosineSimilarity(a, b);

            // Parallel vectors should have similarity close to 1, but overflow may produce
            // Infinity or NaN depending on float arithmetic
            // The key assertion: it does not throw an exception
            // With Float.MAX_VALUE/100, the norms will overflow to Infinity, giving 0/Inf = NaN or similar
            // This documents the behavior rather than asserting correctness
            assertTrue(Float.isNaN(result) || Math.abs(result) <= 1.0f || Float.isInfinite(result),
                "Result should be NaN, Infinity, or a valid similarity when overflow occurs");
        }

        @Test
        @DisplayName("Moderately large values still compute correct similarity")
        void moderatelyLargeValues() {
            // Use values that won't overflow when squared
            float large = 1000f;
            float[] a = {large, large, large};
            float[] b = {large, large, large};

            float result = VectorIndex.cosineSimilarity(a, b);

            assertEquals(1.0f, result, 0.001f);
        }

        @Test
        @DisplayName("Very small float values (near Float.MIN_VALUE) compute without returning NaN")
        void verySmallFloatValues() {
            float tiny = Float.MIN_VALUE;
            float[] a = {tiny, tiny, tiny};
            float[] b = {tiny, tiny, tiny};

            float result = VectorIndex.cosineSimilarity(a, b);

            // With extremely small values, the squared norms may underflow to 0,
            // triggering the zero-norm guard and returning 0
            assertEquals(0f, result,
                "Extremely small values underflow to zero norm, triggering the zero-guard");
        }

        @Test
        @DisplayName("Small but not subnormal values compute correctly")
        void smallButNotSubnormalValues() {
            float small = 1e-20f;
            float[] a = {small, small, small};
            float[] b = {small, small, small};

            float result = VectorIndex.cosineSimilarity(a, b);

            // These values are small enough to potentially underflow when squared
            // If they underflow, we get 0 from the guard. If not, we get 1.0
            assertTrue(result == 0f || Math.abs(result - 1.0f) < 0.01f,
                "Should return 0 (underflow guard) or 1.0 (correct cosine for parallel vectors)");
        }

        @Test
        @DisplayName("NaN values propagate through computation (documents behavior)")
        void nanValuesBehavior() {
            float[] a = {Float.NaN, 1, 2};
            float[] b = {1, 2, 3};

            float result = VectorIndex.cosineSimilarity(a, b);

            // NaN in arithmetic propagates: dotProduct becomes NaN, normA becomes NaN
            // The comparison normA == 0 with NaN is false, so it proceeds to division
            // NaN / anything = NaN
            assertTrue(Float.isNaN(result),
                "NaN in input should propagate to NaN result");
        }

        @Test
        @DisplayName("NaN in second vector also produces NaN result")
        void nanInSecondVectorBehavior() {
            float[] a = {1, 2, 3};
            float[] b = {Float.NaN, 1, 2};

            float result = VectorIndex.cosineSimilarity(a, b);

            assertTrue(Float.isNaN(result),
                "NaN in second vector should also propagate to NaN result");
        }

        @Test
        @DisplayName("Empty vectors (length 0) returns 0 for equal empty arrays")
        void emptyVectorsDoNotThrow() {
            float[] a = {};
            float[] b = {};

            // Both have length 0, so a.length == b.length passes
            // Loop doesn't execute, normA=0, normB=0 -> returns 0
            float result = VectorIndex.cosineSimilarity(a, b);
            assertEquals(0f, result);
        }
    }

    // ==================== Search edge cases ====================

    @Nested
    @DisplayName("Search behavior edge cases")
    class SearchEdgeCases {

        @Test
        @DisplayName("topK larger than index size returns all available items")
        void topKLargerThanIndexSizeReturnsAll() {
            vectorIndex.addChunk(createChunk("doc-1", "First document content"));
            vectorIndex.addChunk(createChunk("doc-2", "Second document content"));
            vectorIndex.addChunk(createChunk("doc-3", "Third document content"));

            List<SearchResult> results = vectorIndex.search("document", 100);

            assertEquals(3, results.size(), "Should return all 3 items when topK=100 > size=3");
        }

        @Test
        @DisplayName("topK = 0 returns empty list")
        void topKZeroReturnsEmpty() {
            vectorIndex.addChunk(createChunk("doc-1", "Some content here"));
            vectorIndex.addChunk(createChunk("doc-2", "More content there"));

            List<SearchResult> results = vectorIndex.search("content", 0);

            assertTrue(results.isEmpty(), "topK=0 should return empty list");
        }

        @Test
        @DisplayName("topK = 1 returns exactly one result")
        void topKOneReturnsSingleResult() {
            vectorIndex.addChunk(createChunk("doc-1", "First item"));
            vectorIndex.addChunk(createChunk("doc-2", "Second item"));
            vectorIndex.addChunk(createChunk("doc-3", "Third item"));

            List<SearchResult> results = vectorIndex.search("item", 1);

            assertEquals(1, results.size());
        }

        @Test
        @DisplayName("Search on empty index returns empty list")
        void searchEmptyIndexReturnsEmptyList() {
            List<SearchResult> results = vectorIndex.search("anything", 10);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("searchByVector with zero vector returns results with score 0")
        void searchByVectorWithZeroVector() {
            vectorIndex.addChunk(createChunk("doc-1", "Some text"));

            float[] zeroQuery = new float[64];
            List<SearchResult> results = vectorIndex.searchByVector(zeroQuery, 10);

            // The zero-norm guard in cosineSimilarity returns 0 for any comparison with zero vector
            assertFalse(results.isEmpty());
            assertEquals(0f, results.get(0).getVectorScore(), 0.001f);
        }
    }

    // ==================== addChunk edge cases ====================

    @Nested
    @DisplayName("addChunk embedding behavior")
    class AddChunkEdgeCases {

        @Test
        @DisplayName("addChunk with null embedding triggers provider embed call")
        void addChunkNullEmbeddingTriggersProvider() {
            MemoryChunk chunk = createChunk("test-1", "Content to embed");
            // chunk has no embedding set (null by default)

            assertNull(chunk.getEmbedding(), "Precondition: embedding should be null before add");

            vectorIndex.addChunk(chunk);

            assertNotNull(chunk.getEmbedding(), "Embedding should be set after addChunk");
            assertEquals(64, chunk.getEmbedding().length, "Embedding should have provider dimensions");
            assertTrue(vectorIndex.contains("test-1"));
        }

        @Test
        @DisplayName("addChunk with empty embedding array triggers provider embed call")
        void addChunkEmptyEmbeddingTriggersProvider() {
            MemoryChunk chunk = createChunk("test-2", "Content to embed");
            chunk.setEmbedding(new float[0]); // empty array

            vectorIndex.addChunk(chunk);

            assertNotNull(chunk.getEmbedding());
            assertEquals(64, chunk.getEmbedding().length,
                "Empty embedding should be replaced by provider-generated one");
        }

        @Test
        @DisplayName("addChunk with pre-computed embedding does NOT call provider")
        void addChunkPreComputedEmbeddingSkipsProvider() {
            MemoryChunk chunk = createChunk("test-3", "Content");
            float[] custom = new float[64];
            custom[0] = 0.99f;
            custom[1] = -0.5f;
            chunk.setEmbedding(custom);

            vectorIndex.addChunk(chunk);

            // Embedding should remain unchanged (not overwritten by provider)
            assertEquals(0.99f, chunk.getEmbedding()[0], 0.001f);
            assertEquals(-0.5f, chunk.getEmbedding()[1], 0.001f);
        }

        @Test
        @DisplayName("addChunk with same ID overwrites previous entry")
        void addChunkSameIdOverwrites() {
            MemoryChunk chunk1 = createChunk("same-id", "First version");
            MemoryChunk chunk2 = createChunk("same-id", "Second version");

            vectorIndex.addChunk(chunk1);
            vectorIndex.addChunk(chunk2);

            assertEquals(1, vectorIndex.size(), "Same ID should overwrite, not duplicate");
        }
    }

    // ==================== addChunks batch edge cases ====================

    @Nested
    @DisplayName("addChunks batch behavior")
    class AddChunksBatchEdgeCases {

        @Test
        @DisplayName("Batch with mix of pre-embedded and non-embedded chunks")
        void batchMixedEmbeddingStatus() {
            MemoryChunk preEmbedded = createChunk("pre-1", "Already embedded");
            float[] customEmbed = new float[64];
            customEmbed[0] = 0.42f;
            preEmbedded.setEmbedding(customEmbed);

            MemoryChunk needsEmbed1 = createChunk("need-1", "Needs embedding one");
            MemoryChunk needsEmbed2 = createChunk("need-2", "Needs embedding two");

            vectorIndex.addChunks(List.of(preEmbedded, needsEmbed1, needsEmbed2));

            assertEquals(3, vectorIndex.size());

            // Pre-embedded chunk retains its custom embedding
            assertEquals(0.42f, preEmbedded.getEmbedding()[0], 0.001f);

            // Non-embedded chunks now have embeddings from provider
            assertNotNull(needsEmbed1.getEmbedding());
            assertEquals(64, needsEmbed1.getEmbedding().length);
            assertNotNull(needsEmbed2.getEmbedding());
            assertEquals(64, needsEmbed2.getEmbedding().length);
        }

        @Test
        @DisplayName("Batch with all pre-embedded chunks skips batch embed call")
        void batchAllPreEmbeddedSkipsBatchEmbed() {
            MemoryChunk chunk1 = createChunk("a", "Content A");
            MemoryChunk chunk2 = createChunk("b", "Content B");
            float[] embed = new float[64];
            embed[0] = 1.0f;
            chunk1.setEmbedding(embed.clone());
            chunk2.setEmbedding(embed.clone());

            vectorIndex.addChunks(List.of(chunk1, chunk2));

            assertEquals(2, vectorIndex.size());
            // Both retain their original embeddings
            assertEquals(1.0f, chunk1.getEmbedding()[0], 0.001f);
            assertEquals(1.0f, chunk2.getEmbedding()[0], 0.001f);
        }

        @Test
        @DisplayName("Batch with all non-embedded chunks triggers batch embed")
        void batchAllNonEmbeddedTriggersBatchEmbed() {
            MemoryChunk chunk1 = createChunk("x", "Text X");
            MemoryChunk chunk2 = createChunk("y", "Text Y");
            MemoryChunk chunk3 = createChunk("z", "Text Z");

            assertNull(chunk1.getEmbedding());
            assertNull(chunk2.getEmbedding());
            assertNull(chunk3.getEmbedding());

            vectorIndex.addChunks(List.of(chunk1, chunk2, chunk3));

            assertEquals(3, vectorIndex.size());
            assertNotNull(chunk1.getEmbedding());
            assertNotNull(chunk2.getEmbedding());
            assertNotNull(chunk3.getEmbedding());
        }

        @Test
        @DisplayName("Batch with empty list does not throw")
        void batchEmptyListDoesNotThrow() {
            assertDoesNotThrow(() -> vectorIndex.addChunks(List.of()));
            assertEquals(0, vectorIndex.size());
        }
    }

    // ==================== Helper ====================

    private MemoryChunk createChunk(String id, String content) {
        return MemoryChunk.builder()
            .id(id)
            .content(content)
            .sourceFile("test.md")
            .startLine(1)
            .endLine(10)
            .hash("hash-" + id)
            .type(MemoryType.DURABLE)
            .createdAt(Instant.now())
            .build();
    }
}
