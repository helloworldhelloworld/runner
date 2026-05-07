package com.lightweightai.tools.web;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.annotation.AnnotatedToolScanner;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WebTools - 网络搜索工具")
class WebToolsTest {

    @Test
    @DisplayName("默认构造 → mock 模式，defaultMaxResults=3")
    void defaultConstructor() {
        WebTools tools = new WebTools();
        List<Tool> scanned = AnnotatedToolScanner.scan(tools);

        assertEquals(1, scanned.size());
        assertEquals("web_search", scanned.get(0).getName());
    }

    @Test
    @DisplayName("web_search mock 返回包含查询词的结果")
    void mockSearchContainsQuery() {
        WebTools tools = new WebTools();
        List<Tool> scanned = AnnotatedToolScanner.scan(tools);
        Tool searchTool = scanned.get(0);

        ToolResult result = searchTool.execute(Map.of("query", "latest news", "maxResults", 3));

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("latest news"));
        assertTrue(result.getContent().contains("Mock search"));
    }

    @Test
    @DisplayName("maxResults=0 → 使用 defaultMaxResults")
    void zeroMaxResultsUsesDefault() {
        WebTools tools = new WebTools();
        List<Tool> scanned = AnnotatedToolScanner.scan(tools);
        Tool searchTool = scanned.get(0);

        ToolResult result = searchTool.execute(Map.of("query", "test", "maxResults", 0));

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("Mock search"));
    }

    @Test
    @DisplayName("自定义 apiKey + maxResults 构造")
    void customConstructor() {
        WebTools tools = new WebTools("fake-api-key", 5);
        List<Tool> scanned = AnnotatedToolScanner.scan(tools);

        assertEquals(1, scanned.size());
    }

    @Test
    @DisplayName("无 apiKey → mock 结果包含替换提示")
    void mockResultSuggestsRealApi() {
        WebTools tools = new WebTools();
        List<Tool> scanned = AnnotatedToolScanner.scan(tools);
        Tool searchTool = scanned.get(0);

        ToolResult result = searchTool.execute(Map.of("query", "weather", "maxResults", 2));

        assertTrue(result.getContent().contains("Brave Search"));
    }

    @Test
    @DisplayName("工具描述包含 search 关键词")
    void descriptionContainsSearch() {
        WebTools tools = new WebTools();
        List<Tool> scanned = AnnotatedToolScanner.scan(tools);

        assertTrue(scanned.get(0).getDescription().toLowerCase().contains("search"));
    }
}
