package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemoryChunk — 记忆内容块")
class MemoryChunkTest {

    @Nested
    @DisplayName("构造与基本属性")
    class ConstructionTests {

        @Test
        @DisplayName("Builder 构造所有字段")
        void builderSetsAllFields() {
            Instant now = Instant.now();
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("chunk-1")
                    .content("Test content")
                    .sourceFile("test.md")
                    .startLine(10)
                    .endLine(20)
                    .hash("abc123")
                    .createdAt(now)
                    .type(MemoryType.DURABLE)
                    .build();

            assertEquals("chunk-1", chunk.getId());
            assertEquals("Test content", chunk.getContent());
            assertEquals("test.md", chunk.getSourceFile());
            assertEquals(10, chunk.getStartLine());
            assertEquals(20, chunk.getEndLine());
            assertEquals("abc123", chunk.getHash());
            assertEquals(now, chunk.getCreatedAt());
            assertEquals(MemoryType.DURABLE, chunk.getType());
        }

        @Test
        @DisplayName("id 为 null 时抛出 NullPointerException")
        void idRequired() {
            assertThrows(NullPointerException.class, () ->
                    MemoryChunk.builder()
                            .content("x")
                            .hash("h")
                            .build());
        }

        @Test
        @DisplayName("content 为 null 时抛出 NullPointerException")
        void contentRequired() {
            assertThrows(NullPointerException.class, () ->
                    MemoryChunk.builder()
                            .id("1")
                            .hash("h")
                            .build());
        }

        @Test
        @DisplayName("hash 为 null 时抛出 NullPointerException")
        void hashRequired() {
            assertThrows(NullPointerException.class, () ->
                    MemoryChunk.builder()
                            .id("1")
                            .content("x")
                            .build());
        }

        @Test
        @DisplayName("createdAt 默认为当前时间")
        void defaultCreatedAt() {
            Instant before = Instant.now();
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("1").content("x").hash("h").build();
            assertNotNull(chunk.getCreatedAt());
            assertFalse(chunk.getCreatedAt().isBefore(before));
        }

        @Test
        @DisplayName("type 默认为 DURABLE")
        void defaultType() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("1").content("x").hash("h").build();
            assertEquals(MemoryType.DURABLE, chunk.getType());
        }
    }

    @Nested
    @DisplayName("Embedding 操作")
    class EmbeddingTests {

        @Test
        @DisplayName("新建 chunk 无 embedding")
        void noEmbeddingByDefault() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("1").content("x").hash("h").build();
            assertFalse(chunk.hasEmbedding());
            assertNull(chunk.getEmbedding());
        }

        @Test
        @DisplayName("设置 embedding 后 hasEmbedding 返回 true")
        void setEmbedding() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("1").content("x").hash("h").build();

            float[] embedding = {0.1f, 0.2f, 0.3f};
            chunk.setEmbedding(embedding);

            assertTrue(chunk.hasEmbedding());
            assertArrayEquals(embedding, chunk.getEmbedding());
        }

        @Test
        @DisplayName("空数组 embedding — hasEmbedding 返回 false")
        void emptyEmbedding() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("1").content("x").hash("h").build();

            chunk.setEmbedding(new float[0]);
            assertFalse(chunk.hasEmbedding());
        }
    }

    @Test
    @DisplayName("toString 包含关键信息")
    void toStringFormat() {
        MemoryChunk chunk = MemoryChunk.builder()
                .id("c1")
                .content("hello")
                .sourceFile("src.md")
                .startLine(5)
                .endLine(10)
                .hash("h")
                .type(MemoryType.EPHEMERAL)
                .build();

        String str = chunk.toString();
        assertTrue(str.contains("c1"));
        assertTrue(str.contains("src.md"));
        assertTrue(str.contains("EPHEMERAL"));
    }
}
