package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemoryChunk - 记忆块模型")
class MemoryChunkTest {

    @Test
    @DisplayName("Builder 正常构建")
    void builderNormal() {
        Instant now = Instant.now();
        MemoryChunk chunk = MemoryChunk.builder()
                .id("chunk-1")
                .content("Hello world")
                .sourceFile("test.md")
                .startLine(1)
                .endLine(10)
                .hash("abc123")
                .createdAt(now)
                .type(MemoryType.DURABLE)
                .build();

        assertEquals("chunk-1", chunk.getId());
        assertEquals("Hello world", chunk.getContent());
        assertEquals("test.md", chunk.getSourceFile());
        assertEquals(1, chunk.getStartLine());
        assertEquals(10, chunk.getEndLine());
        assertEquals("abc123", chunk.getHash());
        assertEquals(now, chunk.getCreatedAt());
        assertEquals(MemoryType.DURABLE, chunk.getType());
    }

    @Test
    @DisplayName("createdAt 为 null 默认为当前时间")
    void defaultCreatedAt() {
        Instant before = Instant.now();
        MemoryChunk chunk = MemoryChunk.builder()
                .id("c1").content("text").hash("h1")
                .build();
        assertNotNull(chunk.getCreatedAt());
        assertFalse(chunk.getCreatedAt().isBefore(before));
    }

    @Test
    @DisplayName("type 为 null 默认为 DURABLE")
    void defaultType() {
        MemoryChunk chunk = MemoryChunk.builder()
                .id("c1").content("text").hash("h1")
                .build();
        assertEquals(MemoryType.DURABLE, chunk.getType());
    }

    @Test
    @DisplayName("id 为 null 抛 NPE")
    void nullIdThrows() {
        assertThrows(NullPointerException.class, () ->
                MemoryChunk.builder().content("text").hash("h1").build());
    }

    @Test
    @DisplayName("content 为 null 抛 NPE")
    void nullContentThrows() {
        assertThrows(NullPointerException.class, () ->
                MemoryChunk.builder().id("c1").hash("h1").build());
    }

    @Test
    @DisplayName("hash 为 null 抛 NPE")
    void nullHashThrows() {
        assertThrows(NullPointerException.class, () ->
                MemoryChunk.builder().id("c1").content("text").build());
    }

    @Test
    @DisplayName("embedding 设置和检查")
    void embedding() {
        MemoryChunk chunk = MemoryChunk.builder()
                .id("c1").content("text").hash("h1").build();

        assertFalse(chunk.hasEmbedding());
        assertNull(chunk.getEmbedding());

        float[] vec = {0.1f, 0.2f, 0.3f};
        chunk.setEmbedding(vec);
        assertTrue(chunk.hasEmbedding());
        assertArrayEquals(vec, chunk.getEmbedding());
    }

    @Test
    @DisplayName("空 embedding 数组 hasEmbedding 返回 false")
    void emptyEmbedding() {
        MemoryChunk chunk = MemoryChunk.builder()
                .id("c1").content("text").hash("h1").build();
        chunk.setEmbedding(new float[]{});
        assertFalse(chunk.hasEmbedding());
    }

    @Test
    @DisplayName("toString 包含关键信息")
    void toStringOutput() {
        MemoryChunk chunk = MemoryChunk.builder()
                .id("c1").content("text").hash("h1")
                .sourceFile("file.md").startLine(1).endLine(5)
                .type(MemoryType.EPHEMERAL)
                .build();
        String str = chunk.toString();
        assertTrue(str.contains("c1"));
        assertTrue(str.contains("file.md"));
        assertTrue(str.contains("EPHEMERAL"));
    }
}
