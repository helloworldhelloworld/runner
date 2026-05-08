package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.*;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MemoryChunk} — memory content chunk with metadata.
 *
 * Covers: builder, required fields, embedding management, defaults.
 */
@DisplayName("MemoryChunk - 记忆内容块")
class MemoryChunkTest {

    // ==================== Builder ====================

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("完整 builder — 所有字段正确")
        void fullBuilder_allFieldsSet() {
            Instant now = Instant.now();
            MemoryChunk chunk = MemoryChunk.builder()
                .id("chunk-1")
                .content("Hello world")
                .sourceFile("test.md")
                .startLine(1)
                .endLine(10)
                .hash("abc123")
                .createdAt(now)
                .type(MemoryType.EPHEMERAL)
                .build();

            assertEquals("chunk-1", chunk.getId());
            assertEquals("Hello world", chunk.getContent());
            assertEquals("test.md", chunk.getSourceFile());
            assertEquals(1, chunk.getStartLine());
            assertEquals(10, chunk.getEndLine());
            assertEquals("abc123", chunk.getHash());
            assertEquals(now, chunk.getCreatedAt());
            assertEquals(MemoryType.EPHEMERAL, chunk.getType());
        }

        @Test
        @DisplayName("null id — 抛出 NullPointerException")
        void nullId_throws() {
            assertThrows(NullPointerException.class, () ->
                MemoryChunk.builder()
                    .id(null)
                    .content("text")
                    .hash("hash")
                    .build());
        }

        @Test
        @DisplayName("null content — 抛出 NullPointerException")
        void nullContent_throws() {
            assertThrows(NullPointerException.class, () ->
                MemoryChunk.builder()
                    .id("id")
                    .content(null)
                    .hash("hash")
                    .build());
        }
    }

    // ==================== Defaults ====================

    @Nested
    @DisplayName("默认值")
    class Defaults {

        @Test
        @DisplayName("null createdAt — 默认为 Instant.now()")
        void nullCreatedAt_defaultsToNow() {
            Instant before = Instant.now();
            MemoryChunk chunk = MemoryChunk.builder()
                .id("id").content("c").hash("h").createdAt(null).build();
            Instant after = Instant.now();

            assertFalse(chunk.getCreatedAt().isBefore(before));
            assertFalse(chunk.getCreatedAt().isAfter(after));
        }

        @Test
        @DisplayName("null type — 默认为 DURABLE")
        void nullType_defaultsToDurable() {
            MemoryChunk chunk = MemoryChunk.builder()
                .id("id").content("c").hash("h").type(null).build();

            assertEquals(MemoryType.DURABLE, chunk.getType());
        }
    }

    // ==================== Embedding ====================

    @Nested
    @DisplayName("Embedding 管理")
    class EmbeddingTests {

        @Test
        @DisplayName("初始无 embedding")
        void initiallyNoEmbedding() {
            MemoryChunk chunk = MemoryChunk.builder()
                .id("id").content("c").hash("h").build();

            assertNull(chunk.getEmbedding());
            assertFalse(chunk.hasEmbedding());
        }

        @Test
        @DisplayName("设置 embedding 后可获取")
        void setEmbedding_thenHasEmbedding() {
            MemoryChunk chunk = MemoryChunk.builder()
                .id("id").content("c").hash("h").build();

            float[] emb = {0.1f, 0.2f, 0.3f};
            chunk.setEmbedding(emb);

            assertTrue(chunk.hasEmbedding());
            assertArrayEquals(emb, chunk.getEmbedding());
        }

        @Test
        @DisplayName("空数组 — hasEmbedding 返回 false")
        void emptyArray_hasEmbeddingFalse() {
            MemoryChunk chunk = MemoryChunk.builder()
                .id("id").content("c").hash("h").build();

            chunk.setEmbedding(new float[0]);
            assertFalse(chunk.hasEmbedding());
        }
    }

    // ==================== toString ====================

    @Test
    @DisplayName("toString 包含关键字段")
    void toString_containsKeyFields() {
        MemoryChunk chunk = MemoryChunk.builder()
            .id("chunk-42")
            .content("test content")
            .sourceFile("file.md")
            .startLine(5)
            .endLine(15)
            .hash("xyz")
            .type(MemoryType.SESSION)
            .build();

        String str = chunk.toString();
        assertTrue(str.contains("chunk-42"));
        assertTrue(str.contains("file.md"));
        assertTrue(str.contains("SESSION"));
    }
}
