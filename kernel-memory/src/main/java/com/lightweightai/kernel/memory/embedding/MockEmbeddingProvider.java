package com.lightweightai.kernel.memory.embedding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Mock embedding provider for testing.
 * Generates deterministic embeddings based on text hash.
 */
public class MockEmbeddingProvider implements EmbeddingProvider {

    private final int dimensions;

    public MockEmbeddingProvider() {
        this(128);  // Small dimension for testing
    }

    public MockEmbeddingProvider(int dimensions) {
        this.dimensions = dimensions;
    }

    @Override
    public float[] embed(String text) {
        return generateEmbedding(text);
    }

    @Override
    public CompletableFuture<float[]> embedAsync(String text) {
        return CompletableFuture.completedFuture(embed(text));
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> results = new ArrayList<>();
        for (String text : texts) {
            results.add(embed(text));
        }
        return results;
    }

    @Override
    public CompletableFuture<List<float[]>> embedBatchAsync(List<String> texts) {
        return CompletableFuture.completedFuture(embedBatch(texts));
    }

    @Override
    public int getDimensions() {
        return dimensions;
    }

    @Override
    public String getProviderName() {
        return "mock";
    }

    @Override
    public String getModelName() {
        return "mock-embedding-v1";
    }

    /**
     * Generate a deterministic embedding from text.
     * Uses hash to create consistent vectors for the same text.
     */
    private float[] generateEmbedding(String text) {
        float[] embedding = new float[dimensions];

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));

            // Use hash bytes to seed the embedding values
            for (int i = 0; i < dimensions; i++) {
                int byteIndex = i % hash.length;
                // Map byte value (-128 to 127) to float (-1 to 1)
                embedding[i] = hash[byteIndex] / 128.0f;
            }

            // Normalize the vector
            float norm = 0;
            for (float v : embedding) {
                norm += v * v;
            }
            norm = (float) Math.sqrt(norm);
            if (norm > 0) {
                for (int i = 0; i < dimensions; i++) {
                    embedding[i] /= norm;
                }
            }
        } catch (Exception e) {
            // Fill with zeros if hash fails
            for (int i = 0; i < dimensions; i++) {
                embedding[i] = 0;
            }
        }

        return embedding;
    }
}
