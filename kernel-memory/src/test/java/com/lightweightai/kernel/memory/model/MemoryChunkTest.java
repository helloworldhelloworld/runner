package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemoryChunk")
class MemoryChunkTest {

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        void shouldCreateWithAllFields() {
            Instant now = Instant.now();
            MemoryChunk chunk = new MemoryChunk(
                    "id-1", "test content", "file.md", 1, 10, "hash123", now, MemoryType.DURABLE);

            assertEquals("id-1", chunk.getId());
            assertEquals("test content", chunk.getContent());
            assertEquals("file.md", chunk.getSourceFile());
            assertEquals(1, chunk.getStartLine());
            assertEquals(10, chunk.getEndLine());
            assertEquals("hash123", chunk.getHash());
            assertEquals(now, chunk.getCreatedAt());
            assertEquals(MemoryType.DURABLE, chunk.getType());
        }

        @Test
        void shouldDefaultToNowWhenCreatedAtIsNull() {
            Instant before = Instant.now();
            MemoryChunk chunk = new MemoryChunk(
                    "id-1", "content", "file.md", 0, 0, "hash", null, MemoryType.DURABLE);

            assertNotNull(chunk.getCreatedAt());
            assertFalse(chunk.getCreatedAt().isBefore(before));
        }

        @Test
        void shouldDefaultToDurableWhenTypeIsNull() {
            MemoryChunk chunk = new MemoryChunk(
                    "id-1", "content", "file.md", 0, 0, "hash", Instant.now(), null);

            assertEquals(MemoryType.DURABLE, chunk.getType());
        }

        @Test
        void shouldRejectNullId() {
            assertThrows(NullPointerException.class, () ->
                    new MemoryChunk(null, "content", "file.md", 0, 0, "hash", Instant.now(), MemoryType.DURABLE));
        }

        @Test
        void shouldRejectNullContent() {
            assertThrows(NullPointerException.class, () ->
                    new MemoryChunk("id", null, "file.md", 0, 0, "hash", Instant.now(), MemoryType.DURABLE));
        }

        @Test
        void shouldRejectNullHash() {
            assertThrows(NullPointerException.class, () ->
                    new MemoryChunk("id", "content", "file.md", 0, 0, null, Instant.now(), MemoryType.DURABLE));
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        void shouldBuildWithBuilder() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("id-1")
                    .content("hello")
                    .hash("h1")
                    .sourceFile("test.md")
                    .startLine(5)
                    .endLine(15)
                    .type(MemoryType.EPHEMERAL)
                    .build();

            assertEquals("id-1", chunk.getId());
            assertEquals("hello", chunk.getContent());
            assertEquals("test.md", chunk.getSourceFile());
            assertEquals(5, chunk.getStartLine());
            assertEquals(15, chunk.getEndLine());
            assertEquals(MemoryType.EPHEMERAL, chunk.getType());
        }
    }

    @Nested
    @DisplayName("Embedding")
    class EmbeddingTests {

        @Test
        void shouldStartWithoutEmbedding() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("id").content("text").hash("h").build();

            assertFalse(chunk.hasEmbedding());
            assertNull(chunk.getEmbedding());
        }

        @Test
        void shouldSetAndGetEmbedding() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("id").content("text").hash("h").build();

            float[] embedding = {0.1f, 0.2f, 0.3f};
            chunk.setEmbedding(embedding);

            assertTrue(chunk.hasEmbedding());
            assertArrayEquals(embedding, chunk.getEmbedding());
        }

        @Test
        void shouldReportNoEmbeddingForEmptyArray() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("id").content("text").hash("h").build();

            chunk.setEmbedding(new float[0]);
            assertFalse(chunk.hasEmbedding());
        }
    }

    @Test
    void shouldIncludeIdInToString() {
        MemoryChunk chunk = MemoryChunk.builder()
                .id("chunk-42").content("text").hash("h").sourceFile("test.md")
                .startLine(1).endLine(5).type(MemoryType.SESSION).build();

        String str = chunk.toString();
        assertTrue(str.contains("chunk-42"));
        assertTrue(str.contains("test.md"));
        assertTrue(str.contains("SESSION"));
    }
}
