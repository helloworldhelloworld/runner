package com.lightweightai.kernel.memory.embedding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MockEmbeddingProvider - 测试用 Embedding 提供者")
class MockEmbeddingProviderTest {

    private MockEmbeddingProvider provider;

    @BeforeEach
    void setUp() {
        provider = new MockEmbeddingProvider();
    }

    @Nested
    @DisplayName("基本属性")
    class BasicProperties {

        @Test
        @DisplayName("默认维度为 128")
        void defaultDimensions() {
            assertEquals(128, provider.getDimensions());
        }

        @Test
        @DisplayName("自定义维度")
        void customDimensions() {
            MockEmbeddingProvider custom = new MockEmbeddingProvider(64);
            assertEquals(64, custom.getDimensions());
        }

        @Test
        @DisplayName("provider 名称和模型名")
        void providerAndModelName() {
            assertEquals("mock", provider.getProviderName());
            assertEquals("mock-embedding-v1", provider.getModelName());
        }
    }

    @Nested
    @DisplayName("Embedding 生成")
    class EmbeddingGeneration {

        @Test
        @DisplayName("生成正确维度的向量")
        void correctDimensions() {
            float[] embedding = provider.embed("hello");
            assertEquals(128, embedding.length);
        }

        @Test
        @DisplayName("相同文本生成相同向量（确定性）")
        void deterministicEmbedding() {
            float[] a = provider.embed("test text");
            float[] b = provider.embed("test text");
            assertArrayEquals(a, b);
        }

        @Test
        @DisplayName("不同文本生成不同向量")
        void differentTextDifferentEmbedding() {
            float[] a = provider.embed("hello");
            float[] b = provider.embed("world");
            boolean allEqual = true;
            for (int i = 0; i < a.length; i++) {
                if (a[i] != b[i]) { allEqual = false; break; }
            }
            assertFalse(allEqual);
        }

        @Test
        @DisplayName("向量已归一化（L2 norm 约等于 1）")
        void embeddingNormalized() {
            float[] embedding = provider.embed("test");
            float norm = 0;
            for (float v : embedding) norm += v * v;
            norm = (float) Math.sqrt(norm);
            assertEquals(1.0f, norm, 0.01f);
        }
    }

    @Nested
    @DisplayName("批量和异步")
    class BatchAndAsync {

        @Test
        @DisplayName("embedBatch 对每个文本生成独立向量")
        void batchEmbedding() {
            List<float[]> results = provider.embedBatch(List.of("a", "b", "c"));
            assertEquals(3, results.size());
            assertEquals(128, results.get(0).length);
        }

        @Test
        @DisplayName("embedAsync 返回 CompletableFuture")
        void asyncEmbedding() {
            float[] result = provider.embedAsync("async test").join();
            assertEquals(128, result.length);
        }

        @Test
        @DisplayName("embedBatchAsync 返回 CompletableFuture")
        void asyncBatchEmbedding() {
            List<float[]> results = provider.embedBatchAsync(List.of("a", "b")).join();
            assertEquals(2, results.size());
        }
    }
}
