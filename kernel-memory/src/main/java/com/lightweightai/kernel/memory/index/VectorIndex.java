package com.lightweightai.kernel.memory.index;

import com.lightweightai.kernel.memory.embedding.EmbeddingProvider;
import com.lightweightai.kernel.memory.model.MemoryChunk;
import com.lightweightai.kernel.memory.model.SearchResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-memory vector index for semantic search.
 * Uses cosine similarity for vector comparison.
 *
 * For production with large datasets, consider using:
 * - sqlite-vec (SQLite extension)
 * - Pinecone, Weaviate, or other vector databases
 */
public class VectorIndex {

    private static final Logger log = LoggerFactory.getLogger(VectorIndex.class);

    private final EmbeddingProvider embeddingProvider;
    private final Map<String, VectorEntry> vectors = new ConcurrentHashMap<>();

    public VectorIndex(EmbeddingProvider embeddingProvider) {
        this.embeddingProvider = embeddingProvider;
    }

    /**
     * Add a chunk to the vector index.
     * If the chunk doesn't have an embedding, one will be generated.
     */
    public void addChunk(MemoryChunk chunk) {
        float[] embedding = chunk.getEmbedding();

        if (embedding == null || embedding.length == 0) {
            embedding = embeddingProvider.embed(chunk.getContent());
            chunk.setEmbedding(embedding);
        }

        vectors.put(chunk.getId(), new VectorEntry(chunk.getId(), embedding, chunk));
        log.debug("Added vector for chunk: {}", chunk.getId());
    }

    /**
     * Add multiple chunks to the index.
     */
    public void addChunks(List<MemoryChunk> chunks) {
        // Batch embed chunks that don't have embeddings
        List<MemoryChunk> needsEmbedding = new ArrayList<>();
        List<String> textsToEmbed = new ArrayList<>();

        for (MemoryChunk chunk : chunks) {
            if (!chunk.hasEmbedding()) {
                needsEmbedding.add(chunk);
                textsToEmbed.add(chunk.getContent());
            }
        }

        // Batch embed
        if (!textsToEmbed.isEmpty()) {
            List<float[]> embeddings = embeddingProvider.embedBatch(textsToEmbed);
            for (int i = 0; i < needsEmbedding.size(); i++) {
                needsEmbedding.get(i).setEmbedding(embeddings.get(i));
            }
        }

        // Add all to index
        for (MemoryChunk chunk : chunks) {
            vectors.put(chunk.getId(), new VectorEntry(chunk.getId(), chunk.getEmbedding(), chunk));
        }

        log.info("Added {} vectors to index", chunks.size());
    }

    /**
     * Remove a chunk from the index.
     */
    public void removeChunk(String chunkId) {
        vectors.remove(chunkId);
    }

    /**
     * Search for similar vectors using cosine similarity.
     */
    public List<SearchResult> search(String query, int topK) {
        float[] queryEmbedding = embeddingProvider.embed(query);
        return searchByVector(queryEmbedding, topK);
    }

    /**
     * Search by embedding vector.
     */
    public List<SearchResult> searchByVector(float[] queryEmbedding, int topK) {
        if (vectors.isEmpty()) {
            return List.of();
        }

        // Calculate similarity for all vectors
        List<ScoredEntry> scored = new ArrayList<>();
        for (VectorEntry entry : vectors.values()) {
            float similarity = cosineSimilarity(queryEmbedding, entry.embedding);
            scored.add(new ScoredEntry(entry, similarity));
        }

        // Sort by similarity (descending) and take top K
        scored.sort((a, b) -> Float.compare(b.score, a.score));

        List<SearchResult> results = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, scored.size()); i++) {
            ScoredEntry se = scored.get(i);
            results.add(SearchResult.builder()
                .chunk(se.entry.chunk)
                .score(se.score)
                .bm25Score(0f)
                .vectorScore(se.score)
                .snippet(extractSnippet(se.entry.chunk.getContent(), 700))
                .build());
        }

        return results;
    }

    /**
     * Get the number of vectors in the index.
     */
    public int size() {
        return vectors.size();
    }

    /**
     * Clear all vectors.
     */
    public void clear() {
        vectors.clear();
    }

    /**
     * Check if a chunk exists in the index.
     */
    public boolean contains(String chunkId) {
        return vectors.containsKey(chunkId);
    }

    /**
     * Get the embedding provider.
     */
    public EmbeddingProvider getEmbeddingProvider() {
        return embeddingProvider;
    }

    // Helper methods

    /**
     * Calculate cosine similarity between two vectors.
     */
    public static float cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vectors must have same dimension");
        }

        float dotProduct = 0;
        float normA = 0;
        float normB = 0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0 || normB == 0) {
            return 0;
        }

        return dotProduct / (float) (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private String extractSnippet(String content, int maxLength) {
        if (content == null || content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }

    // Inner classes

    private static class VectorEntry {
        final String id;
        final float[] embedding;
        final MemoryChunk chunk;

        VectorEntry(String id, float[] embedding, MemoryChunk chunk) {
            this.id = id;
            this.embedding = embedding;
            this.chunk = chunk;
        }
    }

    private static class ScoredEntry {
        final VectorEntry entry;
        final float score;

        ScoredEntry(VectorEntry entry, float score) {
            this.entry = entry;
            this.score = score;
        }
    }
}
