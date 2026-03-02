package com.lightweightai.tools.web;

import com.lightweightai.kernel.agent.annotation.ToolFunction;
import com.lightweightai.kernel.agent.annotation.ToolParam;

/**
 * 网络搜索工具集（注解方式）
 *
 * 默认 mock 模式。通过构造函数注入 API key 启用真实搜索。
 */
public class WebTools {

    private final String apiKey;
    private final int defaultMaxResults;

    public WebTools() {
        this(null, 3);
    }

    public WebTools(String apiKey, int defaultMaxResults) {
        this.apiKey = apiKey;
        this.defaultMaxResults = defaultMaxResults;
    }

    @ToolFunction(
        name = "web_search",
        description = "Search the internet for up-to-date information. Use when the user asks about recent events, news, weather, or real-time data.",
        category = "web",
        tags = {"web", "search", "external"}
    )
    public String webSearch(
        @ToolParam(name = "query", description = "Search keywords", required = true) String query,
        @ToolParam(name = "maxResults", description = "Number of results to return (default 3, max 5)", type = "integer") int maxResults
    ) {
        int max = maxResults > 0 ? maxResults : defaultMaxResults;

        if (apiKey != null && !apiKey.isBlank()) {
            // TODO: Integrate with Brave Search / Serper API
        }

        return mockResults(query, max);
    }

    private String mockResults(String query, int max) {
        int shown = Math.min(max, 2);
        return "[Mock search: " + query + "]\n" +
               "1. Example result 1 — mock search data for \"" + query + "\".\n" +
               "2. Example result 2 — replace with Brave Search API in production.\n" +
               "(" + shown + " results)";
    }
}
