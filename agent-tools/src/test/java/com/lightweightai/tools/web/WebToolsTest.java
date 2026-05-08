package com.lightweightai.tools.web;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.annotation.AnnotatedToolScanner;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link WebTools} — web search tool (mock mode).
 *
 * Covers: mock search result format, tool discovery via annotations.
 */
@DisplayName("WebTools - 网络搜索工具")
class WebToolsTest {

    @Test
    @DisplayName("webSearch — mock 模式返回模拟结果")
    void webSearch_mockMode_returnsMockResults() {
        WebTools tools = new WebTools();
        String result = tools.webSearch("test query", 3);

        assertNotNull(result);
        assertTrue(result.contains("test query"));
        assertTrue(result.contains("Mock search"));
    }

    @Test
    @DisplayName("webSearch — maxResults <= 0 时使用默认值")
    void webSearch_zeroMaxResults_usesDefault() {
        WebTools tools = new WebTools();
        String result = tools.webSearch("query", 0);

        assertNotNull(result);
        assertTrue(result.contains("query"));
    }

    @Test
    @DisplayName("通过 AnnotatedToolScanner 发现为 Tool")
    void discoveredAsAnnotatedTool() {
        List<Tool> tools = AnnotatedToolScanner.scan(new WebTools());

        assertEquals(1, tools.size());
        Tool webSearch = tools.get(0);
        assertEquals("web_search", webSearch.getName());
        assertTrue(webSearch.getDescription().contains("Search"));
    }

    @Test
    @DisplayName("发现的 Tool 可以执行")
    void discoveredTool_canExecute() {
        List<Tool> tools = AnnotatedToolScanner.scan(new WebTools());
        Tool webSearch = tools.get(0);

        var result = webSearch.execute(Map.of("query", "hello", "maxResults", 2));

        assertNotNull(result);
        assertFalse(result.isError());
        assertTrue(result.getContent().contains("hello"));
    }
}
