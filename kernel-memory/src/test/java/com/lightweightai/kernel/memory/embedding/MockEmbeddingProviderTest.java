package com.lightweightai.kernel.memory.embedding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MockEmbeddingProvider - Deterministic embedding generation")
class MockEmbeddingProviderTest {

    // ==================== Constructor defaults ====================

    @Test
    @DisplayName("Default constructor uses 128 dimensions")
    void defaultConstructorUses128Dimensions() {
        MockEmbeddingProvider provider = new MockEmbeddingProvider();

        assertEquals(128, provider.getDimensions());
    }

    @Test
    @DisplayName("Custom dimensions are respected")
    void customDimensionsRespected() {
        MockEmbeddingProvider provider = new MockEmbeddingProvider(256);

        assertEquals(256, provider.getDimensions());
    }

    // ==================== embed() ====================

    @Test
    @DisplayName("embed returns array of correct length matching dimensions")
    void embedReturnsCorrectLengthArray() {
        MockEmbeddingProvider provider = new MockEmbeddingProvider(64);

        float[] embedding = provider.embed("hello world");

        assertEquals(64, embedding.length);
    }

    @Test
    @DisplayName("embed returns array of correct length for default dimensions")
    void embedReturnsCorrectLengthForDefaultDimensions() {
        MockEmbeddingProvider provider = new MockEmbeddingProvider();

        float[] embedding = provider.embed("test text");

        assertEquals(128, embedding.length);
    }

    @Test
    @DisplayName("embed is deterministic - same text produces same embedding")
    void embedIsDeterministic() {
        MockEmbeddingProvider provider = new MockEmbeddingProvider(64);

        float[] first = provider.embed("hello world");
        float[] second = provider.embed("hello world");

        assertArrayEquals(first, second,
                "Same text must produce identical embeddings");
    }

    @Test
    @DisplayName("embed is deterministic across separate provider instances")
    void embedDeterministicAcrossInstances() {
        MockEmbeddingProvider provider1 = new MockEmbeddingProvider(64);
        MockEmbeddingProvider provider2 = new MockEmbeddingProvider(64);

        float[] first = provider1.embed("determinism check");
        float[] second = provider2.embed("determinism check");

        assertArrayEquals(first, second,
                "Different provider instances must produce identical embeddings for same text");
    }

    @Test
    @DisplayName("embed produces different results for different texts")
    void embedProducesDifferentResultsForDifferentTexts() {
        MockEmbeddingProvider provider = new MockEmbeddingProvider(64);

        float[] embedding1 = provider.embed("hello world");
        float[] embedding2 = provider.embed("goodbye world");

        assertFalse(Arrays.equals(embedding1, embedding2),
                "Different texts must produce different embeddings");
    }

    @Test
    @DisplayName("embed produces normalized vector with L2 norm approximately 1.0")
    void embedProducesNormalizedVector() {
        MockEmbeddingProvider provider = new MockEmbeddingProvider(128);

        float[] embedding = provider.embed("normalization test");

        double norm = 0.0;
        for (float v : embedding) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);

