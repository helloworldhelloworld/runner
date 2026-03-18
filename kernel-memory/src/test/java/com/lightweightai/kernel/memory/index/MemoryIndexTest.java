package com.lightweightai.kernel.memory.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lightweightai.kernel.memory.model.MemoryChunk;
import com.lightweightai.kernel.memory.model.MemoryType;
import com.lightweightai.kernel.memory.model.SearchResult;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MemoryIndexTest {

    @TempDir
    Path tempDir;

    private MemoryIndex index;

    @BeforeEach
    void setUp() {
        Path indexPath = tempDir.resolve("test-agent").resolve("memory.sqlite");
        index = new MemoryIndex(indexPath, "test-agent");
    }

    @AfterEach
    void tearDown() {
        if (index != null) {
            index.close();
        }
    }

    @Test
    void testInsertAndRetrieve() {
        MemoryChunk chunk = createChunk("Test content for indexing");
        index.upsertChunk(chunk);

        MemoryChunk retrieved = index.getChunk(chunk.getId());
        assertNotNull(retrieved);
        assertEquals(chunk.getId(), retrieved.getId());
        assertEquals(chunk.getContent(), retrieved.getContent());
        assertEquals(chunk.getType(), retrieved.getType());
    }

    @Test
    void testBatchInsert() {
        List<MemoryChunk> chunks = List.of(
            createChunk("First chunk content"),
            createChunk("Second chunk content"),
            createChunk("Third chunk content")
        );

        index.insertChunks(chunks);

        assertEquals(3, index.getChunkCount());
    }

    @Test
    void testBM25Search() {
        // Insert test data
        index.upsertChunk(createChunk("The quick brown fox jumps over the lazy dog"));
        index.upsertChunk(createChunk("A fast brown cat runs past the sleepy dog"));
        index.upsertChunk(createChunk("Python programming with machine learning"));

        // Search for "brown dog"
        List<SearchResult> results = index.searchBM25("brown dog", 10);

        assertFalse(results.isEmpty());
        // Both fox and cat chunks should match (they contain "brown" and "dog")
        assertTrue(results.size() >= 2);

        // Check that results are sorted by relevance
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i - 1).getScore() >= results.get(i).getScore(),
                "Results should be sorted by score descending");
        }
    }

    @Test
    void testBM25SearchNoResults() {
        index.upsertChunk(createChunk("Some random content here"));

        List<SearchResult> results = index.searchBM25("nonexistent term", 10);

        assertTrue(results.isEmpty());
    }

    @Test
    void testGetChunksBySourceFile() {
        MemoryChunk chunk1 = createChunk("Content 1", "file1.md");
        MemoryChunk chunk2 = createChunk("Content 2", "file1.md");
        MemoryChunk chunk3 = createChunk("Content 3", "file2.md");

        index.insertChunks(List.of(chunk1, chunk2, chunk3));

        List<MemoryChunk> file1Chunks = index.getChunksBySourceFile("file1.md");
        assertEquals(2, file1Chunks.size());

        List<MemoryChunk> file2Chunks = index.getChunksBySourceFile("file2.md");
        assertEquals(1, file2Chunks.size());
    }

    @Test
    void testDeleteChunksBySourceFile() {
        index.insertChunks(List.of(
            createChunk("Content 1", "file1.md"),
            createChunk("Content 2", "file1.md"),
            createChunk("Content 3", "file2.md")
        ));

        assertEquals(3, index.getChunkCount());

        index.deleteChunksBySourceFile("file1.md");

        assertEquals(1, index.getChunkCount());
        assertTrue(index.getChunksBySourceFile("file1.md").isEmpty());
    }

    @Test
    void testExistsByHash() {
        MemoryChunk chunk = createChunk("Unique content");
        index.upsertChunk(chunk);

        assertTrue(index.existsByHash(chunk.getHash()));
        assertFalse(index.existsByHash("nonexistent-hash"));
    }

    @Test
    void testUpsertUpdatesExisting() {
        String id = "fixed-id-" + UUID.randomUUID().toString().substring(0, 8);
        MemoryChunk original = MemoryChunk.builder()
            .id(id)
            .content("Original content")
            .sourceFile("test.md")
            .startLine(1)
            .endLine(10)
            .hash("hash1")
            .type(MemoryType.DURABLE)
            .createdAt(Instant.now())
            .build();

        index.upsertChunk(original);

        MemoryChunk updated = MemoryChunk.builder()
            .id(id)
            .content("Updated content")
            .sourceFile("test.md")
            .startLine(1)
            .endLine(10)
            .hash("hash2")
            .type(MemoryType.DURABLE)
            .createdAt(Instant.now())
            .build();

        index.upsertChunk(updated);

        assertEquals(1, index.getChunkCount());
        MemoryChunk retrieved = index.getChunk(id);
        assertEquals("Updated content", retrieved.getContent());
    }

    @Test
    void testEmbeddingStorage() {
        MemoryChunk chunk = createChunk("Content with embedding");
        float[] embedding = {0.1f, 0.2f, 0.3f, 0.4f, 0.5f};
        chunk.setEmbedding(embedding);

        index.upsertChunk(chunk);

        MemoryChunk retrieved = index.getChunk(chunk.getId());
        assertNotNull(retrieved.getEmbedding());
        assertEquals(embedding.length, retrieved.getEmbedding().length);

        for (int i = 0; i < embedding.length; i++) {
            assertEquals(embedding[i], retrieved.getEmbedding()[i], 0.0001f);
        }
    }

    @Test
    void testFileMetadata() {
        String path = "memory/test.md";
        String hash = "abc123";

        assertTrue(index.fileNeedsReindex(path, hash)); // Not indexed yet, needs reindex

        index.updateFileMetadata(path, hash, 5);

        assertFalse(index.fileNeedsReindex(path, hash)); // Same hash, no reindex needed
        assertTrue(index.fileNeedsReindex(path, "different-hash")); // Different hash, needs reindex
    }

    @Test
    void testSnippetExtraction() {
        String longContent = "This is some prefix text. " +
            "The important keyword is here in the middle. " +
            "And this is some suffix text that follows.";

        MemoryChunk chunk = createChunk(longContent);
        index.upsertChunk(chunk);

        List<SearchResult> results = index.searchBM25("keyword", 10);

        assertFalse(results.isEmpty());
        String snippet = results.get(0).getSnippet();
        assertTrue(snippet.contains("keyword"));
    }

    @Test
    void testMemoryTypeFiltering() {
        index.insertChunks(List.of(
            createChunkWithType("Ephemeral content", MemoryType.EPHEMERAL),
            createChunkWithType("Durable content", MemoryType.DURABLE),
            createChunkWithType("Session content", MemoryType.SESSION)
        ));

        assertEquals(3, index.getChunkCount());

        // BM25 search finds all types
        List<SearchResult> results = index.searchBM25("content", 10);
        assertEquals(3, results.size());
    }

    // Helper methods

    private MemoryChunk createChunk(String content) {
        return createChunk(content, "test.md");
    }

    private MemoryChunk createChunk(String content, String sourceFile) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        String hash = "hash-" + id;

        return MemoryChunk.builder()
            .id(id)
            .content(content)
            .sourceFile(sourceFile)
            .startLine(1)
            .endLine(10)
            .hash(hash)
            .type(MemoryType.DURABLE)
            .createdAt(Instant.now())
            .build();
    }

    private MemoryChunk createChunkWithType(String content, MemoryType type) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        String hash = "hash-" + id;

        return MemoryChunk.builder()
            .id(id)
            .content(content)
            .sourceFile("test.md")
            .startLine(1)
            .endLine(10)
            .hash(hash)
            .type(type)
            .createdAt(Instant.now())
            .build();
    }
}
