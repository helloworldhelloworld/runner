package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemoryChunk - 记忆块模型")
class MemoryChunkTest {

    @Nested
    @DisplayName("构造与校验")
    class Construction {

        @Test
        @DisplayName("builder 创建完整对象")
        void shouldBuildCompleteChunk() {
            Instant now = Instant.now();
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("chunk-1")
                    .content("Hello world")
                    .sourceFile("test.md")
                    .startLine(1)
                    .endLine(5)
                    .hash("abc123")
                    .createdAt(now)
                    .type(MemoryType.EPHEMERAL)
                    .build();

            assertEquals("chunk-1", chunk.getId());
            assertEquals("Hello world", chunk.getContent());
            assertEquals("test.md", chunk.getSourceFile());
            assertEquals(1, chunk.getStartLine());
            assertEquals(5, chunk.getEndLine());
            assertEquals("abc123", chunk.getHash());
            assertEquals(now, chunk.getCreatedAt());
            assertEquals(MemoryType.EPHEMERAL, chunk.getType());
        }

        @Test
        @DisplayName("id 为 null 抛出 NullPointerException")
        void shouldThrowWhenIdIsNull() {
            assertThrows(NullPointerException.class, () ->
                    MemoryChunk.builder()
                            .content("text")
                            .hash("h1")
                            .build());
        }

        @Test
        @DisplayName("content 为 null 抛出 NullPointerException")
        void shouldThrowWhenContentIsNull() {
            assertThrows(NullPointerException.class, () ->
                    MemoryChunk.builder()
                            .id("id-1")
                            .hash("h1")
                            .build());
        }

        @Test
        @DisplayName("hash 为 null 抛出 NullPointerException")
        void shouldThrowWhenHashIsNull() {
            assertThrows(NullPointerException.class, () ->
                    MemoryChunk.builder()
                            .id("id-1")
                            .content("text")
                            .build());
        }

        @Test
        @DisplayName("createdAt 为 null 默认为当前时间")
        void shouldDefaultCreatedAtToNow() {
            Instant before = Instant.now();
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("id-1").content("text").hash("h1").build();
            Instant after = Instant.now();

            assertNotNull(chunk.getCreatedAt());
            assertFalse(chunk.getCreatedAt().isBefore(before));
            assertFalse(chunk.getCreatedAt().isAfter(after));
        }

        @Test
        @DisplayName("type 为 null 默认为 DURABLE")
        void shouldDefaultTypeToDurable() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("id-1").content("text").hash("h1").build();
            assertEquals(MemoryType.DURABLE, chunk.getType());
        }

        @Test
        @DisplayName("sourceFile 可以为 null")
        void shouldAllowNullSourceFile() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("id-1").content("text").hash("h1").build();
            assertNull(chunk.getSourceFile());
        }
    }

    @Nested
    @DisplayName("Embedding 操作")
    class EmbeddingOperations {

        @Test
        @DisplayName("初始无 embedding")
        void shouldNotHaveEmbeddingInitially() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("id-1").content("text").hash("h1").build();
            assertFalse(chunk.hasEmbedding());
            assertNull(chunk.getEmbedding());
        }

        @Test
        @DisplayName("设置 embedding 后 hasEmbedding 返回 true")
        void shouldHaveEmbeddingAfterSetting() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("id-1").content("text").hash("h1").build();
            chunk.setEmbedding(new float[]{0.1f, 0.2f, 0.3f});
            assertTrue(chunk.hasEmbedding());
            assertEquals(3, chunk.getEmbedding().length);
        }

        @Test
        @DisplayName("空数组视为无 embedding")
        void shouldTreatEmptyArrayAsNoEmbedding() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("id-1").content("text").hash("h1").build();
            chunk.setEmbedding(new float[]{});
            assertFalse(chunk.hasEmbedding());
        }
    }

    @Test
    @DisplayName("toString 包含关键信息")
    void shouldContainKeyInfoInToString() {
        MemoryChunk chunk = MemoryChunk.builder()
                .id("id-1").content("text").sourceFile("src.md")
                .startLine(10).endLine(20).hash("h1")
                .type(MemoryType.SESSION).build();
        String str = chunk.toString();
        assertTrue(str.contains("id-1"));
        assertTrue(str.contains("src.md"));
        assertTrue(str.contains("10-20"));
        assertTrue(str.contains("SESSION"));
    }
}
