package com.lightweightai.kernel.llm;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
    /**
     * 工具产出的图像帧（视觉回灌链，ADR-010）。永不为 null；默认空列表。
     * 由 {@code look}/{@code capture_image} 之类工具附上，{@code ToolCallingLoop}
     * 会把它回灌成下一轮多模态消息里的 image block。
     */
    private final List<ImageContent> images;

    private ToolResult(String toolUseId, String content, boolean isError) {
        this(toolUseId, content, isError, null);
    }

    private ToolResult(String toolUseId, String content, boolean isError,
                       Map<String, Object> structuredContent) {
        this(toolUseId, content, isError, structuredContent, null);
    }

    private ToolResult(String toolUseId, String content, boolean isError,
                       Map<String, Object> structuredContent, List<ImageContent> images) {
        if (content == null) {
            throw new IllegalArgumentException("Content cannot be null");
        }

        this.toolUseId = toolUseId;
        this.content = content;
        this.isError = isError;
        this.structuredContent = structuredContent;
        this.images = images == null || images.isEmpty()
            ? Collections.emptyList()
            : List.copyOf(images);
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

    /**
     * 创建带图像帧的成功结果（视觉回灌链，ADR-010）。
     *
     * 供 {@code look}/{@code capture_image} 之类工具返回抓到的帧：
     * {@code content} 是给 LLM 的文本说明（如 "captured 1 frame"），
     * {@code images} 是帧本身（URL 或 base64 字节）。
     * 回灌时 {@code ToolCallingLoop} 会把这些帧放进下一轮多模态消息的 image block。
     *
     * @param content 文本说明（不可为 null）
     * @param images  图像帧列表（null/空均可）
     * @return ToolResult 实例
     */
    public static ToolResult withImages(String content, List<ImageContent> images) {
        return new ToolResult(null, content, false, null, images);
    }

    /**
     * 便捷：单帧成功结果。
     *
     * @param content 文本说明
     * @param image   单张图像帧
     * @return ToolResult 实例
     */
    public static ToolResult image(String content, ImageContent image) {
        return new ToolResult(null, content, false, null,
            image == null ? null : List.of(image));
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
        return new ToolResult(toolUseId, this.content, this.isError, this.structuredContent, this.images);
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
     * 获取工具产出的图像帧（视觉回灌链，ADR-010）。永不返回 null。
     *
     * @return 不可变图像列表（无图像时为空列表）
     */
    public List<ImageContent> getImages() {
        return images;
    }

    /**
     * 是否携带图像帧。
     */
    public boolean hasImages() {
        return !images.isEmpty();
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
