package com.lightweightai.kernel.memory.embedding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MockEmbeddingProvider - 模拟嵌入提供者")
class MockEmbeddingProviderTest {

    private MockEmbeddingProvider provider;

    @BeforeEach
    void setUp() {
        provider = new MockEmbeddingProvider();
    }

    @Nested
    @DisplayName("基本属性")
    class Properties {

        @Test
        @DisplayName("默认维度为 128")
        void shouldHaveDefaultDimensions() {
            assertEquals(128, provider.getDimensions());
        }

        @Test
        @DisplayName("自定义维度")
        void shouldSupportCustomDimensions() {
            MockEmbeddingProvider custom = new MockEmbeddingProvider(256);
            assertEquals(256, custom.getDimensions());
        }

        @Test
        @DisplayName("提供者名称为 mock")
        void shouldReturnCorrectProviderName() {
            assertEquals("mock", provider.getProviderName());
        }

        @Test
        @DisplayName("模型名称为 mock-embedding-v1")
        void shouldReturnCorrectModelName() {
            assertEquals("mock-embedding-v1", provider.getModelName());
        }
    }

    @Nested
    @DisplayName("嵌入生成")
    class EmbeddingGeneration {

        @Test
        @DisplayName("生成正确维度的向量")
        void shouldGenerateCorrectDimensionVector() {
            float[] embedding = provider.embed("test text");
            assertEquals(128, embedding.length);
        }

        @Test
        @DisplayName("相同文本生成相同向量（确定性）")
        void shouldBeDeterministic() {
            float[] embedding1 = provider.embed("same text");
            float[] embedding2 = provider.embed("same text");
            assertArrayEquals(embedding1, embedding2);
        }

        @Test
        @DisplayName("不同文本生成不同向量")
        void shouldGenerateDifferentVectorsForDifferentTexts() {
            float[] e1 = provider.embed("hello");
            float[] e2 = provider.embed("world");

            boolean allEqual = true;
            for (int i = 0; i < e1.length; i++) {
                if (e1[i] != e2[i]) {
                    allEqual = false;
                    break;
                }
            }
            assertFalse(allEqual);
        }

        @Test
        @DisplayName("向量已归一化（单位长度）")
        void shouldGenerateNormalizedVector() {
            float[] embedding = provider.embed("normalize test");

            double norm = 0;
            for (float v : embedding) {
                norm += v * v;
            }
            norm = Math.sqrt(norm);

            assertEquals(1.0, norm, 0.01);
        }
    }

    @Nested
    @DisplayName("批量操作")
    class BatchOperations {

        @Test
        @DisplayName("批量嵌入返回正确数量")
        void shouldReturnCorrectBatchSize() {
            List<float[]> results = provider.embedBatch(List.of("a", "b", "c"));
            assertEquals(3, results.size());
        }

        @Test
        @DisplayName("批量嵌入每个维度正确")
        void shouldReturnCorrectDimensionsInBatch() {
            List<float[]> results = provider.embedBatch(List.of("a", "b"));
            for (float[] result : results) {
                assertEquals(128, result.length);
            }
        }
    }

    @Nested
    @DisplayName("异步操作")
    class AsyncOperations {

        @Test
        @DisplayName("异步嵌入返回已完成的 Future")
        void shouldReturnCompletedFuture() throws Exception {
            CompletableFuture<float[]> future = provider.embedAsync("async test");
            assertTrue(future.isDone());
            assertEquals(128, future.get().length);
        }

        @Test
        @DisplayName("异步批量嵌入返回已完成的 Future")
        void shouldReturnCompletedBatchFuture() throws Exception {
            CompletableFuture<List<float[]>> future =
                    provider.embedBatchAsync(List.of("a", "b"));
            assertTrue(future.isDone());
            assertEquals(2, future.get().size());
        }
    }
}
