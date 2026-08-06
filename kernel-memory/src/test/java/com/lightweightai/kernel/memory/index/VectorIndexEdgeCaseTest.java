package com.lightweightai.kernel.memory.index;

import com.lightweightai.kernel.memory.embedding.MockEmbeddingProvider;
import com.lightweightai.kernel.memory.model.MemoryChunk;
import com.lightweightai.kernel.memory.model.MemoryType;
import com.lightweightai.kernel.memory.model.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VectorIndex 边界条件测试
 *
 * 覆盖：
 * - 零向量 cosine similarity
 * - 不同维度向量抛异常
 * - 重复 ID 覆盖
 * - topK > 索引大小
 * - contains 对已删除和不存在的条目
 * - 批量添加混合（有/无预计算 embedding）
 * - 大量条目性能
 */
@DisplayName("VectorIndex - 边界条件")
class VectorIndexEdgeCaseTest {

    private VectorIndex vectorIndex;
    private MockEmbeddingProvider embeddingProvider;

    @BeforeEach
    void setUp() {
        embeddingProvider = new MockEmbeddingProvider(64);
        vectorIndex = new VectorIndex(embeddingProvider);
    }

    // ==================== Cosine Similarity 边界 ====================

    @Nested
    @DisplayName("Cosine Similarity 边界")
    class CosineSimilarityEdgeCases {

        @Test
        @DisplayName("零向量返回 0")
        void zeroVectorShouldReturnZero() {
            float[] zero = new float[3];
            float[] normal = {1, 2, 3};
            assertEquals(0.0f, VectorIndex.cosineSimilarity(zero, normal), 0.001f);
        }

        @Test
        @DisplayName("两个零向量返回 0")
        void twoZeroVectorsShouldReturnZero() {
            float[] a = new float[3];
            float[] b = new float[3];
            assertEquals(0.0f, VectorIndex.cosineSimilarity(a, b), 0.001f);
        }

        @Test
        @DisplayName("不同维度向量抛出异常")
        void differentDimensionsShouldThrow() {
            float[] a = {1, 2, 3};
            float[] b = {1, 2};
            assertThrows(IllegalArgumentException.class,
                () -> VectorIndex.cosineSimilarity(a, b));
        }

        @Test
        @DisplayName("相同向量 similarity = 1.0")
        void identicalVectorsShouldBeOne() {
            float[] a = {0.5f, 0.3f, 0.8f};
            assertEquals(1.0f, VectorIndex.cosineSimilarity(a, a), 0.001f);
        }

        @Test
        @DisplayName("正交向量 similarity = 0.0")
        void orthogonalVectorsShouldBeZero() {
            float[] a = {1, 0, 0, 0};
            float[] b = {0, 0, 1, 0};
            assertEquals(0.0f, VectorIndex.cosineSimilarity(a, b), 0.001f);
        }

        @Test
        @DisplayName("反向向量 similarity = -1.0")
        void oppositeVectorsShouldBeNegativeOne() {
            float[] a = {1, 2, 3};
            float[] b = {-1, -2, -3};
            assertEquals(-1.0f, VectorIndex.cosineSimilarity(a, b), 0.001f);
        }

        @Test
        @DisplayName("单维度向量正确计算")
        void singleDimensionShouldWork() {
            float[] a = {5};
            float[] b = {3};
            assertEquals(1.0f, VectorIndex.cosineSimilarity(a, b), 0.001f);
        }
    }

    // ==================== 索引操作边界 ====================

    @Nested
    @DisplayName("索引操作边界")
    class IndexOperationEdgeCases {

        @Test
        @DisplayName("重复 ID 覆盖旧条目")
        void duplicateIdShouldOverwrite() {
            MemoryChunk chunk1 = createChunkWithId("same-id", "Content version 1");
            MemoryChunk chunk2 = createChunkWithId("same-id", "Content version 2");

            vectorIndex.addChunk(chunk1);
            vectorIndex.addChunk(chunk2);

            assertEquals(1, vectorIndex.size());
            assertTrue(vectorIndex.contains("same-id"));
        }

        @Test
        @DisplayName("topK > 索引大小返回所有结果")
        void topKLargerThanIndexShouldReturnAll() {
            vectorIndex.addChunk(createChunk("Content 1"));
            vectorIndex.addChunk(createChunk("Content 2"));

            List<SearchResult> results = vectorIndex.search("content", 100);
            assertEquals(2, results.size());
        }

