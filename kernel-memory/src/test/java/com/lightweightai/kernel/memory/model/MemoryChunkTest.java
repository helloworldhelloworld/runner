package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemoryChunk - 内存内容块")
class MemoryChunkTest {

    @Nested
    @DisplayName("构造与必填字段")
    class ConstructionTests {

        @Test
        @DisplayName("Builder 构造所有字段正确填充")
        void builderSetsAllFields() {
            Instant now = Instant.now();
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("chunk-1")
                    .content("Hello world")
                    .sourceFile("README.md")
                    .startLine(1)
                    .endLine(10)
                    .hash("abc123")
                    .createdAt(now)
                    .type(MemoryType.DURABLE)
                    .build();

            assertEquals("chunk-1", chunk.getId());
            assertEquals("Hello world", chunk.getContent());
            assertEquals("README.md", chunk.getSourceFile());
            assertEquals(1, chunk.getStartLine());
            assertEquals(10, chunk.getEndLine());
            assertEquals("abc123", chunk.getHash());
            assertEquals(now, chunk.getCreatedAt());
            assertEquals(MemoryType.DURABLE, chunk.getType());
        }

        @Test
        @DisplayName("id 为 null 时抛 NPE")
        void idRequired() {
            assertThrows(NullPointerException.class, () ->
                    MemoryChunk.builder()
                            .content("text")
                            .hash("h")
                            .build());
        }

        @Test
        @DisplayName("content 为 null 时抛 NPE")
        void contentRequired() {
            assertThrows(NullPointerException.class, () ->
                    MemoryChunk.builder()
                            .id("1")
                            .hash("h")
                            .build());
        }

        @Test
        @DisplayName("hash 为 null 时抛 NPE")
        void hashRequired() {
            assertThrows(NullPointerException.class, () ->
                    MemoryChunk.builder()
                            .id("1")
                            .content("text")
                            .build());
        }
    }

    @Nested
    @DisplayName("默认值")
    class DefaultValues {

        @Test
        @DisplayName("createdAt 为 null 时默认为当前时间")
        void defaultCreatedAt() {
            Instant before = Instant.now();
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("1").content("text").hash("h").build();
            Instant after = Instant.now();

            assertNotNull(chunk.getCreatedAt());
            assertFalse(chunk.getCreatedAt().isBefore(before));
            assertFalse(chunk.getCreatedAt().isAfter(after));
        }

        @Test
        @DisplayName("type 为 null 时默认为 DURABLE")
        void defaultType() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("1").content("text").hash("h").build();

            assertEquals(MemoryType.DURABLE, chunk.getType());
        }

        @Test
        @DisplayName("sourceFile 可以为 null")
        void sourceFileNullable() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("1").content("text").hash("h").build();

            assertNull(chunk.getSourceFile());
        }
    }

    @Nested
    @DisplayName("Embedding 管理")
    class EmbeddingTests {

        @Test
        @DisplayName("初始无 embedding")
        void noEmbeddingByDefault() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("1").content("text").hash("h").build();

            assertFalse(chunk.hasEmbedding());
            assertNull(chunk.getEmbedding());
        }

        @Test
        @DisplayName("设置 embedding 后可检索")
        void setAndGetEmbedding() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("1").content("text").hash("h").build();

            float[] embedding = {0.1f, 0.2f, 0.3f};
            chunk.setEmbedding(embedding);

            assertTrue(chunk.hasEmbedding());
            assertArrayEquals(embedding, chunk.getEmbedding());
        }

        @Test
        @DisplayName("空数组算没有 embedding")
        void emptyEmbeddingMeansNoEmbedding() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("1").content("text").hash("h").build();

            chunk.setEmbedding(new float[0]);
            assertFalse(chunk.hasEmbedding());
        }
    }

    @Test
    @DisplayName("toString 包含 id、sourceFile、lines、type")
    void toStringContainsKeyInfo() {
        MemoryChunk chunk = MemoryChunk.builder()
                .id("chunk-42")
                .content("text")
                .sourceFile("notes.md")
                .startLine(5)
                .endLine(15)
                .hash("h")
                .type(MemoryType.EPHEMERAL)
                .build();

        String str = chunk.toString();
        assertTrue(str.contains("chunk-42"));
        assertTrue(str.contains("notes.md"));
        assertTrue(str.contains("5-15"));
        assertTrue(str.contains("EPHEMERAL"));
    }
}
