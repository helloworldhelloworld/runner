package com.lightweightai.kernel.memory.tools;

import com.lightweightai.kernel.memory.file.FileMemoryManager;
import com.lightweightai.kernel.memory.model.SearchOptions;
import com.lightweightai.kernel.memory.model.SearchResult;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Memory search tool for AI agents.
 * Searches through stored memories using hybrid (keyword + semantic) search.
 *
 * Tool Definition:
 * - name: memory_search
 * - description: Search through stored memories and past conversations
 * - parameters:
 *   - query (required): The search query
 *   - top_k (optional): Number of results to return (default: 5)
 *   - vector_weight (optional): Weight for semantic search 0-1 (default: 0.7)
 */
public class MemorySearchTool {

    public static final String TOOL_NAME = "memory_search";
    public static final String TOOL_DESCRIPTION =
        "Search through stored memories and past conversations to find relevant information. " +
        "Use this when you need to recall past interactions, user preferences, or stored knowledge.";

    private final FileMemoryManager memoryManager;

    public MemorySearchTool(FileMemoryManager memoryManager) {
        this.memoryManager = memoryManager;
    }

    /**
     * Execute the memory search.
     */
    public MemorySearchResult execute(Map<String, Object> parameters) {
        String query = (String) parameters.get("query");
        if (query == null || query.isBlank()) {
            return new MemorySearchResult(false, "Query parameter is required", List.of());
        }

        int topK = getIntParam(parameters, "top_k", 5);
        float vectorWeight = getFloatParam(parameters, "vector_weight", 0.7f);

        SearchOptions options = SearchOptions.builder()
            .topK(topK)
            .vectorWeight(vectorWeight)
            .build();

        List<SearchResult> results = memoryManager.search(query, options);

        List<MemoryMatch> matches = results.stream()
            .map(r -> new MemoryMatch(
                r.getChunk().getContent(),
                r.getChunk().getSourceFile(),
                r.getScore(),
                r.getSnippet()
            ))
            .collect(Collectors.toList());

        return new MemorySearchResult(true, null, matches);
    }

    /**
     * Get JSON schema for tool definition.
     */
    public static Map<String, Object> getToolSchema() {
        return Map.of(
            "name", TOOL_NAME,
            "description", TOOL_DESCRIPTION,
            "input_schema", Map.of(
                "type", "object",
                "properties", Map.of(
                    "query", Map.of(
                        "type", "string",
                        "description", "The search query to find relevant memories"
                    ),
                    "top_k", Map.of(
                        "type", "integer",
                        "description", "Number of results to return (default: 5)"
                    ),
                    "vector_weight", Map.of(
                        "type", "number",
                        "description", "Weight for semantic search 0-1 (default: 0.7)"
                    )
                ),
                "required", List.of("query")
            )
        );
    }

    private int getIntParam(Map<String, Object> params, String key, int defaultValue) {
        Object value = params.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    private float getFloatParam(Map<String, Object> params, String key, float defaultValue) {
        Object value = params.get(key);
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return defaultValue;
    }

    // Result types

    public record MemorySearchResult(
        boolean success,
        String error,
        List<MemoryMatch> matches
    ) {
        public String toText() {
            if (!success) {
                return "Error: " + error;
            }
            if (matches.isEmpty()) {
                return "No matching memories found.";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(matches.size()).append(" relevant memories:\n\n");
            for (int i = 0; i < matches.size(); i++) {
                MemoryMatch match = matches.get(i);
                sb.append(i + 1).append(". [").append(match.source()).append("] (score: ")
                  .append(String.format("%.3f", match.score())).append(")\n");
                sb.append(match.snippet()).append("\n\n");
            }
            return sb.toString();
        }
    }

    public record MemoryMatch(
        String content,
        String source,
        float score,
        String snippet
    ) {}
}
