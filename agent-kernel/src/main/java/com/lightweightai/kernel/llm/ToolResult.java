package com.lightweightai.kernel.llm;

import java.util.HashMap;
import java.util.Map;

/**
 * 工具执行结果
 *
 * 统一的工具结果表示，同时适用于 LLM tool_use 调用和 MCP 协议。
 * toolUseId 由调用方（AgentLoop / ToolExecutor）在 LLM 上下文中设置，
 * 工具实现本身无需关心。
 */
public class ToolResult {

    private final String toolUseId;
    private final String content;
    private final boolean isError;
    private final Map<String, Object> structuredContent;

    private ToolResult(String toolUseId, String content, boolean isError) {
        this(toolUseId, content, isError, null);
    }

    private ToolResult(String toolUseId, String content, boolean isError,
                       Map<String, Object> structuredContent) {
        if (content == null) {
            throw new IllegalArgumentException("Content cannot be null");
        }

        this.toolUseId = toolUseId;
        this.content = content;
        this.isError = isError;
        this.structuredContent = structuredContent;
    }

    // ==================== 推荐 API（工具实现使用）====================

    /**
     * 创建成功结果（不绑定 toolUseId）
     *
     * @param content 结果内容
     * @return ToolResult 实例
     */
    public static ToolResult success(String content) {
        return new ToolResult(null, content, false);
    }

    /**
     * 创建成功结果（带结构化内容，MCP 2025-06-18 structuredContent）
     *
     * @param content           文本内容
     * @param structuredContent 结构化 JSON 对象（可为 null）
     * @return ToolResult 实例
     */
    public static ToolResult success(String content, Map<String, Object> structuredContent) {
        return new ToolResult(null, content, false, structuredContent);
    }

    /**
     * 创建错误结果（不绑定 toolUseId）
     *
     * @param errorMessage 错误信息
     * @return ToolResult 实例
     */
    public static ToolResult error(String errorMessage) {
        return new ToolResult(null, errorMessage, true);
    }

    /**
     * 创建错误结果（从异常，不绑定 toolUseId）
     *
     * @param exception 异常
     * @return ToolResult 实例
     */
    public static ToolResult error(Throwable exception) {
        String errorMessage = exception.getMessage() != null
            ? exception.getMessage()
            : exception.getClass().getSimpleName();
        return new ToolResult(null, errorMessage, true);
    }

    // ==================== 兼容 API（带 toolUseId）====================

    /**
     * 创建成功结果（带 toolUseId，用于 LLM 上下文）
     *
     * @param toolUseId LLM tool_use 的 ID
     * @param content   结果内容
     * @return ToolResult 实例
     */
    public static ToolResult success(String toolUseId, String content) {
        return new ToolResult(toolUseId, content, false);
    }

    /**
     * 创建成功结果（带 toolUseId，从对象）
     *
     * @param toolUseId LLM tool_use 的 ID
     * @param result    结果对象
     * @return ToolResult 实例
     */
    public static ToolResult success(String toolUseId, Object result) {
        String content = result != null ? result.toString() : "null";
        return new ToolResult(toolUseId, content, false);
    }

    /**
     * 创建错误结果（带 toolUseId）
     *
     * @param toolUseId    LLM tool_use 的 ID
     * @param errorMessage 错误信息
     * @return ToolResult 实例
     */
    public static ToolResult error(String toolUseId, String errorMessage) {
        return new ToolResult(toolUseId, errorMessage, true);
    }

    /**
     * 创建错误结果（带 toolUseId，从异常）
     *
     * @param toolUseId LLM tool_use 的 ID
     * @param exception 异常
     * @return ToolResult 实例
     */
    public static ToolResult error(String toolUseId, Throwable exception) {
        String errorMessage = exception.getMessage() != null
            ? exception.getMessage()
            : exception.getClass().getSimpleName();
        return new ToolResult(toolUseId, errorMessage, true);
    }

    // ==================== 绑定 toolUseId ====================

    /**
     * 返回一个带有指定 toolUseId 的新 ToolResult
     *
     * 用于 AgentLoop / ToolExecutor 在调用 tool.execute() 后，
     * 将 LLM 返回的 tool_use id 绑定到结果上。
     *
     * @param toolUseId LLM tool_use 的 ID
     * @return 新的 ToolResult 实例
     */
    public ToolResult withToolUseId(String toolUseId) {
        return new ToolResult(toolUseId, this.content, this.isError, this.structuredContent);
    }

    // ==================== Getters ====================

    /**
     * 获取 tool_use ID（可能为 null，如果未绑定）
     */
    public String getToolUseId() {
        return toolUseId;
    }

    /**
     * 获取结果内容
     */
    public String getContent() {
        return content;
    }

    /**
     * 是否为错误结果
     */
    public boolean isError() {
        return isError;
    }

    /**
     * 获取结构化内容（MCP 2025-06-18 structuredContent）
     *
     * @return 结构化 JSON 对象，可能为 null
     */
    public Map<String, Object> getStructuredContent() {
        return structuredContent;
    }

    /**
     * 是否包含结构化内容
     */
    public boolean hasStructuredContent() {
        return structuredContent != null && !structuredContent.isEmpty();
    }

    /**
     * 转换为 Claude API 格式
     *
     * @return Map 格式的 API 表示
     * @throws IllegalStateException 如果 toolUseId 未设置
     */
    public Map<String, Object> toApiFormat() {
        if (toolUseId == null || toolUseId.trim().isEmpty()) {
            throw new IllegalStateException(
                "toolUseId is required for API format. Use withToolUseId() to bind it.");
        }

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
