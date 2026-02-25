package com.lightweightai.web.tools;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 网络搜索工具 — Mock 模式
 *
 * 当 app.web-search.api-key 为空时使用 Mock 数据（默认）。
 * 后续接入 Brave / Serper API 时替换 execute() 中的 TODO 分支。
 */
@Component
public class WebSearchTool implements Tool {

    @Value("${app.web-search.api-key:}")
    private String apiKey;

    @Value("${app.web-search.max-results:3}")
    private int defaultMaxResults;

    @Override
    public String getName() {
        return "web_search";
    }

    @Override
    public String getDescription() {
        return "搜索互联网获取最新信息。当用户询问最新事件、新闻、天气、实时数据时使用。";
    }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.withRequired(Map.of(
            "query",      Map.of("type", "string",  "description", "搜索关键词"),
            "maxResults", Map.of("type", "integer", "description", "返回条数，默认3，最多5")
        ), "query");
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String query = (String) args.get("query");
        int max = args.containsKey("maxResults")
            ? ((Number) args.get("maxResults")).intValue()
            : defaultMaxResults;

        System.out.println("[WebSearchTool] query=" + query + ", max=" + max
            + (isApiKeyConfigured() ? " (real API)" : " (mock)"));

        if (!isApiKeyConfigured()) {
            return ToolResult.success("web_search", mockResults(query, max));
        }

        // TODO: 接入 Brave Search / Serper — apiKey 已就位
        return ToolResult.success("web_search", mockResults(query, max));
    }

    private boolean isApiKeyConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    private String mockResults(String query, int max) {
        int shown = Math.min(max, 2);
        return "[Mock 搜索: " + query + "]\n" +
               "1. 示例结果一 — 这是「" + query + "」的模拟搜索数据。\n" +
               "2. 示例结果二 — 实际部署时替换为 Brave Search API。\n" +
               "(共 " + shown + " 条)";
    }
}
