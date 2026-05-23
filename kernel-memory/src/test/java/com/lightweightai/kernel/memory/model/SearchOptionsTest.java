package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SearchOptions - 搜索选项")
class SearchOptionsTest {

    @Nested
    @DisplayName("默认值")
    class DefaultValues {

        @Test
        @DisplayName("defaults() 返回标准默认值")
        void standardDefaults() {
            SearchOptions opts = SearchOptions.defaults();

            assertEquals(10, opts.getTopK());
            assertEquals(0.7f, opts.getVectorWeight(), 0.001f);
            assertEquals(Set.of(MemoryType.values()), opts.getMemoryTypes());
            assertNull(opts.getSessionId());
            assertEquals(700, opts.getSnippetLength());
        }

        @Test
        @DisplayName("Builder 不设置任何值时等同 defaults()")
        void builderDefaults() {
            SearchOptions opts = SearchOptions.builder().build();

            assertEquals(10, opts.getTopK());
            assertEquals(0.7f, opts.getVectorWeight(), 0.001f);
            assertEquals(700, opts.getSnippetLength());
        }
    }

    @Nested
    @DisplayName("自定义参数")
    class CustomParameters {

        @Test
        @DisplayName("自定义 topK")
        void customTopK() {
            SearchOptions opts = SearchOptions.builder().topK(5).build();
            assertEquals(5, opts.getTopK());
        }

        @Test
        @DisplayName("自定义 vectorWeight")
        void customVectorWeight() {
            SearchOptions opts = SearchOptions.builder().vectorWeight(0.3f).build();
            assertEquals(0.3f, opts.getVectorWeight(), 0.001f);
        }

        @Test
        @DisplayName("自定义 memoryTypes")
        void customMemoryTypes() {
            SearchOptions opts = SearchOptions.builder()
                    .memoryTypes(Set.of(MemoryType.DURABLE))
                    .build();
            assertEquals(Set.of(MemoryType.DURABLE), opts.getMemoryTypes());
        }

        @Test
        @DisplayName("自定义 sessionId")
        void customSessionId() {
            SearchOptions opts = SearchOptions.builder()
                    .sessionId("session-123")
                    .build();
            assertEquals("session-123", opts.getSessionId());
        }

        @Test
        @DisplayName("自定义 snippetLength")
        void customSnippetLength() {
            SearchOptions opts = SearchOptions.builder()
                    .snippetLength(200)
                    .build();
            assertEquals(200, opts.getSnippetLength());
        }
    }

    @Nested
    @DisplayName("vectorWeight 边界")
    class VectorWeightBounds {

        @Test
        @DisplayName("vectorWeight 超过 1.0 时钳位到 1.0")
        void clampAboveOne() {
            SearchOptions opts = SearchOptions.builder()
                    .vectorWeight(1.5f)
                    .build();
            assertEquals(1.0f, opts.getVectorWeight(), 0.001f);
        }

        @Test
        @DisplayName("vectorWeight 低于 0.0 时钳位到 0.0")
        void clampBelowZero() {
            SearchOptions opts = SearchOptions.builder()
                    .vectorWeight(-0.5f)
                    .build();
            assertEquals(0.0f, opts.getVectorWeight(), 0.001f);
        }

        @Test
        @DisplayName("vectorWeight 正好 0.0 和 1.0 保持不变")
        void exactBoundaryValues() {
            assertEquals(0.0f, SearchOptions.builder().vectorWeight(0.0f).build().getVectorWeight(), 0.001f);
            assertEquals(1.0f, SearchOptions.builder().vectorWeight(1.0f).build().getVectorWeight(), 0.001f);
        }
    }
}
