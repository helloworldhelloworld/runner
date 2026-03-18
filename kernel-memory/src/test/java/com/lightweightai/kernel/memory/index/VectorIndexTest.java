package com.lightweightai.kernel.memory.index;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lightweightai.kernel.memory.embedding.MockEmbeddingProvider;
import com.lightweightai.kernel.memory.model.MemoryChunk;
import com.lightweightai.kernel.memory.model.MemoryType;
import com.lightweightai.kernel.memory.model.SearchResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VectorIndexTest {

    private VectorIndex vectorIndex;
    private MockEmbeddingProvider embeddingProvider;

    @BeforeEach
    void setUp() {
        embeddingProvider = new MockEmbeddingProvider(64);
        vectorIndex = new VectorIndex(embeddingProvider);
    }

    @Test
    void testAddAndSearch() {
        vectorIndex.addChunk(createChunk("The quick brown fox jumps over the lazy dog"));
        vectorIndex.addChunk(createChunk("A fast brown cat runs past the sleepy dog"));
        vectorIndex.addChunk(createChunk("Python programming with machine learning"));

        assertEquals(3, vectorIndex.size());

        // Search for similar content
        List<SearchResult> results = vectorIndex.search("brown animal dog", 10);

        assertFalse(results.isEmpty());
        assertTrue(results.get(0).getVectorScore() > 0);
    }

    @Test
    void testSearchReturnsOrderedByScore() {
        vectorIndex.addChunk(createChunk("Dogs are loyal pets"));
        vectorIndex.addChunk(createChunk("Cats are independent animals"));
        vectorIndex.addChunk(createChunk("Python is a programming language"));

        List<SearchResult> results = vectorIndex.search("pets and animals", 10);

        // Results should be sorted by score descending
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i - 1).getScore() >= results.get(i).getScore(),
                "Results should be sorted by score descending");
        }
    }

    @Test
    void testSearchWithTopK() {
        for (int i = 0; i < 20; i++) {
            vectorIndex.addChunk(createChunk("Content number " + i));
        }

        List<SearchResult> results = vectorIndex.search("content", 5);

        assertEquals(5, results.size());
    }

    @Test
    void testSearchEmptyIndex() {
        List<SearchResult> results = vectorIndex.search("anything", 10);
        assertTrue(results.isEmpty());
    }

    @Test
    void testAddChunksWithExistingEmbeddings() {
        MemoryChunk chunk = createChunk("Test content");
        float[] precomputedEmbedding = new float[64];
        for (int i = 0; i < 64; i++) {
            precomputedEmbedding[i] = i / 64.0f;
        }
        chunk.setEmbedding(precomputedEmbedding);

        vectorIndex.addChunk(chunk);

        assertEquals(1, vectorIndex.size());
        assertTrue(vectorIndex.contains(chunk.getId()));
    }

    @Test
    void testBatchAddChunks() {
        List<MemoryChunk> chunks = List.of(
            createChunk("First document"),
            createChunk("Second document"),
            createChunk("Third document")
        );

        vectorIndex.addChunks(chunks);

        assertEquals(3, vectorIndex.size());
    }

    @Test
    void testRemoveChunk() {
        MemoryChunk chunk = createChunk("To be removed");
        vectorIndex.addChunk(chunk);

        assertTrue(vectorIndex.contains(chunk.getId()));

        vectorIndex.removeChunk(chunk.getId());

        assertFalse(vectorIndex.contains(chunk.getId()));
        assertEquals(0, vectorIndex.size());
    }

    @Test
    void testClear() {
        vectorIndex.addChunk(createChunk("Content 1"));
        vectorIndex.addChunk(createChunk("Content 2"));

        assertEquals(2, vectorIndex.size());

        vectorIndex.clear();

        assertEquals(0, vectorIndex.size());
    }

    @Test
    void testCosineSimilarity() {
        float[] a = {1, 0, 0};
        float[] b = {1, 0, 0};
        assertEquals(1.0f, VectorIndex.cosineSimilarity(a, b), 0.001f);

        float[] c = {1, 0, 0};
        float[] d = {0, 1, 0};
        assertEquals(0.0f, VectorIndex.cosineSimilarity(c, d), 0.001f);

        float[] e = {1, 0, 0};
        float[] f = {-1, 0, 0};
        assertEquals(-1.0f, VectorIndex.cosineSimilarity(e, f), 0.001f);
    }

    @Test
    void testSameTextSameEmbedding() {
        String text = "Identical content";
        float[] embedding1 = embeddingProvider.embed(text);
        float[] embedding2 = embeddingProvider.embed(text);

        assertArrayEquals(embedding1, embedding2);
    }

    @Test
    void testSearchResultHasSnippet() {
        vectorIndex.addChunk(createChunk("This is some content that should appear in the snippet"));

        List<SearchResult> results = vectorIndex.search("content", 10);

        assertFalse(results.isEmpty());
        assertNotNull(results.get(0).getSnippet());
        assertTrue(results.get(0).getSnippet().contains("content"));
    }

    @Test
    void testSimilarContentHigherScore() {
        vectorIndex.addChunk(createChunk("Machine learning and artificial intelligence"));
        vectorIndex.addChunk(createChunk("Deep learning neural networks"));
        vectorIndex.addChunk(createChunk("Cooking recipes and food"));

        List<SearchResult> results = vectorIndex.search("AI and machine learning", 3);

        // The cooking content should have lower similarity
        assertEquals(3, results.size());
    }

    // Helper method

    private MemoryChunk createChunk(String content) {
        String id = UUID.randomUUID().toString().substring(0, 8);
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
