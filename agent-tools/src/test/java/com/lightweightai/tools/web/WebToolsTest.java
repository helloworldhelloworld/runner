package com.lightweightai.tools.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WebTools — 网络搜索工具")
class WebToolsTest {

    @Nested
    @DisplayName("默认构造 (mock 模式)")
    class DefaultConstructor {

        @Test
        @DisplayName("默认构造函数创建 mock 模式实例")
        void defaultConstructorCreatesMockMode() {
            WebTools tools = new WebTools();
            assertNotNull(tools);
        }

        @Test
        @DisplayName("webSearch 返回 mock 结果")
        void webSearchReturnsMockResults() {
            WebTools tools = new WebTools();
            String result = tools.webSearch("AI news", 3);

            assertNotNull(result);
            assertTrue(result.contains("Mock search"));
            assertTrue(result.contains("AI news"));
        }

        @Test
        @DisplayName("webSearch 结果包含搜索关键词")
        void webSearchContainsQuery() {
            WebTools tools = new WebTools();
            String result = tools.webSearch("quantum computing", 2);

            assertTrue(result.contains("quantum computing"));
        }

        @Test
        @DisplayName("webSearch maxResults=0 使用默认 defaultMaxResults")
        void webSearchZeroMaxResultsUsesDefault() {
            WebTools tools = new WebTools();
            String result = tools.webSearch("test", 0);

            assertNotNull(result);
            assertTrue(result.contains("Mock search"));
        }

        @Test
        @DisplayName("webSearch 负数 maxResults 使用默认 defaultMaxResults")
        void webSearchNegativeMaxResultsUsesDefault() {
            WebTools tools = new WebTools();
            String result = tools.webSearch("test", -1);

            assertNotNull(result);
            assertTrue(result.contains("Mock search"));
        }
    }

    @Nested
    @DisplayName("自定义构造")
    class CustomConstructor {

        @Test
        @DisplayName("空 apiKey 仍然使用 mock 模式")
        void emptyApiKeyUsesMockMode() {
            WebTools tools = new WebTools("", 5);
            String result = tools.webSearch("test", 3);

            assertTrue(result.contains("Mock search"));
        }

        @Test
        @DisplayName("空白 apiKey 仍然使用 mock 模式")
        void blankApiKeyUsesMockMode() {
            WebTools tools = new WebTools("   ", 5);
            String result = tools.webSearch("test", 3);

            assertTrue(result.contains("Mock search"));
        }

        @Test
        @DisplayName("null apiKey 使用 mock 模式")
        void nullApiKeyUsesMockMode() {
            WebTools tools = new WebTools(null, 5);
            String result = tools.webSearch("test", 3);

            assertTrue(result.contains("Mock search"));
        }

        @Test
        @DisplayName("自定义 defaultMaxResults 在 maxResults=0 时使用")
        void customDefaultMaxResultsApplied() {
            WebTools tools = new WebTools(null, 1);
            String result = tools.webSearch("test", 0);

            assertNotNull(result);
            assertTrue(result.contains("1 results"));
        }
    }

    @Nested
    @DisplayName("Mock 结果格式")
    class MockResultFormat {

        @Test
        @DisplayName("结果包含编号列表")
        void resultContainsNumberedList() {
            WebTools tools = new WebTools();
            String result = tools.webSearch("weather", 3);

            assertTrue(result.contains("1."));
            assertTrue(result.contains("2."));
        }

        @Test
        @DisplayName("结果包含结果数量")
        void resultContainsResultCount() {
            WebTools tools = new WebTools();
            String result = tools.webSearch("test", 2);

            assertTrue(result.contains("results)"));
        }
    }
}
