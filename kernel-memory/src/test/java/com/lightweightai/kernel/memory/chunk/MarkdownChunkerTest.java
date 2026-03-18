package com.lightweightai.kernel.memory.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lightweightai.kernel.memory.model.MemoryChunk;
import com.lightweightai.kernel.memory.model.MemoryType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MarkdownChunkerTest {

    private MarkdownChunker chunker;

    @BeforeEach
    void setUp() {
        // Use smaller chunk size for testing
        chunker = new MarkdownChunker(200, 50);
    }

    @Test
    void testEmptyContent() {
        List<MemoryChunk> chunks = chunker.chunk("", "test.md");
        assertTrue(chunks.isEmpty());
    }

    @Test
    void testNullContent() {
        List<MemoryChunk> chunks = chunker.chunk(null, "test.md");
        assertTrue(chunks.isEmpty());
    }

    @Test
    void testSmallContent() {
        String content = "# Hello\n\nThis is a small test.";
        List<MemoryChunk> chunks = chunker.chunk(content, "test.md");

        assertEquals(1, chunks.size());
        assertEquals(content, chunks.get(0).getContent());
        assertEquals("test.md", chunks.get(0).getSourceFile());
        assertEquals(1, chunks.get(0).getStartLine());
        assertEquals(3, chunks.get(0).getEndLine());
    }

    @Test
    void testLargeContentCreatesMultipleChunks() {
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            content.append("Line ").append(i).append(": This is some test content that takes up space.\n");
        }

        List<MemoryChunk> chunks = chunker.chunk(content.toString(), "large.md");

        assertTrue(chunks.size() > 1, "Should create multiple chunks");

        // Verify chunks have content
        for (MemoryChunk chunk : chunks) {
            assertFalse(chunk.getContent().isBlank());
            assertNotNull(chunk.getId());
            assertNotNull(chunk.getHash());
            assertEquals("large.md", chunk.getSourceFile());
            assertTrue(chunk.getStartLine() >= 1);
            assertTrue(chunk.getEndLine() >= chunk.getStartLine());
        }
    }

    @Test
    void testOverlapBetweenChunks() {
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            content.append("Line ").append(i).append(": Content content content.\n");
        }

        List<MemoryChunk> chunks = chunker.chunk(content.toString(), "overlap.md");

        // If there are multiple chunks, verify overlap exists
        if (chunks.size() > 1) {
            for (int i = 1; i < chunks.size(); i++) {
                MemoryChunk prev = chunks.get(i - 1);
                MemoryChunk curr = chunks.get(i);

                // Current chunk should start before or at previous chunk's end
                assertTrue(curr.getStartLine() <= prev.getEndLine(),
                    "Chunks should have overlapping line ranges");
            }
        }
    }

    @Test
    void testHashIsDeterministic() {
        String content = "Test content";
        String hash1 = MarkdownChunker.computeHash(content);
        String hash2 = MarkdownChunker.computeHash(content);

        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length()); // SHA-256 produces 64 hex chars
    }

    @Test
    void testDifferentContentDifferentHash() {
        String hash1 = MarkdownChunker.computeHash("Content A");
        String hash2 = MarkdownChunker.computeHash("Content B");

        assertNotEquals(hash1, hash2);
    }

    @Test
    void testMemoryTypeIsPreserved() {
        String content = "# Test\n\nSome content.";
        List<MemoryChunk> chunks = chunker.chunk(content, "test.md", MemoryType.EPHEMERAL);

        assertEquals(1, chunks.size());
        assertEquals(MemoryType.EPHEMERAL, chunks.get(0).getType());
    }

    @Test
    void testEstimateTokens() {
        assertEquals(0, MarkdownChunker.estimateTokens(null));
        assertEquals(0, MarkdownChunker.estimateTokens(""));
        assertEquals(1, MarkdownChunker.estimateTokens("test"));  // 4 chars ≈ 1 token
        assertEquals(25, MarkdownChunker.estimateTokens("a".repeat(100)));  // 100/4 = 25
    }

    @Test
    void testMarkdownStructurePreserved() {
        String markdown = """
            # Heading 1

            Some paragraph text here.

            ## Heading 2

            - List item 1
            - List item 2

            ```java
            code block
            ```
            """;

        List<MemoryChunk> chunks = chunker.chunk(markdown, "doc.md");

        // Content should be preserved (possibly across chunks)
        String allContent = chunks.stream()
            .map(MemoryChunk::getContent)
            .reduce("", (a, b) -> a + b);

        // Key elements should be present
        assertTrue(allContent.contains("# Heading 1"));
        assertTrue(allContent.contains("## Heading 2"));
        assertTrue(allContent.contains("- List item 1"));
    }

    @Test
    void testUniqueIdsGenerated() {
        String content = "Line 1\nLine 2\nLine 3\n".repeat(50);
        List<MemoryChunk> chunks = chunker.chunk(content, "test.md");

        long uniqueIds = chunks.stream()
            .map(MemoryChunk::getId)
            .distinct()
            .count();

        assertEquals(chunks.size(), uniqueIds, "All chunk IDs should be unique");
    }
}
