package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SearchOptions - 记忆搜索选项")
class SearchOptionsTest {

    @Nested
    @DisplayName("默认值")
    class DefaultTests {

        @Test
        @DisplayName("defaults() 返回合理的默认配置")
        void defaultValues() {
            SearchOptions opts = SearchOptions.defaults();
            assertEquals(10, opts.getTopK());
            assertEquals(0.7f, opts.getVectorWeight(), 0.001f);
            assertEquals(Set.of(MemoryType.values()), opts.getMemoryTypes());
            assertNull(opts.getSessionId());
            assertEquals(700, opts.getSnippetLength());
        }
    }

    @Nested
    @DisplayName("Builder 配置")
    class BuilderTests {

        @Test
        @DisplayName("自定义所有参数")
        void customAll() {
            SearchOptions opts = SearchOptions.builder()
                    .topK(5)
                    .vectorWeight(0.3f)
                    .memoryTypes(Set.of(MemoryType.EPHEMERAL))
                    .sessionId("session-1")
                    .snippetLength(500)
                    .build();

            assertEquals(5, opts.getTopK());
            assertEquals(0.3f, opts.getVectorWeight(), 0.001f);
            assertEquals(Set.of(MemoryType.EPHEMERAL), opts.getMemoryTypes());
            assertEquals("session-1", opts.getSessionId());
            assertEquals(500, opts.getSnippetLength());
        }

        @Test
        @DisplayName("vectorWeight 超过 1.0 被裁剪到 1.0")
        void vectorWeightClampedAbove() {
            SearchOptions opts = SearchOptions.builder().vectorWeight(1.5f).build();
            assertEquals(1.0f, opts.getVectorWeight(), 0.001f);
        }

        @Test
        @DisplayName("vectorWeight 低于 0.0 被裁剪到 0.0")
        void vectorWeightClampedBelow() {
            SearchOptions opts = SearchOptions.builder().vectorWeight(-0.5f).build();
            assertEquals(0.0f, opts.getVectorWeight(), 0.001f);
        }

        @Test
        @DisplayName("vectorWeight 边界值 0.0 和 1.0 正常工作")
        void vectorWeightBoundary() {
            assertEquals(0.0f, SearchOptions.builder().vectorWeight(0.0f).build().getVectorWeight(), 0.001f);
            assertEquals(1.0f, SearchOptions.builder().vectorWeight(1.0f).build().getVectorWeight(), 0.001f);
        }
    }
}
