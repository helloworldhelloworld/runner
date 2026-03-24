package com.lightweightai.kernel.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemorySearchResult - 记忆搜索结果")
class MemorySearchResultTest {

    @Nested
    @DisplayName("工厂方法")
    class FactoryMethods {

        @Test
        @DisplayName("of() 创建默认 EPHEMERAL 类型结果")
        void ofCreatesEphemeralByDefault() {
            MemorySearchResult result = MemorySearchResult.of("some content", 0.85);

            assertEquals("some content", result.getContent());
            assertEquals(0.85, result.getScore(), 0.001);
            assertNull(result.getSource());
            assertEquals(MemorySearchResult.MemoryType.EPHEMERAL, result.getType());
        }

        @Test
        @DisplayName("ephemeral() 创建 EPHEMERAL 类型结果")
        void ephemeralFactory() {
            MemorySearchResult result = MemorySearchResult.ephemeral("content", "daily-log.txt", 0.72);

            assertEquals("content", result.getContent());
            assertEquals("daily-log.txt", result.getSource());
            assertEquals(0.72, result.getScore(), 0.001);
            assertEquals(MemorySearchResult.MemoryType.EPHEMERAL, result.getType());
        }

        @Test
        @DisplayName("durable() 创建 DURABLE 类型结果")
        void durableFactory() {
            MemorySearchResult result = MemorySearchResult.durable("important fact", "knowledge-base", 0.95);

            assertEquals("important fact", result.getContent());
            assertEquals("knowledge-base", result.getSource());
            assertEquals(0.95, result.getScore(), 0.001);
            assertEquals(MemorySearchResult.MemoryType.DURABLE, result.getType());
        }
    }

    @Nested
    @DisplayName("getSnippet 截断")
    class SnippetTests {

        @Test
        @DisplayName("短内容不截断")
        void shortContentNotTruncated() {
            MemorySearchResult result = MemorySearchResult.of("short", 0.5);
            assertEquals("short", result.getSnippet(100));
        }

        @Test
        @DisplayName("超过 maxLength 的内容被截断并添加省略号")
        void longContentTruncated() {
            String longContent = "a".repeat(100);
            MemorySearchResult result = MemorySearchResult.of(longContent, 0.5);

            String snippet = result.getSnippet(20);
            assertEquals(23, snippet.length()); // 20 chars + "..."
            assertTrue(snippet.endsWith("..."));
            assertEquals("a".repeat(20) + "...", snippet);
        }

        @Test
        @DisplayName("恰好等于 maxLength 不截断")
        void exactLengthNotTruncated() {
            String content = "a".repeat(50);
            MemorySearchResult result = MemorySearchResult.of(content, 0.5);

            String snippet = result.getSnippet(50);
            assertEquals(content, snippet);
            assertFalse(snippet.contains("..."));
        }
    }

    @Nested
    @DisplayName("空值校验")
    class NullChecks {

        @Test
        @DisplayName("null content 抛出 NullPointerException")
        void nullContentThrows() {
            assertThrows(NullPointerException.class,
                () -> new MemorySearchResult(null, "source", 0.5, MemorySearchResult.MemoryType.EPHEMERAL));
        }

        @Test
        @DisplayName("null type 默认为 EPHEMERAL")
        void nullTypeDefaultsToEphemeral() {
            MemorySearchResult result = new MemorySearchResult("content", "source", 0.5, null);
            assertEquals(MemorySearchResult.MemoryType.EPHEMERAL, result.getType());
        }
    }

    @Nested
    @DisplayName("MemoryType 枚举")
    class MemoryTypeTests {

        @Test
        @DisplayName("包含所有预期的枚举值")
        void allEnumValues() {
            MemorySearchResult.MemoryType[] values = MemorySearchResult.MemoryType.values();
            assertEquals(3, values.length);
            assertNotNull(MemorySearchResult.MemoryType.valueOf("EPHEMERAL"));
            assertNotNull(MemorySearchResult.MemoryType.valueOf("DURABLE"));
            assertNotNull(MemorySearchResult.MemoryType.valueOf("SESSION"));
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("toString 包含 score、type 和内容摘要")
        void toStringFormat() {
            MemorySearchResult result = MemorySearchResult.durable("test content", "src", 0.123);
            String str = result.toString();

            assertTrue(str.contains("0.123"));
            assertTrue(str.contains("DURABLE"));
            assertTrue(str.contains("test content"));
        }

        @Test
        @DisplayName("toString 中长内容被截断到50字符")
        void toStringTruncatesLongContent() {
            String longContent = "x".repeat(100);
            MemorySearchResult result = MemorySearchResult.of(longContent, 0.5);
            String str = result.toString();

            assertTrue(str.contains("..."));
        }
    }
}
