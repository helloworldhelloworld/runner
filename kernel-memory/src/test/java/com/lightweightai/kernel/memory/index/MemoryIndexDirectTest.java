package com.lightweightai.kernel.memory.index;

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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemoryIndex - 直接单元测试补全")
class MemoryIndexDirectTest {

    @TempDir
    Path tempDir;

    private MemoryIndex index;

    @BeforeEach
    void setUp() {
        Path indexPath = tempDir.resolve("direct-test").resolve("memory.sqlite");
        index = new MemoryIndex(indexPath, "direct-test-agent");
    }

    @AfterEach
    void tearDown() {
        if (index != null) {
            index.close();
        }
    }

    @Nested
    @DisplayName("getChunk 边界条件")
    class GetChunkEdgeCases {

        @Test
        @DisplayName("查询不存在的ID返回null")
        void shouldReturnNullForMissingId() {
            assertNull(index.getChunk("nonexistent-id-12345"));
        }

        @Test
        @DisplayName("空索引中查询返回null")
        void shouldReturnNullFromEmptyIndex() {
            assertNull(index.getChunk("any-id"));
        }
    }

    @Nested
    @DisplayName("getChunkCount 边界条件")
    class ChunkCount {

        @Test
        @DisplayName("空索引返回0")
        void shouldReturnZeroForEmptyIndex() {
            assertEquals(0, index.getChunkCount());
        }

        @Test
        @DisplayName("插入后计数准确")
        void shouldCountAccuratelyAfterInserts() {
            index.upsertChunk(makeChunk("content1"));
            assertEquals(1, index.getChunkCount());

            index.upsertChunk(makeChunk("content2"));
            assertEquals(2, index.getChunkCount());
        }

        @Test
        @DisplayName("upsert相同ID不增加计数")
        void shouldNotIncrementOnUpsertSameId() {
            String id = "fixed-id";
            index.upsertChunk(makeChunk(id, "original", "file.md"));
            assertEquals(1, index.getChunkCount());

            index.upsertChunk(makeChunk(id, "updated", "file.md"));
            assertEquals(1, index.getChunkCount());
        }
    }

    @Nested
    @DisplayName("insertChunks 批量插入")
    class BatchInsert {

        @Test
        @DisplayName("空列表不报错")
        void shouldHandleEmptyList() {
            assertDoesNotThrow(() -> index.insertChunks(List.of()));
            assertEquals(0, index.getChunkCount());
        }

        @Test
        @DisplayName("单元素列表正常插入")
        void shouldInsertSingleElement() {
            index.insertChunks(List.of(makeChunk("single")));
            assertEquals(1, index.getChunkCount());
        }

        @Test
        @DisplayName("批量插入保留所有内容")
        void shouldPreserveAllContent() {
            List<MemoryChunk> chunks = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                chunks.add(makeChunk("content-" + i));
            }
            index.insertChunks(chunks);
            assertEquals(10, index.getChunkCount());