        @Test
        @DisplayName("topK = 0 返回空列表")
        void topKZeroShouldReturnEmpty() {
            vectorIndex.addChunk(createChunk("Content"));

            List<SearchResult> results = vectorIndex.search("content", 0);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("删除后搜索不到")
        void shouldNotFindAfterRemoval() {
            MemoryChunk chunk = createChunk("Removable content");
            vectorIndex.addChunk(chunk);

            vectorIndex.removeChunk(chunk.getId());

            List<SearchResult> results = vectorIndex.search("removable", 10);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("删除不存在的 ID 不报错")
        void removingNonExistentIdShouldNotError() {
            assertDoesNotThrow(() -> vectorIndex.removeChunk("nonexistent"));
        }

        @Test
        @DisplayName("contains 对不存在 ID 返回 false")
        void containsShouldReturnFalseForMissing() {
            assertFalse(vectorIndex.contains("nonexistent"));
        }

        @Test
        @DisplayName("清空后大小为 0")
        void clearShouldEmptyIndex() {
            vectorIndex.addChunk(createChunk("A"));
            vectorIndex.addChunk(createChunk("B"));
            vectorIndex.clear();
            assertEquals(0, vectorIndex.size());
        }

        @Test
        @DisplayName("getEmbeddingProvider 返回原始 provider")
        void shouldReturnEmbeddingProvider() {
            assertSame(embeddingProvider, vectorIndex.getEmbeddingProvider());
        }
    }

    // ==================== 批量操作 ====================

    @Nested
    @DisplayName("批量操作")
    class BatchOperations {

        @Test
        @DisplayName("批量添加混合 embedding（有/无预计算）")
        void batchAddMixedEmbeddings() {
            MemoryChunk withEmbedding = createChunk("Pre-computed");
            float[] precomputed = new float[64];
            for (int i = 0; i < 64; i++) precomputed[i] = i / 64.0f;
            withEmbedding.setEmbedding(precomputed);

            MemoryChunk withoutEmbedding = createChunk("Needs computing");

            vectorIndex.addChunks(List.of(withEmbedding, withoutEmbedding));

            assertEquals(2, vectorIndex.size());
            assertTrue(vectorIndex.contains(withEmbedding.getId()));
            assertTrue(vectorIndex.contains(withoutEmbedding.getId()));
        }

        @Test
        @DisplayName("批量添加空列表不报错")
        void batchAddEmptyListShouldNotError() {
            assertDoesNotThrow(() -> vectorIndex.addChunks(List.of()));
            assertEquals(0, vectorIndex.size());
        }
    }

    // ==================== searchByVector ====================

    @Nested
    @DisplayName("searchByVector")
    class SearchByVector {

        @Test
        @DisplayName("searchByVector 在空索引返回空")
        void searchByVectorOnEmptyIndexShouldReturnEmpty() {
            float[] query = new float[64];
            List<SearchResult> results = vectorIndex.searchByVector(query, 10);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("searchByVector 返回带 snippet 的结果")
        void searchByVectorShouldReturnSnippets() {
            vectorIndex.addChunk(createChunk("Some test content for snippet generation"));

            float[] queryVector = embeddingProvider.embed("test content");
            List<SearchResult> results = vectorIndex.searchByVector(queryVector, 10);

            assertFalse(results.isEmpty());
            assertNotNull(results.get(0).getSnippet());
        }
    }

    // ==================== 辅助方法 ====================

    private MemoryChunk createChunk(String content) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return MemoryChunk.builder()
            .id(id)
            .content(content)
            .sourceFile("test.md")
            .startLine(1)
            .endLine(10)
            .hash("hash-" + id)
            .type(MemoryType.DURABLE)
            .createdAt(Instant.now())
            .build();
    }

    private MemoryChunk createChunkWithId(String id, String content) {
        return MemoryChunk.builder()
            .id(id)
            .content(content)
            .sourceFile("test.md")
            .startLine(1)
            .endLine(10)
            .hash("hash-" + id)
            .type(MemoryType.DURABLE)
            .createdAt(Instant.now())
            .build();
    }
}
