package com.lightweightai.kernel.memory.index;

import com.lightweightai.kernel.memory.embedding.EmbeddingProvider;
import com.lightweightai.kernel.memory.model.MemoryChunk;
import com.lightweightai.kernel.memory.model.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Hybrid search combining BM25 (keyword) and Vector (semantic) search.
 *
 * Uses Reciprocal Rank Fusion (RRF) to combine results from both methods.
 * Default weighting: 70% vector + 30% BM25 (configurable).
 *
 * Inspired by OpenClaw's hybrid search approach.
 */
public class HybridSearch {

    private static final Logger log = LoggerFactory.getLogger(HybridSearch.class);

    // RRF constant (typically 60)
    private static final float RRF_K = 60f;

    private final MemoryIndex memoryIndex;
    private final VectorIndex vectorIndex;

    public HybridSearch(MemoryIndex memoryIndex, VectorIndex vectorIndex) {
        this.memoryIndex = memoryIndex;
        this.vectorIndex = vectorIndex;
    }

    /**
     * Perform hybrid search with default vector weight (0.7).
     */
    public List<SearchResult> search(String query, int topK) {
        return search(query, topK, 0.7f);
    }

    /**
     * Perform hybrid search with configurable vector weight.
     *
     * @param query        The search query
     * @param topK         Number of results to return
     * @param vectorWeight Weight for vector results (0.0 to 1.0). BM25 weight = 1 - vectorWeight.
     * @return Fused search results sorted by combined score
     */
    public List<SearchResult> search(String query, int topK, float vectorWeight) {
        vectorWeight = Math.max(0f, Math.min(1f, vectorWeight));
        float bm25Weight = 1f - vectorWeight;

        // Get more results than needed for better fusion
        int fetchK = topK * 3;

        // Get BM25 results
        List<SearchResult> bm25Results = memoryIndex.searchBM25(query, fetchK);
        log.debug("BM25 search returned {} results", bm25Results.size());

        // Get vector results
        List<SearchResult> vectorResults = vectorIndex.search(query, fetchK);
        log.debug("Vector search returned {} results", vectorResults.size());

        // Fuse results using RRF
        Map<String, FusedScore> fusedScores = new HashMap<>();

        // Process BM25 results
        for (int rank = 0; rank < bm25Results.size(); rank++) {
            SearchResult result = bm25Results.get(rank);
            String id = result.getChunk().getId();
            float rrfScore = bm25Weight / (RRF_K + rank + 1);

            fusedScores.computeIfAbsent(id, k -> new FusedScore(result.getChunk()))
                .addBm25Score(rrfScore, result.getBm25Score(), result.getSnippet());
        }

        // Process vector results
        for (int rank = 0; rank < vectorResults.size(); rank++) {
            SearchResult result = vectorResults.get(rank);
            String id = result.getChunk().getId();
            float rrfScore = vectorWeight / (RRF_K + rank + 1);

            fusedScores.computeIfAbsent(id, k -> new FusedScore(result.getChunk()))
                .addVectorScore(rrfScore, result.getVectorScore(), result.getSnippet());
        }

        // Sort by fused score and return top K
        List<SearchResult> results = fusedScores.values().stream()
            .sorted((a, b) -> Float.compare(b.totalScore, a.totalScore))
            .limit(topK)
            .map(FusedScore::toSearchResult)
            .collect(Collectors.toList());

        log.debug("Hybrid search returned {} results for query: '{}'", results.size(), truncate(query, 50));
        return results;
    }

    /**
     * Search using only BM25 (keyword search).
     */
    public List<SearchResult> searchBM25Only(String query, int topK) {
        return memoryIndex.searchBM25(query, topK);
    }

    /**
     * Search using only vector similarity.
     */
    public List<SearchResult> searchVectorOnly(String query, int topK) {
        return vectorIndex.search(query, topK);
    }

    /**
     * Index a chunk in both indexes.
     */
    public void indexChunk(MemoryChunk chunk) {
        memoryIndex.upsertChunk(chunk);
        vectorIndex.addChunk(chunk);
    }

    /**
     * Index multiple chunks.
     */
    public void indexChunks(List<MemoryChunk> chunks) {
        memoryIndex.insertChunks(chunks);
        vectorIndex.addChunks(chunks);
    }

    /**
     * Remove a chunk from both indexes.
     */
    public void removeChunk(String chunkId) {
        vectorIndex.removeChunk(chunkId);
        // Note: MemoryIndex removal would need to be by chunk ID
    }

    /**
     * Get statistics about the indexes.
     */
    public IndexStats getStats() {
        return new IndexStats(
            memoryIndex.getChunkCount(),
            vectorIndex.size(),
            vectorIndex.getEmbeddingProvider().getDimensions()
        );
    }

    // Helper classes

    private static class FusedScore {
        final MemoryChunk chunk;
        float totalScore = 0;
        float bm25Score = 0;
        float vectorScore = 0;
        String snippet;

        FusedScore(MemoryChunk chunk) {
            this.chunk = chunk;
        }

        void addBm25Score(float rrfScore, float rawScore, String snippet) {
            this.totalScore += rrfScore;
            this.bm25Score = rawScore;
            if (this.snippet == null) {
                this.snippet = snippet;
            }
        }

        void addVectorScore(float rrfScore, float rawScore, String snippet) {
            this.totalScore += rrfScore;
            this.vectorScore = rawScore;
            if (this.snippet == null) {
                this.snippet = snippet;
            }
        }

        SearchResult toSearchResult() {
            return SearchResult.builder()
                .chunk(chunk)
                .score(totalScore)
                .bm25Score(bm25Score)
                .vectorScore(vectorScore)
                .snippet(snippet)
                .build();
        }
    }

    public static final class IndexStats {
        private final int bm25ChunkCount;
        private final int vectorChunkCount;
        private final int embeddingDimensions;

        public IndexStats(int bm25ChunkCount, int vectorChunkCount, int embeddingDimensions) {
            this.bm25ChunkCount = bm25ChunkCount;
            this.vectorChunkCount = vectorChunkCount;
            this.embeddingDimensions = embeddingDimensions;
        }

        public int bm25ChunkCount() { return bm25ChunkCount; }
        public int vectorChunkCount() { return vectorChunkCount; }
        public int embeddingDimensions() { return embeddingDimensions; }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
