package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemoryChunk")
class MemoryChunkTest {

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("stores all fields when fully specified")
        void allFieldsSet() {
            Instant now = Instant.parse("2025-01-15T10:30:00Z");
            MemoryChunk chunk = new MemoryChunk(
                    "chunk-1", "some content", "src/Main.java",
                    10, 20, "abc123", now, MemoryType.SESSION);

            assertEquals("chunk-1", chunk.getId());
            assertEquals("some content", chunk.getContent());
            assertEquals("src/Main.java", chunk.getSourceFile());
            assertEquals(10, chunk.getStartLine());
            assertEquals(20, chunk.getEndLine());
            assertEquals("abc123", chunk.getHash());
            assertEquals(now, chunk.getCreatedAt());
            assertEquals(MemoryType.SESSION, chunk.getType());
        }

        @Test
        @DisplayName("defaults createdAt to Instant.now() when null")
        void defaultsCreatedAt() {
            Instant before = Instant.now();
            MemoryChunk chunk = new MemoryChunk(
                    "id", "content", null, 0, 0, "hash", null, MemoryType.DURABLE);
            Instant after = Instant.now();

            assertNotNull(chunk.getCreatedAt());
            assertFalse(chunk.getCreatedAt().isBefore(before));
            assertFalse(chunk.getCreatedAt().isAfter(after));
        }

        @Test
        @DisplayName("defaults type to DURABLE when null")
        void defaultsTypeToDurable() {
            MemoryChunk chunk = new MemoryChunk(
                    "id", "content", null, 0, 0, "hash", Instant.now(), null);

            assertEquals(MemoryType.DURABLE, chunk.getType());
        }

        @Test
        @DisplayName("allows null sourceFile")
        void nullSourceFile() {
            MemoryChunk chunk = new MemoryChunk(
                    "id", "content", null, 0, 0, "hash", Instant.now(), MemoryType.EPHEMERAL);

            assertNull(chunk.getSourceFile());
        }

        @Test
        @DisplayName("throws NullPointerException when id is null")
        void nullIdThrows() {
            assertThrows(NullPointerException.class, () ->
                    new MemoryChunk(null, "content", null, 0, 0, "hash", Instant.now(), MemoryType.DURABLE));
        }

        @Test
        @DisplayName("throws NullPointerException when content is null")
        void nullContentThrows() {
            assertThrows(NullPointerException.class, () ->
                    new MemoryChunk("id", null, null, 0, 0, "hash", Instant.now(), MemoryType.DURABLE));
        }

        @Test
        @DisplayName("throws NullPointerException when hash is null")
        void nullHashThrows() {
            assertThrows(NullPointerException.class, () ->
                    new MemoryChunk("id", "content", null, 0, 0, null, Instant.now(), MemoryType.DURABLE));
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("builds chunk with all fields via builder")
        void buildsWithAllFields() {
            Instant ts = Instant.parse("2025-06-01T12:00:00Z");
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("b-1")
                    .content("builder content")
                    .sourceFile("file.py")
                    .startLine(5)
                    .endLine(15)
                    .hash("h456")
                    .createdAt(ts)
                    .type(MemoryType.EPHEMERAL)
                    .build();

            assertEquals("b-1", chunk.getId());
            assertEquals("builder content", chunk.getContent());
            assertEquals("file.py", chunk.getSourceFile());
            assertEquals(5, chunk.getStartLine());
            assertEquals(15, chunk.getEndLine());
            assertEquals("h456", chunk.getHash());
            assertEquals(ts, chunk.getCreatedAt());
            assertEquals(MemoryType.EPHEMERAL, chunk.getType());
        }

        @Test
        @DisplayName("builder defaults apply when createdAt and type omitted")
        void builderDefaults() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("b-2")
                    .content("minimal")
                    .hash("h789")
                    .build();

            assertNotNull(chunk.getCreatedAt());
            assertEquals(MemoryType.DURABLE, chunk.getType());
        }
    }

    @Nested
    @DisplayName("Embedding")
    class EmbeddingTests {

        @Test
        @DisplayName("hasEmbedding returns false when embedding not set")
        void noEmbedding() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("e-1").content("c").hash("h").build();

            assertFalse(chunk.hasEmbedding());
            assertNull(chunk.getEmbedding());
        }

        @Test
        @DisplayName("hasEmbedding returns false when embedding is empty array")
        void emptyEmbedding() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("e-2").content("c").hash("h").build();
            chunk.setEmbedding(new float[0]);

            assertFalse(chunk.hasEmbedding());
        }

        @Test
        @DisplayName("hasEmbedding returns true after setEmbedding with non-empty array")
        void validEmbedding() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("e-3").content("c").hash("h").build();
            float[] emb = {0.1f, 0.2f, 0.3f};
            chunk.setEmbedding(emb);

            assertTrue(chunk.hasEmbedding());
            assertArrayEquals(emb, chunk.getEmbedding());
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("includes id, sourceFile, line range, and type")
        void toStringContainsFields() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("ts-1")
                    .content("irrelevant")
                    .sourceFile("App.java")
                    .startLine(1)
                    .endLine(42)
                    .hash("x")
                    .type(MemoryType.SESSION)
                    .build();

            String str = chunk.toString();
            assertTrue(str.contains("ts-1"), "should contain id");
            assertTrue(str.contains("App.java"), "should contain sourceFile");
            assertTrue(str.contains("1-42"), "should contain line range");
            assertTrue(str.contains("SESSION"), "should contain type");
        }
    }
}
