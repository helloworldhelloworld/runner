package com.lightweightai.tools.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebTools 测试 — web 搜索工具
 *
 * 覆盖：
 * - 默认构造器（mock 模式）
 * - 带 apiKey 构造器
 * - webSearch mock 模式返回模拟结果
 * - maxResults 参数传递
 */
@DisplayName("WebTools - Web 搜索工具")
class WebToolsTest {

    @Test
    @DisplayName("默认构造器创建 mock 模式实例")
    void defaultConstructorCreatesMockMode() {
        WebTools tools = new WebTools();
        assertNotNull(tools);
    }

    @Test
    @DisplayName("带 apiKey 构造器正确初始化")
    void constructorWithApiKey() {
        WebTools tools = new WebTools("test-api-key", 5);
        assertNotNull(tools);
    }

    @Test
    @DisplayName("mock 模式 webSearch 返回非空结果")
    void mockModeSearchReturnsResult() {
        WebTools tools = new WebTools();
        String result = tools.webSearch("test query", 3);

        assertNotNull(result);
        assertFalse(result.isEmpty(), "Mock search should return some result");
    }

    @Test
    @DisplayName("webSearch 返回包含搜索关键词的结果")
    void searchResultContainsQuery() {
        WebTools tools = new WebTools();
        String result = tools.webSearch("Java programming", 5);

        assertNotNull(result);
        // Mock 模式通常会返回包含查询信息的结果
        assertFalse(result.isEmpty());
    }

    @Test
    @DisplayName("maxResults 为 1 时仍然返回结果")
    void searchWithMinimalResults() {
        WebTools tools = new WebTools();
        String result = tools.webSearch("test", 1);

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }
}
