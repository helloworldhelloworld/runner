package com.lightweightai.web.gateway;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * 统一聊天响应 - 客户端无关
 *
 * 支持：Web / iOS / Android / HarmonyOS / 小程序
 */
public class UnifiedChatResponse {

    /**
     * 请求 ID（用于追踪）
     */
    @JsonProperty("request_id")
    private String requestId;

    /**
     * 会话 ID
     */
    @JsonProperty("session_id")
    private String sessionId;

    /**
     * 响应类型
     */
    private ResponseType type;

    /**
     * 响应内容
     */
    private String content;

    /**
     * 是否出错
     */
    private boolean error;

    /**
     * 错误信息
     */
    @JsonProperty("error_message")
    private String errorMessage;

    /**
     * 延迟（毫秒）
     */
    @JsonProperty("latency_ms")
    private long latencyMs;

    /**
     * 应用的技能
     */
    @JsonProperty("skills_applied")
    private List<String> skillsApplied;

    /**
     * 元数据
     */
    private Map<String, Object> metadata;

    /**
     * 工具调用信息（仅 TOOL_* 类型有值）
     */
    @JsonProperty("tool_event")
    private ToolEvent toolEvent;

    // ==================== 响应类型 ====================

    public enum ResponseType {
        FULL,             // 完整响应
        DELTA,            // 流式增量
        COMPLETE,         // 流式完成
        TOOL_CALL_START,  // 工具调用开始
        TOOL_PROGRESS,    // 工具执行进度
        TOOL_LOG,         // 工具执行日志
        TOOL_RESULT,      // 工具执行结果
        TOOL_ERROR,       // 工具执行错误
        ERROR             // 错误
    }

    /**
     * 工具事件详情
     */
    public static class ToolEvent {
        @JsonProperty("tool_name")
        private String toolName;

        @JsonProperty("tool_call_id")
        private String toolCallId;

        private Double progress;
        private Double total;
        private String message;
        private String content;

        @JsonProperty("is_error")
        private Boolean isError;

        public String getToolName() { return toolName; }
        public void setToolName(String toolName) { this.toolName = toolName; }
        public String getToolCallId() { return toolCallId; }
        public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }
        public Double getProgress() { return progress; }
        public void setProgress(Double progress) { this.progress = progress; }
        public Double getTotal() { return total; }
        public void setTotal(Double total) { this.total = total; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public Boolean getIsError() { return isError; }
        public void setIsError(Boolean isError) { this.isError = isError; }
    }

    // ==================== 工厂方法 ====================

    /**
     * 创建完整响应
     */
    public static UnifiedChatResponse full(String requestId, String sessionId, String content) {
        UnifiedChatResponse response = new UnifiedChatResponse();
        response.requestId = requestId;
        response.sessionId = sessionId;
        response.type = ResponseType.FULL;
        response.content = content;
        return response;
    }

    /**
     * 创建流式增量
     */
    public static UnifiedChatResponse delta(String requestId, String delta) {
        UnifiedChatResponse response = new UnifiedChatResponse();
        response.requestId = requestId;
        response.type = ResponseType.DELTA;
        response.content = delta;
        return response;
    }

    /**
     * 创建流式完成
     */
    public static UnifiedChatResponse complete(String requestId, String sessionId, String fullContent) {
        UnifiedChatResponse response = new UnifiedChatResponse();
        response.requestId = requestId;
        response.sessionId = sessionId;
        response.type = ResponseType.COMPLETE;
        response.content = fullContent;
        return response;
    }

    /**
     * 创建错误响应
     */
    public static UnifiedChatResponse error(String requestId, String errorMessage) {
        UnifiedChatResponse response = new UnifiedChatResponse();
        response.requestId = requestId;
        response.type = ResponseType.ERROR;
        response.error = true;
        response.errorMessage = errorMessage;
        return response;
    }

    /**
     * 创建工具调用开始事件
     */
    public static UnifiedChatResponse toolCallStart(String requestId, String toolName, String toolCallId) {
        UnifiedChatResponse response = new UnifiedChatResponse();
        response.requestId = requestId;
        response.type = ResponseType.TOOL_CALL_START;
        ToolEvent te = new ToolEvent();
        te.setToolName(toolName);
        te.setToolCallId(toolCallId);
        response.toolEvent = te;
        return response;
    }

    /**
     * 创建工具进度事件
     */
    public static UnifiedChatResponse toolProgress(String requestId, String toolName,
                                                     double progress, double total, String message) {
        UnifiedChatResponse response = new UnifiedChatResponse();
        response.requestId = requestId;
        response.type = ResponseType.TOOL_PROGRESS;
        ToolEvent te = new ToolEvent();
        te.setToolName(toolName);
        te.setProgress(progress);
        te.setTotal(total);
        te.setMessage(message);
        response.toolEvent = te;
        return response;
    }

    /**
     * 创建工具日志事件
     */
    public static UnifiedChatResponse toolLog(String requestId, String toolName, String message) {
        UnifiedChatResponse response = new UnifiedChatResponse();
        response.requestId = requestId;
        response.type = ResponseType.TOOL_LOG;
        ToolEvent te = new ToolEvent();
        te.setToolName(toolName);
        te.setMessage(message);
        response.toolEvent = te;
        return response;
    }

    /**
     * 创建工具结果事件
     */
    public static UnifiedChatResponse toolResult(String requestId, String toolName,
                                                  String content, boolean isError) {
        UnifiedChatResponse response = new UnifiedChatResponse();
        response.requestId = requestId;
        response.type = ResponseType.TOOL_RESULT;
        ToolEvent te = new ToolEvent();
        te.setToolName(toolName);
        te.setContent(content);
        te.setIsError(isError);
        response.toolEvent = te;
        return response;
    }

    /**
     * 创建工具错误事件
     */
    public static UnifiedChatResponse toolError(String requestId, String toolName, String message) {
        UnifiedChatResponse response = new UnifiedChatResponse();
        response.requestId = requestId;
        response.type = ResponseType.TOOL_ERROR;
        ToolEvent te = new ToolEvent();
        te.setToolName(toolName);
        te.setMessage(message);
        response.toolEvent = te;
        return response;
    }

    // ==================== Getters/Setters ====================

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public ResponseType getType() { return type; }
    public void setType(ResponseType type) { this.type = type; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public boolean isError() { return error; }
    public void setError(boolean error) { this.error = error; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }

    public List<String> getSkillsApplied() { return skillsApplied; }
    public void setSkillsApplied(List<String> skillsApplied) { this.skillsApplied = skillsApplied; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public ToolEvent getToolEvent() { return toolEvent; }
    public void setToolEvent(ToolEvent toolEvent) { this.toolEvent = toolEvent; }

    @Override
    public String toString() {
        return String.format("UnifiedChatResponse{type=%s, requestId='%s'}", type, requestId);
    }
}