            for (MemoryChunk chunk : chunks) {
                MemoryChunk retrieved = index.getChunk(chunk.getId());
                assertNotNull(retrieved, "Chunk " + chunk.getId() + " should exist");
                assertEquals(chunk.getContent(), retrieved.getContent());
            }
        }
    }

    @Nested
    @DisplayName("BM25搜索边界条件")
    class BM25EdgeCases {

        @Test
        @DisplayName("空索引搜索返回空列表")
        void shouldReturnEmptyForEmptyIndex() {
            List<SearchResult> results = index.searchBM25("anything", 10);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("FTS5特殊字符被正确转义")
        void shouldEscapeSpecialCharacters() {
            index.upsertChunk(makeChunk("Hello world test content"));

            assertDoesNotThrow(() -> {
                index.searchBM25("test\"query", 10);
                index.searchBM25("test'query", 10);
                index.searchBM25("test(query)", 10);
                index.searchBM25("test[query]", 10);
                index.searchBM25("test{query}", 10);
                index.searchBM25("test*query", 10);
                index.searchBM25("test:query", 10);
            });
        }

        @Test
        @DisplayName("中文内容BM25搜索不报错(FTS5默认tokenizer不支持中文分词)")
        void shouldNotThrowForChineseContent() {
            index.upsertChunk(makeChunk("今天我很开心因为天气很好"));
            index.upsertChunk(makeChunk("昨天下雨了心情不太好"));

            // FTS5 default tokenizer (unicode61) doesn't segment Chinese —
            // search may return empty, but must not throw
            assertDoesNotThrow(() -> index.searchBM25("开心", 10));
        }

        @Test
        @DisplayName("topK限制结果数量")
        void shouldRespectTopKLimit() {
            for (int i = 0; i < 10; i++) {
                index.upsertChunk(makeChunk("common keyword content " + i));
            }

            List<SearchResult> results = index.searchBM25("common keyword", 3);
            assertTrue(results.size() <= 3);
        }
    }

    @Nested
    @DisplayName("existsByHash")
    class ExistsByHash {

        @Test
        @DisplayName("空索引中hash不存在")
        void shouldNotExistInEmptyIndex() {
            assertFalse(index.existsByHash("any-hash"));
        }

        @Test
        @DisplayName("插入后hash存在")
        void shouldExistAfterInsert() {
            MemoryChunk chunk = makeChunk("test content");
            index.upsertChunk(chunk);
            assertTrue(index.existsByHash(chunk.getHash()));
        }

        @Test
        @DisplayName("删除后hash不再存在")
        void shouldNotExistAfterDelete() {
            MemoryChunk chunk = makeChunk("id1", "test", "file.md");
            index.upsertChunk(chunk);
            assertTrue(index.existsByHash(chunk.getHash()));

            index.deleteChunksBySourceFile("file.md");
            assertFalse(index.existsByHash(chunk.getHash()));
        }
    }

    @Nested
    @DisplayName("fileNeedsReindex")
    class FileReindex {

        @Test
        @DisplayName("未索引的文件需要重新索引")
        void shouldNeedReindexForNewFile() {
            assertTrue(index.fileNeedsReindex("new-file.md", "hash123"));
        }

        @Test
        @DisplayName("hash相同不需要重新索引")
        void shouldNotNeedReindexForSameHash() {
            index.updateFileMetadata("file.md", "hash-abc", 5);
            assertFalse(index.fileNeedsReindex("file.md", "hash-abc"));
        }

        @Test
        @DisplayName("hash不同需要重新索引")
        void shouldNeedReindexForDifferentHash() {
            index.updateFileMetadata("file.md", "hash-abc", 5);
            assertTrue(index.fileNeedsReindex("file.md", "hash-xyz"));
        }
    }

    @Nested
    @DisplayName("getChunksBySourceFile 排序")
    class ChunksBySourceFileOrder {

        @Test
        @DisplayName("空文件返回空列表")
        void shouldReturnEmptyForMissingFile() {
            assertTrue(index.getChunksBySourceFile("nonexistent.md").isEmpty());
        }

        @Test
        @DisplayName("结果按start_line排序")
        void shouldOrderByStartLine() {
            MemoryChunk c1 = makeChunkWithLines("a", "test.md", 10, 20);
            MemoryChunk c2 = makeChunkWithLines("b", "test.md", 1, 5);
            MemoryChunk c3 = makeChunkWithLines("c", "test.md", 30, 40);
            index.insertChunks(List.of(c1, c2, c3));

            List<MemoryChunk> chunks = index.getChunksBySourceFile("test.md");
            assertEquals(3, chunks.size());
            assertTrue(chunks.get(0).getStartLine() <= chunks.get(1).getStartLine());
            assertTrue(chunks.get(1).getStartLine() <= chunks.get(2).getStartLine());
        }
    }

    @Nested
    @DisplayName("deleteChunksBySourceFile")
    class DeleteBySourceFile {

        @Test
        @DisplayName("删除不存在的文件不报错")
        void shouldNotThrowForMissingFile() {
            assertDoesNotThrow(() -> index.deleteChunksBySourceFile("nonexistent.md"));
        }

        @Test
        @DisplayName("只删除指定文件的chunks")
        void shouldOnlyDeleteTargetFile() {
            index.insertChunks(List.of(
                makeChunk("id1", "c1", "a.md"),
                makeChunk("id2", "c2", "a.md"),
                makeChunk("id3", "c3", "b.md")
            ));
            assertEquals(3, index.getChunkCount());

            index.deleteChunksBySourceFile("a.md");

            assertEquals(1, index.getChunkCount());
            assertNull(index.getChunk("id1"));
            assertNull(index.getChunk("id2"));
            assertNotNull(index.getChunk("id3"));
        }
    }

    @Nested
    @DisplayName("close 操作")
    class CloseOperation {

        @Test
        @DisplayName("重复close不报错")
        void shouldHandleDoubleClose() {
            assertDoesNotThrow(() -> {
                index.close();
                index.close();
            });
            index = null; // prevent tearDown from closing again
        }
    }

    // Helpers

    private MemoryChunk makeChunk(String content) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return MemoryChunk.builder()
            .id(id).content(content).sourceFile("test.md")
            .startLine(1).endLine(10)
            .hash("hash-" + id)
            .type(MemoryType.DURABLE).createdAt(Instant.now())
            .build();
    }

    private MemoryChunk makeChunk(String id, String content, String sourceFile) {
        return MemoryChunk.builder()
            .id(id).content(content).sourceFile(sourceFile)
            .startLine(1).endLine(10)
            .hash("hash-" + id)
            .type(MemoryType.DURABLE).createdAt(Instant.now())
            .build();
    }

    private MemoryChunk makeChunkWithLines(String content, String sourceFile, int start, int end) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return MemoryChunk.builder()
            .id(id).content(content).sourceFile(sourceFile)
            .startLine(start).endLine(end)
            .hash("hash-" + id)
            .type(MemoryType.DURABLE).createdAt(Instant.now())
            .build();
    }
}
