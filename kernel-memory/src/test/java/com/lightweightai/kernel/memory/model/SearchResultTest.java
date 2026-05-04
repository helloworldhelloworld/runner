package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SearchResult & SearchOptions & MemoryChunk — Memory 模型层")
class SearchResultTest {

    @Nested
    @DisplayName("MemoryChunk")
    class MemoryChunkTests {

        @Test
        @DisplayName("Builder 构建完整 MemoryChunk")
        void builderCreatesChunk() {
            Instant now = Instant.now();
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("chunk-1")
                    .content("Test content")
                    .sourceFile("test.md")
                    .startLine(1)
                    .endLine(10)
                    .hash("abc123")
                    .createdAt(now)
                    .type(MemoryType.DURABLE)
                    .build();

            assertEquals("chunk-1", chunk.getId());
            assertEquals("Test content", chunk.getContent());
            assertEquals("test.md", chunk.getSourceFile());
            assertEquals(1, chunk.getStartLine());
            assertEquals(10, chunk.getEndLine());
            assertEquals("abc123", chunk.getHash());
            assertEquals(now, chunk.getCreatedAt());
            assertEquals(MemoryType.DURABLE, chunk.getType());
        }

        @Test
        @DisplayName("默认 type 为 DURABLE")
        void defaultTypeDurable() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("c1").content("x").hash("h1").build();
            assertEquals(MemoryType.DURABLE, chunk.getType());
        }

        @Test
        @DisplayName("默认 createdAt 为当前时间")
        void defaultCreatedAtIsNow() {
            Instant before = Instant.now();
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("c1").content("x").hash("h1").build();
            assertTrue(chunk.getCreatedAt().compareTo(before) >= 0);
        }

        @Test
        @DisplayName("embedding 默认为 null，hasEmbedding 为 false")
        void noEmbeddingByDefault() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("c1").content("x").hash("h1").build();
            assertFalse(chunk.hasEmbedding());
            assertNull(chunk.getEmbedding());
        }

        @Test
        @DisplayName("设置 embedding 后 hasEmbedding 为 true")
        void embeddingCanBeSet() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("c1").content("x").hash("h1").build();
            chunk.setEmbedding(new float[]{0.1f, 0.2f, 0.3f});
            assertTrue(chunk.hasEmbedding());
            assertEquals(3, chunk.getEmbedding().length);
        }

        @Test
        @DisplayName("null id 抛 NPE")
        void nullIdThrows() {
            assertThrows(NullPointerException.class, () ->
                    MemoryChunk.builder().id(null).content("x").hash("h").build());
        }

        @Test
        @DisplayName("toString 包含关键信息")
        void toStringContainsKeyInfo() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("c1").content("x").sourceFile("f.md")
                    .startLine(1).endLine(5).hash("h1")
                    .type(MemoryType.EPHEMERAL).build();
            String str = chunk.toString();
            assertTrue(str.contains("c1"));
            assertTrue(str.contains("f.md"));
            assertTrue(str.contains("EPHEMERAL"));
        }
    }

    @Nested
    @DisplayName("SearchResult")
    class SearchResultTests {

        @Test
        @DisplayName("Builder 构建完整 SearchResult")
        void builderCreatesResult() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("c1").content("test").hash("h1").sourceFile("s.md").build();

            SearchResult result = SearchResult.builder()
                    .chunk(chunk)
                    .score(0.95f)
                    .bm25Score(0.8f)
                    .vectorScore(0.99f)
                    .snippet("test snippet...")
                    .build();

            assertEquals(chunk, result.getChunk());
            assertEquals(0.95f, result.getScore(), 0.001f);
            assertEquals(0.8f, result.getBm25Score(), 0.001f);
            assertEquals(0.99f, result.getVectorScore(), 0.001f);
            assertEquals("test snippet...", result.getSnippet());
        }

        @Test
        @DisplayName("toString 包含 chunk id 和 score")
        void toStringContainsKeyInfo() {
            MemoryChunk chunk = MemoryChunk.builder()
                    .id("c1").content("test").hash("h1").build();
            SearchResult result = SearchResult.builder()
                    .chunk(chunk).score(0.85f).snippet("a short snippet").build();
            String str = result.toString();
            assertTrue(str.contains("c1"));
            assertTrue(str.contains("0.85"));
        }
    }

    @Nested
    @DisplayName("SearchOptions")
    class SearchOptionsTests {

        @Test
        @DisplayName("默认值正确")
        void defaultValues() {
            SearchOptions opts = SearchOptions.defaults();

            assertEquals(10, opts.getTopK());
            assertEquals(0.7f, opts.getVectorWeight(), 0.001f);
            assertEquals(Set.of(MemoryType.values()), opts.getMemoryTypes());
            assertNull(opts.getSessionId());
            assertEquals(700, opts.getSnippetLength());
        }

        @Test
        @DisplayName("Builder 自定义所有字段")
        void builderCustomValues() {
            SearchOptions opts = SearchOptions.builder()
                    .topK(5)
                    .vectorWeight(0.3f)
                    .memoryTypes(Set.of(MemoryType.DURABLE))
                    .sessionId("sess-1")
                    .snippetLength(300)
                    .build();

            assertEquals(5, opts.getTopK());
            assertEquals(0.3f, opts.getVectorWeight(), 0.001f);
            assertEquals(Set.of(MemoryType.DURABLE), opts.getMemoryTypes());
            assertEquals("sess-1", opts.getSessionId());
            assertEquals(300, opts.getSnippetLength());
        }

        @Test
        @DisplayName("vectorWeight 超出范围被截断为 [0, 1]")
        void vectorWeightClamped() {
            SearchOptions high = SearchOptions.builder().vectorWeight(1.5f).build();
            assertEquals(1.0f, high.getVectorWeight(), 0.001f);

            SearchOptions low = SearchOptions.builder().vectorWeight(-0.5f).build();
            assertEquals(0.0f, low.getVectorWeight(), 0.001f);
        }
    }

    @Nested
    @DisplayName("MemoryType 枚举")
    class MemoryTypeTests {

        @Test
        @DisplayName("所有 MemoryType 值存在")
        void allTypesExist() {
            assertNotNull(MemoryType.EPHEMERAL);
            assertNotNull(MemoryType.DURABLE);
            assertNotNull(MemoryType.SESSION);
        }
    }
}
