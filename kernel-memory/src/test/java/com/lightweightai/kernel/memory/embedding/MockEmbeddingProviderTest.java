package com.lightweightai.kernel.memory.embedding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MockEmbeddingProvider - determinism, normalization, and batch consistency")
class MockEmbeddingProviderTest {

    @Nested
    @DisplayName("Determinism")
    class Determinism {

        @Test
        @DisplayName("same text always produces identical embeddings")
        void sameTextSameEmbedding() {
            MockEmbeddingProvider provider = new MockEmbeddingProvider();
            float[] first = provider.embed("hello world");
            float[] second = provider.embed("hello world");

            assertArrayEquals(first, second,
                    "Same text must produce identical embeddings for test reproducibility");
        }

        @Test
        @DisplayName("different texts produce different embeddings")
        void differentTextDifferentEmbedding() {
            MockEmbeddingProvider provider = new MockEmbeddingProvider();
            float[] a = provider.embed("hello");
            float[] b = provider.embed("goodbye");

            boolean allEqual = true;
            for (int i = 0; i < a.length; i++) {
                if (a[i] != b[i]) {
                    allEqual = false;
                    break;
                }
            }
            assertFalse(allEqual, "Different texts should produce different embeddings");
        }
    }

    @Nested
    @DisplayName("Normalization")
    class Normalization {

        @Test
        @DisplayName("embedding vector is unit-normalized (L2 norm ~ 1.0)")
        void embeddingIsUnitNormalized() {
            MockEmbeddingProvider provider = new MockEmbeddingProvider(128);
            float[] embedding = provider.embed("test normalization");

            float norm = 0;
            for (float v : embedding) {
                norm += v * v;
            }
            norm = (float) Math.sqrt(norm);

            assertEquals(1.0f, norm, 0.001f,
                    "Embedding should be unit-normalized (L2 norm = 1.0)");
        }

        @Test
        @DisplayName("normalization holds for various input texts")
        void normalizationForVariousInputs() {
            MockEmbeddingProvider provider = new MockEmbeddingProvider(256);
            String[] texts = {"short", "a longer piece of text with more words", "", "test"};

            for (String text : texts) {
                float[] embedding = provider.embed(text);
                float norm = 0;
                for (float v : embedding) {
                    norm += v * v;
                }
                norm = (float) Math.sqrt(norm);
                assertEquals(1.0f, norm, 0.01f,
                        "Normalization failed for: '" + text + "'");
            }
        }
    }

    @Nested
    @DisplayName("Dimensions")
    class Dimensions {

        @Test
        @DisplayName("default dimension is 128")
        void defaultDimension() {
            MockEmbeddingProvider provider = new MockEmbeddingProvider();
            assertEquals(128, provider.getDimensions());
            assertEquals(128, provider.embed("test").length);
        }

        @Test
        @DisplayName("custom dimension is respected")
        void customDimension() {
            MockEmbeddingProvider provider = new MockEmbeddingProvider(64);
            assertEquals(64, provider.getDimensions());
            assertEquals(64, provider.embed("test").length);
        }

        @Test
        @DisplayName("large dimension works correctly")
        void largeDimension() {
            MockEmbeddingProvider provider = new MockEmbeddingProvider(1536);
            assertEquals(1536, provider.getDimensions());
            assertEquals(1536, provider.embed("test").length);
        }
    }

    @Nested
    @DisplayName("Batch operations")
    class BatchOperations {

        @Test
        @DisplayName("batch embed produces same results as individual embeds")
        void batchConsistentWithSingle() {
            MockEmbeddingProvider provider = new MockEmbeddingProvider();
            List<String> texts = List.of("alpha", "beta", "gamma");

            List<float[]> batchResults = provider.embedBatch(texts);
            assertEquals(3, batchResults.size());

            for (int i = 0; i < texts.size(); i++) {
                float[] single = provider.embed(texts.get(i));
                assertArrayEquals(single, batchResults.get(i),
                        "Batch result[" + i + "] must match single embed for '" + texts.get(i) + "'");
            }
        }

        @Test
        @DisplayName("empty batch returns empty list")
        void emptyBatchReturnsEmpty() {
            MockEmbeddingProvider provider = new MockEmbeddingProvider();
            List<float[]> results = provider.embedBatch(List.of());
            assertTrue(results.isEmpty());
        }
    }

    @Nested
    @DisplayName("Async operations")
    class AsyncOperations {

        @Test
        @DisplayName("embedAsync returns same result as sync embed")
        void asyncMatchesSync() throws Exception {
            MockEmbeddingProvider provider = new MockEmbeddingProvider();
            float[] syncResult = provider.embed("async test");
            CompletableFuture<float[]> asyncFuture = provider.embedAsync("async test");

            float[] asyncResult = asyncFuture.get();
            assertArrayEquals(syncResult, asyncResult);
        }

        @Test
        @DisplayName("embedBatchAsync returns same result as sync embedBatch")
        void batchAsyncMatchesSync() throws Exception {
            MockEmbeddingProvider provider = new MockEmbeddingProvider();
            List<String> texts = List.of("one", "two");
            List<float[]> syncResults = provider.embedBatch(texts);
            CompletableFuture<List<float[]>> asyncFuture = provider.embedBatchAsync(texts);

            List<float[]> asyncResults = asyncFuture.get();
            assertEquals(syncResults.size(), asyncResults.size());
            for (int i = 0; i < syncResults.size(); i++) {
                assertArrayEquals(syncResults.get(i), asyncResults.get(i));
            }
        }
    }

    @Nested
    @DisplayName("Provider metadata")
    class ProviderMetadata {

        @Test
        @DisplayName("provider name is 'mock'")
        void providerName() {
            assertEquals("mock", new MockEmbeddingProvider().getProviderName());
        }

        @Test
        @DisplayName("model name is 'mock-embedding-v1'")
        void modelName() {
            assertEquals("mock-embedding-v1", new MockEmbeddingProvider().getModelName());
        }
    }
}
