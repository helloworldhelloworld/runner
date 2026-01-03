package com.lightweightai.kernel.llm;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the result of executing a tool.
 * This is sent back to Claude after executing the tool.
 */
public class ToolResult {

    private final String toolUseId;
    private final String content;
    private final boolean isError;

    private ToolResult(String toolUseId, String content, boolean isError) {
        if (toolUseId == null || toolUseId.trim().isEmpty()) {
            throw new IllegalArgumentException("Tool use ID cannot be null or empty");
        }
        if (content == null) {
            throw new IllegalArgumentException("Content cannot be null");
        }

        this.toolUseId = toolUseId;
        this.content = content;
        this.isError = isError;
    }

    /**
     * Create a successful tool result
     *
     * @param toolUseId The ID of the tool use this is responding to
     * @param content   The result content (typically JSON string)
     * @return ToolResult instance
     */
    public static ToolResult success(String toolUseId, String content) {
        return new ToolResult(toolUseId, content, false);
    }

    /**
     * Create a successful tool result from an object (will be serialized to JSON)
     *
     * @param toolUseId The ID of the tool use this is responding to
     * @param result    The result object
     * @return ToolResult instance
     */
    public static ToolResult success(String toolUseId, Object result) {
        // TODO: Implement JSON serialization
        String content = result != null ? result.toString() : "null";
        return new ToolResult(toolUseId, content, false);
    }

    /**
     * Create an error tool result
     *
     * @param toolUseId    The ID of the tool use this is responding to
     * @param errorMessage The error message
     * @return ToolResult instance
     */
    public static ToolResult error(String toolUseId, String errorMessage) {
        return new ToolResult(toolUseId, errorMessage, true);
    }

    /**
     * Create an error tool result from an exception
     *
     * @param toolUseId The ID of the tool use this is responding to
     * @param exception The exception that occurred
     * @return ToolResult instance
     */
    public static ToolResult error(String toolUseId, Throwable exception) {
        String errorMessage = exception.getMessage() != null
            ? exception.getMessage()
            : exception.getClass().getSimpleName();
        return new ToolResult(toolUseId, errorMessage, true);
    }

    /**
     * Get the tool use ID this result is for
     */
    public String getToolUseId() {
        return toolUseId;
    }

    /**
     * Get the result content
     */
    public String getContent() {
        return content;
    }

    /**
     * Check if this is an error result
     */
    public boolean isError() {
        return isError;
    }

    /**
     * Convert to Claude API format for sending back in the conversation
     *
     * @return Map representation for Claude API
     */
    public Map<String, Object> toApiFormat() {
        Map<String, Object> result = new HashMap<>();
        result.put("type", "tool_result");
        result.put("tool_use_id", toolUseId);
        result.put("content", content);

        if (isError) {
            result.put("is_error", true);
        }

        return result;
    }

    @Override
    public String toString() {
        return "ToolResult{" +
            "toolUseId='" + toolUseId + '\'' +
            ", content='" + content + '\'' +
            ", isError=" + isError +
            '}';
    }
}
