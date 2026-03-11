package com.lightweightai.kernel.agent.directive;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lightweightai.kernel.llm.ToolResult;

import java.util.Map;

/**
 * 端→云 Directive 执行结果
 *
 * 端侧执行 Directive 后回传的结果。这是 Directive 协议层进入框架层的唯一桥接点：
 * 通过 {@link #toToolResult()} 转换为框架层的 {@link ToolResult}。
 *
 * <pre>
 * {
 *   "directive_id": "uuid-123",
 *   "success": true,
 *   "content": "https://example.com/photo.jpg",
 *   "metadata": { "device": "Pixel 8", "elapsed_ms": 1200 }
 * }
 * </pre>
 */
public class DirectiveResult {

    @JsonProperty("directive_id")
    private String directiveId;

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("content")
    private String content;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    public DirectiveResult() {
    }

    public DirectiveResult(String directiveId, boolean success, String content,
                           Map<String, Object> metadata) {
        this.directiveId = directiveId;
        this.success = success;
        this.content = content;
        this.metadata = metadata;
    }

    /**
     * 桥接方法：DirectiveResult → ToolResult
     *
     * 这是 Directive 协议层进入框架层的唯一转换点。
     * toolUseId 不在这里设置 — 由 ToolExecutor.withToolUseId() 统一绑定。
     *
     * metadata 如果非空，映射到 ToolResult.structuredContent，
     * 可携带端侧额外信息（设备型号、执行耗时等）。
     */
    public ToolResult toToolResult() {
        if (!success) {
            return ToolResult.error(content != null ? content : "Directive execution failed");
        }
        String resultContent = content != null ? content : "";
        if (metadata != null && !metadata.isEmpty()) {
            return ToolResult.success(resultContent, metadata);
        }
        return ToolResult.success(resultContent);
    }

    // Factory methods

    public static DirectiveResult success(String directiveId, String content) {
        return new DirectiveResult(directiveId, true, content, null);
    }

    public static DirectiveResult success(String directiveId, String content,
                                           Map<String, Object> metadata) {
        return new DirectiveResult(directiveId, true, content, metadata);
    }

    public static DirectiveResult error(String directiveId, String errorMessage) {
        return new DirectiveResult(directiveId, false, errorMessage, null);
    }

    // Getters and Setters

    public String getDirectiveId() {
        return directiveId;
    }

    public void setDirectiveId(String directiveId) {
        this.directiveId = directiveId;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    @Override
    public String toString() {
        return "DirectiveResult{" +
            "directiveId='" + directiveId + '\'' +
            ", success=" + success +
            ", content='" + content + '\'' +
            ", metadata=" + metadata +
            '}';
    }
}
