package com.lightweightai.tools.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebTools - 网络搜索工具测试
 */
@DisplayName("WebTools - 网络搜索工具")
class WebToolsTest {

    @Nested
    @DisplayName("Mock 模式")
    class MockModeTests {

        @Test
        @DisplayName("默认构造使用 mock 模式")
        void shouldUseMockModeByDefault() {
            WebTools tools = new WebTools();
            String result = tools.webSearch("AI news", 3);

            assertNotNull(result);
            assertTrue(result.contains("Mock search"));
            assertTrue(result.contains("AI news"));
        }

        @Test
        @DisplayName("null apiKey 使用 mock 模式")
        void shouldUseMockWithNullApiKey() {
            WebTools tools = new WebTools(null, 5);
            String result = tools.webSearch("test query", 2);

            assertTrue(result.contains("Mock search"));
            assertTrue(result.contains("test query"));
        }

        @Test
        @DisplayName("空 apiKey 使用 mock 模式")
        void shouldUseMockWithBlankApiKey() {
            WebTools tools = new WebTools("  ", 3);
            String result = tools.webSearch("hello", 1);

            assertTrue(result.contains("Mock search"));
        }

        @Test
        @DisplayName("mock 结果限制显示条数")
        void shouldLimitMockResults() {
            WebTools tools = new WebTools();
            String result = tools.webSearch("query", 1);

            assertTrue(result.contains("1 results"));
        }

        @Test
        @DisplayName("maxResults 为 0 使用默认值")
        void shouldUseDefaultMaxResultsWhenZero() {
            WebTools tools = new WebTools(null, 3);
            String result = tools.webSearch("query", 0);

            assertTrue(result.contains("2 results"));
        }

        @Test
        @DisplayName("maxResults 为负数使用默认值")
        void shouldUseDefaultMaxResultsWhenNegative() {
            WebTools tools = new WebTools(null, 3);
            String result = tools.webSearch("query", -1);

            assertNotNull(result);
            assertTrue(result.contains("Mock search"));
        }
    }
}
