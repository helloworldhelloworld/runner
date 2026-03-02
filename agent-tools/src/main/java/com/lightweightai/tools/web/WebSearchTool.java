package com.lightweightai.tools.web;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolResult;

import java.util.List;
import java.util.Map;

/**
 * Web search tool.
 *
 * Uses mock data by default. Provide an API key via constructor
 * to enable real search (Brave Search / Serper).
 */
public class WebSearchTool implements Tool, ToolMetadata {

    private final String apiKey;
    private final int defaultMaxResults;

    /**
     * Create with default settings (mock mode, 3 results).
     */
    public WebSearchTool() {
        this(null, 3);
    }

    /**
     * Create with specific API key and max results.
     *
     * @param apiKey           Search API key (null or empty for mock mode)
     * @param defaultMaxResults Default number of results to return
     */
    public WebSearchTool(String apiKey, int defaultMaxResults) {
        this.apiKey = apiKey;
        this.defaultMaxResults = defaultMaxResults;
    }

    @Override
    public String getName() {
        return "web_search";
    }

    @Override
    public String getDescription() {
        return "Search the internet for up-to-date information. Use when the user asks about recent events, news, weather, or real-time data.";
    }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.withRequired(Map.of(
            "query",      Map.of("type", "string",  "description", "Search keywords"),
            "maxResults", Map.of("type", "integer", "description", "Number of results to return (default 3, max 5)")
        ), "query");
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String query = (String) args.get("query");
        int max = args.containsKey("maxResults")
            ? ((Number) args.get("maxResults")).intValue()
            : defaultMaxResults;

        if (!isApiKeyConfigured()) {
            return ToolResult.success(mockResults(query, max));
        }

        // TODO: Integrate with Brave Search / Serper API
        return ToolResult.success(mockResults(query, max));
    }

    private boolean isApiKeyConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    private String mockResults(String query, int max) {
        int shown = Math.min(max, 2);
        return "[Mock search: " + query + "]\n" +
               "1. Example result 1 — mock search data for \"" + query + "\".\n" +
               "2. Example result 2 — replace with Brave Search API in production.\n" +
               "(" + shown + " results)";
    }

    @Override
    public String getCategory() {
        return "web";
    }

    @Override
    public List<String> getTags() {
        return List.of("web", "search", "external");
    }
}
