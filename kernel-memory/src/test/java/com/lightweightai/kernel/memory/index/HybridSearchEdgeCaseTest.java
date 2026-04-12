package com.lightweightai.kernel.memory.index;

import com.lightweightai.kernel.memory.embedding.MockEmbeddingProvider;
import com.lightweightai.kernel.memory.model.MemoryChunk;
import com.lightweightai.kernel.memory.model.MemoryType;
import com.lightweightai.kernel.memory.model.SearchResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HybridSearch 边界条件测试
 *
 * 覆盖：
 * - 权重边界（0.0, 1.0, 超出范围）
 * - 空索引搜索
 * - 单文档搜索
 * - removeChunk 后搜索
 * - IndexStats 一致性
 * - BM25-only 和 vector-only 独立路径
 * - topK=1 最小结果集
 */
@DisplayName("HybridSearch - 边界条件")
class HybridSearchEdgeCaseTest {

    @TempDir
    Path tempDir;

    private MemoryIndex memoryIndex;
    private VectorIndex vectorIndex;
    private HybridSearch hybridSearch;

    @BeforeEach
    void setUp() {
        Path indexPath = tempDir.resolve("test-agent").resolve("memory.sqlite");
        memoryIndex = new MemoryIndex(indexPath, "test-agent");

        MockEmbeddingProvider embeddingProvider = new MockEmbeddingProvider(64);
        vectorIndex = new VectorIndex(embeddingProvider);

        hybridSearch = new HybridSearch(memoryIndex, vectorIndex);
    }

    @AfterEach
    void tearDown() {
        if (memoryIndex != null) {
            memoryIndex.close();
        }
    }

    // ==================== 权重边界 ====================

    @Nested
    @DisplayName("权重边界")
    class WeightBoundary {

        @Test
        @DisplayName("vectorWeight=0.0 纯 BM25 搜索")
        void pureBM25WithZeroVectorWeight() {
            hybridSearch.indexChunk(createChunk("keyword matching test"));
            hybridSearch.indexChunk(createChunk("unrelated stuff"));

            List<SearchResult> results = hybridSearch.search("keyword matching", 10, 0.0f);

            assertFalse(results.isEmpty());
        }

        @Test
        @DisplayName("vectorWeight=1.0 纯向量搜索")
        void pureVectorWithFullWeight() {
            hybridSearch.indexChunk(createChunk("semantic meaning content"));
            hybridSearch.indexChunk(createChunk("completely different"));

            List<SearchResult> results = hybridSearch.search("semantic meaning", 10, 1.0f);

            assertFalse(results.isEmpty());
        }

        @Test
        @DisplayName("vectorWeight 超出范围被裁剪")
        void weightBeyondRangeShouldBeClipped() {
            hybridSearch.indexChunk(createChunk("test content"));

            // vectorWeight > 1.0 should be clipped to 1.0
            List<SearchResult> results1 = hybridSearch.search("test", 10, 2.0f);
            assertFalse(results1.isEmpty());

            // vectorWeight < 0.0 should be clipped to 0.0
            List<SearchResult> results2 = hybridSearch.search("test", 10, -1.0f);
            assertFalse(results2.isEmpty());
        }

        @Test
        @DisplayName("vectorWeight=0.5 平衡权重")
        void balancedWeight() {
            hybridSearch.indexChunk(createChunk("balanced search content"));

            List<SearchResult> results = hybridSearch.search("balanced content", 10, 0.5f);
            assertFalse(results.isEmpty());
        }
    }

    // ==================== 搜索边界 ====================

    @Nested
    @DisplayName("搜索边界")
    class SearchBoundary {

        @Test
        @DisplayName("单文档搜索返回一个结果")
        void singleDocumentShouldReturnOne() {
            hybridSearch.indexChunk(createChunk("The only document in the index"));

            List<SearchResult> results = hybridSearch.search("document", 10);
            assertEquals(1, results.size());
        }

        @Test
        @DisplayName("topK=1 只返回最佳结果")
        void topKOneShouldReturnBest() {
            hybridSearch.indexChunk(createChunk("First document"));
            hybridSearch.indexChunk(createChunk("Second document"));
            hybridSearch.indexChunk(createChunk("Third document"));

            List<SearchResult> results = hybridSearch.search("document", 1);
            assertEquals(1, results.size());
        }

        @Test
        @DisplayName("空索引搜索 BM25-only 返回空")
        void emptyIndexBM25OnlyShouldReturnEmpty() {
            List<SearchResult> results = hybridSearch.searchBM25Only("anything", 10);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("空索引搜索 vector-only 返回空")
        void emptyIndexVectorOnlyShouldReturnEmpty() {
            List<SearchResult> results = hybridSearch.searchVectorOnly("anything", 10);
            assertTrue(results.isEmpty());
        }
    }

    // ==================== removeChunk ====================

    @Nested
    @DisplayName("删除操作")
    class RemoveOperations {

        @Test
        @DisplayName("removeChunk 后向量搜索不返回该文档")
        void removedChunkShouldNotAppearInVectorSearch() {
            MemoryChunk chunk = createChunk("Removable content");
            hybridSearch.indexChunk(chunk);

            hybridSearch.removeChunk(chunk.getId());

            // Vector search should not find it
            List<SearchResult> results = hybridSearch.searchVectorOnly("removable", 10);
            assertTrue(results.isEmpty());
        }
    }

    // ==================== IndexStats ====================

    @Nested
    @DisplayName("IndexStats")
    class IndexStatsTests {

        @Test
        @DisplayName("空索引统计全为 0")
        void emptyIndexStatsShouldBeZero() {
            HybridSearch.IndexStats stats = hybridSearch.getStats();
            assertEquals(0, stats.bm25ChunkCount());
            assertEquals(0, stats.vectorChunkCount());
            assertEquals(64, stats.embeddingDimensions());
        }

        @Test
        @DisplayName("索引后统计准确")
        void statsAfterIndexingShouldBeAccurate() {
            hybridSearch.indexChunk(createChunk("Doc 1"));
            hybridSearch.indexChunk(createChunk("Doc 2"));
            hybridSearch.indexChunk(createChunk("Doc 3"));

            HybridSearch.IndexStats stats = hybridSearch.getStats();
            assertEquals(3, stats.bm25ChunkCount());
            assertEquals(3, stats.vectorChunkCount());
        }

        @Test
        @DisplayName("批量索引后统计准确")
        void statsAfterBatchIndexingShouldBeAccurate() {
            List<MemoryChunk> chunks = List.of(
                createChunk("Batch 1"),
                createChunk("Batch 2"),
                createChunk("Batch 3"),
                createChunk("Batch 4"),
                createChunk("Batch 5")
            );

            hybridSearch.indexChunks(chunks);

            HybridSearch.IndexStats stats = hybridSearch.getStats();
            assertEquals(5, stats.bm25ChunkCount());
            assertEquals(5, stats.vectorChunkCount());
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
}
