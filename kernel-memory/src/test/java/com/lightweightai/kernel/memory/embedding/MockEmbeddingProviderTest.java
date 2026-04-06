package com.lightweightai.kernel.memory.embedding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MockEmbeddingProvider - Mock嵌入向量提供者")
class MockEmbeddingProviderTest {

    @Test
    @DisplayName("默认维度为 128")
    void shouldHaveDefaultDimensions() {
        MockEmbeddingProvider provider = new MockEmbeddingProvider();
        assertEquals(128, provider.getDimensions());
    }

    @Test
    @DisplayName("自定义维度")
    void shouldAcceptCustomDimensions() {
        MockEmbeddingProvider provider = new MockEmbeddingProvider(256);
        assertEquals(256, provider.getDimensions());
    }

    @Test
    @DisplayName("embed 返回正确维度的向量")
    void shouldReturnCorrectDimensionVector() {
        MockEmbeddingProvider provider = new MockEmbeddingProvider(64);
        float[] embedding = provider.embed("hello world");

        assertEquals(64, embedding.length);
    }

    @Test
    @DisplayName("相同文本产生相同向量（确定性）")
    void shouldBeDeterministic() {
        MockEmbeddingProvider provider = new MockEmbeddingProvider();
        float[] a = provider.embed("hello");
        float[] b = provider.embed("hello");

        assertArrayEquals(a, b);
    }

    @Test
    @DisplayName("不同文本产生不同向量")
    void shouldProduceDifferentVectors() {
        MockEmbeddingProvider provider = new MockEmbeddingProvider();
        float[] a = provider.embed("hello");
        float[] b = provider.embed("world");

        boolean allSame = true;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                allSame = false;
                break;
            }
        }
        assertFalse(allSame, "Different texts should produce different embeddings");
    }

    @Test
    @DisplayName("向量是归一化的")
    void shouldBeNormalized() {
        MockEmbeddingProvider provider = new MockEmbeddingProvider();
        float[] embedding = provider.embed("test text");

        float norm = 0;
        for (float v : embedding) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);

        assertEquals(1.0f, norm, 0.01f, "Embedding should be normalized to unit length");
    }

    @Test
    @DisplayName("embedAsync 返回相同结果")
    void shouldReturnSameResultAsync() throws Exception {
        MockEmbeddingProvider provider = new MockEmbeddingProvider();
        float[] sync = provider.embed("test");
        float[] async = provider.embedAsync("test").get();

        assertArrayEquals(sync, async);
    }

    @Test
    @DisplayName("embedBatch 批量嵌入")
    void shouldEmbedBatch() {
        MockEmbeddingProvider provider = new MockEmbeddingProvider();
        List<float[]> results = provider.embedBatch(List.of("a", "b", "c"));

        assertEquals(3, results.size());
        for (float[] result : results) {
            assertEquals(128, result.length);
        }
    }

    @Test
    @DisplayName("embedBatchAsync 批量异步嵌入")
    void shouldEmbedBatchAsync() throws Exception {
        MockEmbeddingProvider provider = new MockEmbeddingProvider();
        List<float[]> results = provider.embedBatchAsync(List.of("x", "y")).get();

        assertEquals(2, results.size());
    }

    @Test
    @DisplayName("providerName 和 modelName")
    void shouldReturnProviderAndModelName() {
        MockEmbeddingProvider provider = new MockEmbeddingProvider();
        assertEquals("mock", provider.getProviderName());
        assertEquals("mock-embedding-v1", provider.getModelName());
    }
}
