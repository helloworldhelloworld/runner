package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemoryChunk - 记忆内容块")
class MemoryChunkTest {

    @Nested
    @DisplayName("Builder 构建")
    class BuilderTests {

        @Test
        @DisplayName("完整构建并验证所有字段")
        void fullBuild() {
            Instant now = Instant.now();
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("chunk-1")
                    .content("This is a memory chunk")
                    .sourceFile("memory/2024-01-01.md")
                    .startLine(10)
                    .endLine(20)
                    .hash("abc123")
                    .createdAt(now)
                    .type(MemoryType.EPHEMERAL)
                    .build();

            assertEquals("chunk-1", chunk.getId());
            assertEquals("This is a memory chunk", chunk.getContent());
            assertEquals("memory/2024-01-01.md", chunk.getSourceFile());
            assertEquals(10, chunk.getStartLine());
            assertEquals(20, chunk.getEndLine());
            assertEquals("abc123", chunk.getHash());
            assertEquals(now, chunk.getCreatedAt());
            assertEquals(MemoryType.EPHEMERAL, chunk.getType());
        }

        @Test
        @DisplayName("null id 抛出 NullPointerException")
        void nullIdThrows() {
            assertThrows(NullPointerException.class,
                    () -> MemoryChunk.builder()
                            .content("text")
                            .hash("h")
                            .build());
        }

        @Test
        @DisplayName("null content 抛出 NullPointerException")
        void nullContentThrows() {
            assertThrows(NullPointerException.class,
                    () -> MemoryChunk.builder()
                            .id("id")
                            .hash("h")
                            .build());
        }

        @Test
        @DisplayName("null createdAt 默认为当前时间")
        void nullCreatedAtDefaultsToNow() {
            Instant before = Instant.now();
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("id").content("text").hash("h").build();
            assertNotNull(chunk.getCreatedAt());
            assertFalse(chunk.getCreatedAt().isBefore(before));
        }

        @Test
        @DisplayName("null type 默认为 DURABLE")
        void nullTypeDefaultsToDurable() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("id").content("text").hash("h").build();
            assertEquals(MemoryType.DURABLE, chunk.getType());
        }
    }

    @Nested
    @DisplayName("Embedding 管理")
    class EmbeddingTests {

        @Test
        @DisplayName("初始无 embedding")
        void noEmbeddingInitially() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("id").content("text").hash("h").build();
            assertFalse(chunk.hasEmbedding());
            assertNull(chunk.getEmbedding());
        }

        @Test
        @DisplayName("设置 embedding 后 hasEmbedding 返回 true")
        void setEmbedding() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("id").content("text").hash("h").build();

            float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
            chunk.setEmbedding(embedding);

            assertTrue(chunk.hasEmbedding());
            assertArrayEquals(new float[]{0.1f, 0.2f, 0.3f}, chunk.getEmbedding());
        }

        @Test
        @DisplayName("空数组 embedding 视为无 embedding")
        void emptyEmbeddingIsNotPresent() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("id").content("text").hash("h").build();
            chunk.setEmbedding(new float[0]);
            assertFalse(chunk.hasEmbedding());
        }
    }

    @Test
    @DisplayName("toString 包含关键信息")
    void toStringContainsKeyInfo() {
        MemoryChunk chunk = MemoryChunk.builder()
                .id("chunk-1")
                .content("content")
                .sourceFile("file.md")
                .startLine(1).endLine(10)
                .hash("h")
                .type(MemoryType.SESSION)
                .build();
        String s = chunk.toString();
        assertTrue(s.contains("chunk-1"));
        assertTrue(s.contains("file.md"));
        assertTrue(s.contains("SESSION"));
    }
}
