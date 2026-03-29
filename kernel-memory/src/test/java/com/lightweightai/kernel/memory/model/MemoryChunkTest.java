package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemoryChunk - memory content with metadata")
class MemoryChunkTest {

    @Test
    void builder_createsChunkWithAllFields() {
        Instant now = Instant.now();
        MemoryChunk chunk = MemoryChunk.builder()
            .id("chunk-1")
            .content("some content here")
            .sourceFile("daily.md")
            .startLine(10)
            .endLine(20)
            .hash("abc123")
            .createdAt(now)
            .type(MemoryType.EPHEMERAL)
            .build();

        assertEquals("chunk-1", chunk.getId());
        assertEquals("some content here", chunk.getContent());
        assertEquals("daily.md", chunk.getSourceFile());
        assertEquals(10, chunk.getStartLine());
        assertEquals(20, chunk.getEndLine());
        assertEquals("abc123", chunk.getHash());
        assertEquals(now, chunk.getCreatedAt());
        assertEquals(MemoryType.EPHEMERAL, chunk.getType());
    }

    @Test
    void nullCreatedAt_defaultsToNow() {
        MemoryChunk chunk = MemoryChunk.builder()
            .id("c1")
            .content("content")
            .hash("h1")
            .build();

        assertNotNull(chunk.getCreatedAt());
    }

    @Test
    void nullType_defaultsToDurable() {
        MemoryChunk chunk = MemoryChunk.builder()
            .id("c1")
            .content("content")
            .hash("h1")
            .build();

        assertEquals(MemoryType.DURABLE, chunk.getType());
    }

    @Test
    void embedding_initiallyNull() {
        MemoryChunk chunk = MemoryChunk.builder()
            .id("c1").content("c").hash("h").build();

        assertNull(chunk.getEmbedding());
        assertFalse(chunk.hasEmbedding());
    }

    @Test
    void setEmbedding_makesHasEmbeddingTrue() {
        MemoryChunk chunk = MemoryChunk.builder()
            .id("c1").content("c").hash("h").build();

        chunk.setEmbedding(new float[]{0.1f, 0.2f, 0.3f});
        assertTrue(chunk.hasEmbedding());
        assertEquals(3, chunk.getEmbedding().length);
    }

    @Test
    void emptyEmbedding_hasEmbeddingFalse() {
        MemoryChunk chunk = MemoryChunk.builder()
            .id("c1").content("c").hash("h").build();

        chunk.setEmbedding(new float[]{});
        assertFalse(chunk.hasEmbedding());
    }

    @Test
    void toString_containsId() {
        MemoryChunk chunk = MemoryChunk.builder()
            .id("chunk-42").content("c").hash("h")
            .sourceFile("test.md").startLine(1).endLine(5)
            .type(MemoryType.SESSION).build();

        String str = chunk.toString();
        assertTrue(str.contains("chunk-42"));
        assertTrue(str.contains("test.md"));
        assertTrue(str.contains("SESSION"));
    }

    @Test
    void nullId_throwsNPE() {
        assertThrows(NullPointerException.class, () ->
            MemoryChunk.builder().content("c").hash("h").build());
    }

    @Test
    void nullContent_throwsNPE() {
        assertThrows(NullPointerException.class, () ->
            MemoryChunk.builder().id("id").hash("h").build());
    }

    @Test
    void nullHash_throwsNPE() {
        assertThrows(NullPointerException.class, () ->
            MemoryChunk.builder().id("id").content("c").build());
    }
}