        assertEquals(1.0, norm, 0.001,
                "L2 norm of embedding should be approximately 1.0");
    }

    @Test
    @DisplayName("embed produces normalized vector for various inputs")
    void embedNormalizedForVariousInputs() {
        MockEmbeddingProvider provider = new MockEmbeddingProvider(256);

        String[] texts = {"short", "a longer piece of text with many words",
                "12345", "!@#$%^&*()", ""};

        for (String text : texts) {
            float[] embedding = provider.embed(text);
            double norm = 0.0;
            for (float v : embedding) {
                norm += v * v;
            }
            norm = Math.sqrt(norm);

            assertEquals(1.0, norm, 0.001,
                    "L2 norm should be ~1.0 for text: \"" + text + "\"");
        }
    }

    @Test
    @DisplayName("embed produces non-zero values in the vector")
    void embedProducesNonZeroValues() {
        MockEmbeddingProvider provider = new MockEmbeddingProvider(64);

        float[] embedding = provider.embed("non-zero test");

        boolean hasNonZero = false;
        for (float v : embedding) {
            if (v != 0.0f) {
                hasNonZero = true;
                break;
            }
        }
        assertTrue(hasNonZero, "Embedding should contain non-zero values");
    }

    // ==================== embedAsync() ====================

    @Test
    @DisplayName("embedAsync returns same result as synchronous embed")
    void embedAsyncReturnsSameResultAsSync() throws Exception {
        MockEmbeddingProvider provider = new MockEmbeddingProvider(64);
        String text = "async test";

        float[] syncResult = provider.embed(text);
        CompletableFuture<float[]> future = provider.embedAsync(text);
        float[] asyncResult = future.get();

        assertArrayEquals(syncResult, asyncResult,
                "Async result must match sync result");
    }

    @Test
    @DisplayName("embedAsync returns completed future")
    void embedAsyncReturnsCompletedFuture() {
        MockEmbeddingProvider provider = new MockEmbeddingProvider(64);

        CompletableFuture<float[]> future = provider.embedAsync("test");

        assertTrue(future.isDone(), "Future should be already completed");
        assertFalse(future.isCompletedExceptionally(),
                "Future should not be completed exceptionally");
    }

    // ==================== embedBatch() ====================

    @Test
    @DisplayName("embedBatch returns correct count of results")
    void embedBatchReturnsCorrectCount() {
        MockEmbeddingProvider provider = new MockEmbeddingProvider(64);
        List<String> texts = List.of("text one", "text two", "text three");

        List<float[]> results = provider.embedBatch(texts);

        assertEquals(3, results.size());
    }

    @Test
    @DisplayName("embedBatch each result matches individual embed call")
    void embedBatchMatchesIndividualEmbed() {
        MockEmbeddingProvider provider = new MockEmbeddingProvider(64);
        List<String> texts = List.of("alpha", "beta", "gamma");

        List<float[]> batchResults = provider.embedBatch(texts);

        for (int i = 0; i < texts.size(); i++) {
            float[] individual = provider.embed(texts.get(i));
            assertArrayEquals(individual, batchResults.get(i),
                    "Batch result[" + i + "] must match individual embed for \"" + texts.get(i) + "\"");
        }
    }

    @Test
    @DisplayName("embedBatch with empty list returns empty list")
    void embedBatchEmptyListReturnsEmpty() {
        MockEmbeddingProvider provider = new MockEmbeddingProvider(64);

        List<float[]> results = provider.embedBatch(List.of());

        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("embedBatch each result has correct dimensions")
    void embedBatchResultsHaveCorrectDimensions() {
        MockEmbeddingProvider provider = new MockEmbeddingProvider(32);
        List<String> texts = List.of("one", "two");

        List<float[]> results = provider.embedBatch(texts);

        for (float[] result : results) {
            assertEquals(32, result.length);
        }
    }

    // ==================== embedBatchAsync() ====================

    @Test
    @DisplayName("embedBatchAsync returns same results as synchronous embedBatch")
    void embedBatchAsyncReturnsSameResults() throws Exception {
        MockEmbeddingProvider provider = new MockEmbeddingProvider(64);
        List<String> texts = List.of("async batch 1", "async batch 2");

        List<float[]> syncResults = provider.embedBatch(texts);
        CompletableFuture<List<float[]>> future = provider.embedBatchAsync(texts);
        List<float[]> asyncResults = future.get();

        assertEquals(syncResults.size(), asyncResults.size());
        for (int i = 0; i < syncResults.size(); i++) {
            assertArrayEquals(syncResults.get(i), asyncResults.get(i),
                    "Async batch result[" + i + "] must match sync batch result");
        }
    }

    // ==================== Metadata accessors ====================

    @Test
    @DisplayName("getDimensions returns the configured value")
    void getDimensionsReturnsConfiguredValue() {
        assertEquals(128, new MockEmbeddingProvider().getDimensions());
        assertEquals(64, new MockEmbeddingProvider(64).getDimensions());
        assertEquals(512, new MockEmbeddingProvider(512).getDimensions());
    }

    @Test
    @DisplayName("getProviderName returns 'mock'")
    void getProviderNameReturnsMock() {
        MockEmbeddingProvider provider = new MockEmbeddingProvider();

        assertEquals("mock", provider.getProviderName());
    }

    @Test
    @DisplayName("getModelName returns 'mock-embedding-v1'")
    void getModelNameReturnsMockEmbeddingV1() {
        MockEmbeddingProvider provider = new MockEmbeddingProvider();

        assertEquals("mock-embedding-v1", provider.getModelName());
    }

    // ==================== EmbeddingProvider interface ====================

    @Test
    @DisplayName("MockEmbeddingProvider implements EmbeddingProvider interface")
    void implementsEmbeddingProviderInterface() {
        MockEmbeddingProvider provider = new MockEmbeddingProvider();

        assertTrue(provider instanceof EmbeddingProvider,
                "MockEmbeddingProvider must implement EmbeddingProvider");
    }
}
