package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemoryChunk - 记忆块数据模型")
class MemoryChunkTest {

    @Nested
    @DisplayName("构造器验证")
    class ConstructorValidation {

        @Test
        @DisplayName("Builder 构建完整 MemoryChunk")
        void builderFullConstruction() {
            Instant now = Instant.now();
            MemoryChunk chunk = MemoryChunk.builder()
                .id("chunk-001")
                .content("Hello world")
                .sourceFile("test.md")
                .startLine(1)
                .endLine(10)
                .hash("abc123")
                .createdAt(now)
                .type(MemoryType.DURABLE)
                .build();

            assertEquals("chunk-001", chunk.getId());
            assertEquals("Hello world", chunk.getContent());
            assertEquals("test.md", chunk.getSourceFile());
            assertEquals(1, chunk.getStartLine());
            assertEquals(10, chunk.getEndLine());
            assertEquals("abc123", chunk.getHash());
            assertEquals(now, chunk.getCreatedAt());
            assertEquals(MemoryType.DURABLE, chunk.getType());
        }

        @Test
        @DisplayName("null id 抛出 NullPointerException")
        void nullIdThrows() {
            assertThrows(NullPointerException.class,
                () -> MemoryChunk.builder()
                    .content("x").hash("h").build());
        }

        @Test
        @DisplayName("null content 抛出 NullPointerException")
        void nullContentThrows() {
            assertThrows(NullPointerException.class,
                () -> MemoryChunk.builder()
                    .id("1").hash("h").build());
        }

        @Test
        @DisplayName("null createdAt 默认为当前时间")
        void nullCreatedAtDefaultsToNow() {
            MemoryChunk chunk = MemoryChunk.builder()
                .id("1").content("x").hash("h").build();
            assertNotNull(chunk.getCreatedAt());
        }

        @Test
        @DisplayName("null type 默认为 DURABLE")
        void nullTypeDefaultsToDurable() {
            MemoryChunk chunk = MemoryChunk.builder()
                .id("1").content("x").hash("h").build();
            assertEquals(MemoryType.DURABLE, chunk.getType());
        }
    }

    @Nested
    @DisplayName("Embedding 操作")
    class EmbeddingOps {

        @Test
        @DisplayName("新建 chunk 无 embedding")
        void noEmbeddingByDefault() {
            MemoryChunk chunk = MemoryChunk.builder()
                .id("1").content("x").hash("h").build();
            assertFalse(chunk.hasEmbedding());
            assertNull(chunk.getEmbedding());
        }

        @Test
        @DisplayName("设置 embedding 后 hasEmbedding 为 true")
        void setEmbedding() {
            MemoryChunk chunk = MemoryChunk.builder()
                .id("1").content("x").hash("h").build();

            chunk.setEmbedding(new float[]{0.1f, 0.2f, 0.3f});
            assertTrue(chunk.hasEmbedding());
            assertEquals(3, chunk.getEmbedding().length);
        }

        @Test
        @DisplayName("空数组 embedding 时 hasEmbedding 为 false")
        void emptyEmbeddingIsFalse() {
            MemoryChunk chunk = MemoryChunk.builder()
                .id("1").content("x").hash("h").build();

            chunk.setEmbedding(new float[]{});
            assertFalse(chunk.hasEmbedding());
        }
    }

    @Test
    @DisplayName("toString 包含关键字段")
    void toStringContainsFields() {
        MemoryChunk chunk = MemoryChunk.builder()
            .id("chunk-42")
            .content("x")
            .sourceFile("notes.md")
            .startLine(5)
            .endLine(15)
            .hash("h")
            .type(MemoryType.SESSION)
            .build();

        String str = chunk.toString();
        assertTrue(str.contains("chunk-42"));
        assertTrue(str.contains("notes.md"));
        assertTrue(str.contains("SESSION"));
    }
}
