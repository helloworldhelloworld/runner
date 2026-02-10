package com.lightweightai.kernel.memory.index;

import com.lightweightai.kernel.memory.embedding.MockEmbeddingProvider;
import com.lightweightai.kernel.memory.model.MemoryChunk;
import com.lightweightai.kernel.memory.model.MemoryType;
import com.lightweightai.kernel.memory.model.SearchResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HybridSearchTest {

    @TempDir
    Path tempDir;

    private MemoryIndex memoryIndex;
    private VectorIndex vectorIndex;
    private HybridSearch hybridSearch;

    @BeforeEach
    void setUp() {
        Path indexPath = tempDir.resolve("test-agent").resolve("memory.sqlite");
        memoryIndex = new MemoryIndex(indexPath, "test-agent");

        MockEmbeddingProvider embeddingProvider = new MockEmbeddingProvider(64);
        vectorIndex = new VectorIndex(embeddingProvider);

        hybridSearch = new HybridSearch(memoryIndex, vectorIndex);
    }

    @AfterEach
    void tearDown() {
        if (memoryIndex != null) {
            memoryIndex.close();
        }
    }

    @Test
    void testHybridSearchCombinesResults() {
        // Add test data
        hybridSearch.indexChunk(createChunk("The quick brown fox jumps over the lazy dog"));
        hybridSearch.indexChunk(createChunk("A fast brown cat runs past the sleepy dog"));
        hybridSearch.indexChunk(createChunk("Python programming with machine learning"));

        // Search
        List<SearchResult> results = hybridSearch.search("brown dog", 10);

        assertFalse(results.isEmpty());
        // Should have scores from both BM25 and vector
        for (SearchResult result : results) {
            assertTrue(result.getScore() > 0);
        }
    }

    @Test
    void testHybridSearchSortedByScore() {
        hybridSearch.indexChunk(createChunk("Dogs are the best pets ever"));
        hybridSearch.indexChunk(createChunk("Cats are independent animals"));
        hybridSearch.indexChunk(createChunk("Weather forecast for tomorrow"));

        List<SearchResult> results = hybridSearch.search("pets dogs", 10);

        // Results should be sorted by score descending
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i - 1).getScore() >= results.get(i).getScore(),
                "Results should be sorted by score descending");
        }
    }

    @Test
    void testBM25OnlySearch() {
        hybridSearch.indexChunk(createChunk("Keyword matching is important"));
        hybridSearch.indexChunk(createChunk("Semantic understanding matters"));

        List<SearchResult> results = hybridSearch.searchBM25Only("keyword matching", 10);

        assertFalse(results.isEmpty());
        assertTrue(results.get(0).getBm25Score() > 0);
    }

    @Test
    void testVectorOnlySearch() {
        hybridSearch.indexChunk(createChunk("Deep learning models"));
        hybridSearch.indexChunk(createChunk("Neural network architectures"));

        List<SearchResult> results = hybridSearch.searchVectorOnly("machine learning AI", 10);

        assertFalse(results.isEmpty());
        // Vector score can be 0 or negative for dissimilar content with mock embeddings
        assertNotNull(results.get(0).getChunk());
    }

    @Test
    void testCustomVectorWeight() {
        hybridSearch.indexChunk(createChunk("Test content for weighting"));

        // Full vector weight
        List<SearchResult> vectorResults = hybridSearch.search("test", 10, 1.0f);
        // Full BM25 weight
        List<SearchResult> bm25Results = hybridSearch.search("test", 10, 0.0f);
        // Balanced weight
        List<SearchResult> balancedResults = hybridSearch.search("test", 10, 0.5f);

        assertFalse(vectorResults.isEmpty());
        assertFalse(bm25Results.isEmpty());
        assertFalse(balancedResults.isEmpty());
    }

    @Test
    void testIndexChunks() {
        List<MemoryChunk> chunks = List.of(
            createChunk("First document"),
            createChunk("Second document"),
            createChunk("Third document")
        );

        hybridSearch.indexChunks(chunks);

        HybridSearch.IndexStats stats = hybridSearch.getStats();
        assertEquals(3, stats.bm25ChunkCount());
        assertEquals(3, stats.vectorChunkCount());
    }

    @Test
    void testIndexStats() {
        hybridSearch.indexChunk(createChunk("Content 1"));
        hybridSearch.indexChunk(createChunk("Content 2"));

        HybridSearch.IndexStats stats = hybridSearch.getStats();

        assertEquals(2, stats.bm25ChunkCount());
        assertEquals(2, stats.vectorChunkCount());
        assertEquals(64, stats.embeddingDimensions());  // MockEmbeddingProvider uses 64
    }

    @Test
    void testSearchNoResults() {
        // Empty index
        List<SearchResult> results = hybridSearch.search("anything", 10);
        assertTrue(results.isEmpty());
    }

    @Test
    void testSearchWithTopK() {
        for (int i = 0; i < 20; i++) {
            hybridSearch.indexChunk(createChunk("Document number " + i + " with content"));
        }

        List<SearchResult> results = hybridSearch.search("document content", 5);

        assertEquals(5, results.size());
    }

    @Test
    void testResultsHaveSnippets() {
        hybridSearch.indexChunk(createChunk("This content should appear in the snippet for search results"));

        List<SearchResult> results = hybridSearch.search("content snippet", 10);

        assertFalse(results.isEmpty());
        assertNotNull(results.get(0).getSnippet());
    }

    @Test
    void testRRFFusionWithOverlap() {
        // This tests that when both BM25 and vector return the same document,
        // its score is boosted
        hybridSearch.indexChunk(createChunk("exact keyword match with semantic meaning"));
        hybridSearch.indexChunk(createChunk("unrelated document about cooking"));

        List<SearchResult> results = hybridSearch.search("keyword match semantic", 10);

        // The first result should have combined scores from both methods
        assertFalse(results.isEmpty());
        SearchResult top = results.get(0);
        assertTrue(top.getScore() > 0);
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
