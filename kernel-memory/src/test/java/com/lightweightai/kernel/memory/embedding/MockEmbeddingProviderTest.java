package com.lightweightai.kernel.memory.embedding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MockEmbeddingProvider")
class MockEmbeddingProviderTest {

    private MockEmbeddingProvider provider;

    @BeforeEach
    void setUp() {
        provider = new MockEmbeddingProvider();
    }

    @Nested
    @DisplayName("Configuration")
    class ConfigTests {

        @Test
        void shouldDefaultTo128Dimensions() {
            assertEquals(128, provider.getDimensions());
        }

        @Test
        void shouldAcceptCustomDimensions() {
            MockEmbeddingProvider custom = new MockEmbeddingProvider(256);
            assertEquals(256, custom.getDimensions());
        }

        @Test
        void shouldReturnMockProviderName() {
            assertEquals("mock", provider.getProviderName());
        }

        @Test
        void shouldReturnMockModelName() {
            assertEquals("mock-embedding-v1", provider.getModelName());
        }
    }

    @Nested
    @DisplayName("Embedding Generation")
    class EmbeddingTests {

        @Test
        void shouldGenerateEmbeddingWithCorrectDimensions() {
            float[] embedding = provider.embed("test text");
            assertEquals(128, embedding.length);
        }

        @Test
        void shouldGenerateCustomDimensionEmbeddings() {
            MockEmbeddingProvider custom = new MockEmbeddingProvider(64);
            float[] embedding = custom.embed("test");
            assertEquals(64, embedding.length);
        }

        @Test
        void shouldBeDeterministic() {
            float[] first = provider.embed("same text");
            float[] second = provider.embed("same text");

            assertArrayEquals(first, second);
        }

        @Test
        void shouldProduceDifferentEmbeddingsForDifferentTexts() {
            float[] a = provider.embed("hello world");
            float[] b = provider.embed("goodbye world");

            boolean allEqual = true;
            for (int i = 0; i < a.length; i++) {
                if (a[i] != b[i]) {
                    allEqual = false;
                    break;
                }
            }
            assertFalse(allEqual);
        }

        @Test
        void shouldBeNormalized() {
            float[] embedding = provider.embed("test normalization");

            double normSquared = 0;
            for (float v : embedding) {
                normSquared += v * v;
            }
            assertEquals(1.0, Math.sqrt(normSquared), 0.01);
        }
    }

    @Nested
    @DisplayName("Async Operations")
    class AsyncTests {

        @Test
        void shouldEmbedAsync() throws Exception {
            CompletableFuture<float[]> future = provider.embedAsync("async test");
            float[] result = future.get();

            assertNotNull(result);
            assertEquals(128, result.length);
        }

        @Test
        void shouldEmbedBatch() {
            List<float[]> results = provider.embedBatch(List.of("text1", "text2", "text3"));

            assertEquals(3, results.size());
            for (float[] result : results) {
                assertEquals(128, result.length);
            }
        }

        @Test
        void shouldEmbedBatchAsync() throws Exception {
            CompletableFuture<List<float[]>> future =
                    provider.embedBatchAsync(List.of("a", "b"));
            List<float[]> results = future.get();

            assertEquals(2, results.size());
        }

        @Test
        void shouldHandleEmptyBatch() {
            List<float[]> results = provider.embedBatch(List.of());
            assertTrue(results.isEmpty());
        }
    }
}
