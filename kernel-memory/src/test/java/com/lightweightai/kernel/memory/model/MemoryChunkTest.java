package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemoryChunk - 记忆内容分块")
class MemoryChunkTest {

    @Nested
    @DisplayName("构造与默认值")
    class Construction {

        @Test
        @DisplayName("Builder 构建完整 chunk")
        void builderFullBuild() {
            Instant now = Instant.now();
            MemoryChunk chunk = MemoryChunk.builder()
                .id("chunk-001")
                .content("some code")
                .sourceFile("Main.java")
                .startLine(10)
                .endLine(20)
                .hash("abc123")
                .createdAt(now)
                .type(MemoryType.DURABLE)
                .build();

            assertEquals("chunk-001", chunk.getId());
            assertEquals("some code", chunk.getContent());
            assertEquals("Main.java", chunk.getSourceFile());
            assertEquals(10, chunk.getStartLine());
            assertEquals(20, chunk.getEndLine());
            assertEquals("abc123", chunk.getHash());
            assertEquals(now, chunk.getCreatedAt());
            assertEquals(MemoryType.DURABLE, chunk.getType());
        }

        @Test
        @DisplayName("createdAt 为 null 时默认为当前时间")
        void nullCreatedAtDefaultsToNow() {
            MemoryChunk chunk = MemoryChunk.builder()
                .id("c1").content("x").hash("h")
                .build();

            assertNotNull(chunk.getCreatedAt());
        }

        @Test
        @DisplayName("type 为 null 时默认为 DURABLE")
        void nullTypeDefaultsToDurable() {
            MemoryChunk chunk = MemoryChunk.builder()
                .id("c1").content("x").hash("h")
                .build();

            assertEquals(MemoryType.DURABLE, chunk.getType());
        }

        @Test
        @DisplayName("id 不能为 null")
        void nullIdThrows() {
            assertThrows(NullPointerException.class, () ->
                MemoryChunk.builder().content("x").hash("h").build());
        }

        @Test
        @DisplayName("content 不能为 null")
        void nullContentThrows() {
            assertThrows(NullPointerException.class, () ->
                MemoryChunk.builder().id("c1").hash("h").build());
        }

        @Test
        @DisplayName("hash 不能为 null")
        void nullHashThrows() {
            assertThrows(NullPointerException.class, () ->
                MemoryChunk.builder().id("c1").content("x").build());
        }
    }

    @Nested
    @DisplayName("Embedding 管理")
    class EmbeddingTests {

        @Test
        @DisplayName("初始无 embedding")
        void noEmbeddingInitially() {
            MemoryChunk chunk = MemoryChunk.builder()
                .id("c1").content("x").hash("h").build();

            assertFalse(chunk.hasEmbedding());
            assertNull(chunk.getEmbedding());
        }

        @Test
        @DisplayName("设置 embedding 后可检测")
        void setEmbedding() {
            MemoryChunk chunk = MemoryChunk.builder()
                .id("c1").content("x").hash("h").build();

            chunk.setEmbedding(new float[]{0.1f, 0.2f, 0.3f});

            assertTrue(chunk.hasEmbedding());
            assertEquals(3, chunk.getEmbedding().length);
            assertEquals(0.1f, chunk.getEmbedding()[0], 0.001f);
        }

        @Test
        @DisplayName("空数组视为无 embedding")
        void emptyEmbeddingCountsAsNone() {
            MemoryChunk chunk = MemoryChunk.builder()
                .id("c1").content("x").hash("h").build();

            chunk.setEmbedding(new float[]{});

            assertFalse(chunk.hasEmbedding());
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("包含 id, sourceFile, lines, type")
        void containsKeyFields() {
            MemoryChunk chunk = MemoryChunk.builder()
                .id("c1").content("code").hash("h")
                .sourceFile("App.java").startLine(5).endLine(15)
                .type(MemoryType.SESSION)
                .build();

            String str = chunk.toString();
            assertTrue(str.contains("c1"));
            assertTrue(str.contains("App.java"));
            assertTrue(str.contains("5"));
            assertTrue(str.contains("15"));
            assertTrue(str.contains("SESSION"));
        }
    }
}
