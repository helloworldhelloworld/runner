package com.lightweightai.kernel.memory.embedding;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for embedding providers that convert text to vector representations.
 */
public interface EmbeddingProvider {

    /**
     * Embed a single text.
     */
    float[] embed(String text);

    /**
     * Embed a single text asynchronously.
     */
    CompletableFuture<float[]> embedAsync(String text);

    /**
     * Embed multiple texts in a batch for efficiency.
     */
    List<float[]> embedBatch(List<String> texts);

    /**
     * Embed multiple texts in a batch asynchronously.
     */
    CompletableFuture<List<float[]>> embedBatchAsync(List<String> texts);

    /**
     * Get the dimension of embeddings produced by this provider.
     */
    int getDimensions();

    /**
     * Get the provider name.
     */
    String getProviderName();

    /**
     * Get the model name.
     */
    String getModelName();
}
