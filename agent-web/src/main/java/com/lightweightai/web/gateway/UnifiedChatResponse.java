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

    // ==================== 响应类型 ====================

    public enum ResponseType {
        FULL,       // 完整响应
        DELTA,      // 流式增量
        COMPLETE,   // 流式完成
        ERROR       // 错误
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

    // ==================== Getters/Setters ====================

    public String getRequestId() {
        return requestId;
    }
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getSessionId() {
        return sessionId;
    }
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public ResponseType getType() {
        return type;
    }
    public void setType(ResponseType type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }
    public void setContent(String content) {
        this.content = content;
    }

    public boolean isError() {
        return error;
    }
    public void setError(boolean error) {
        this.error = error;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public long getLatencyMs() {
        return latencyMs;
    }
    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public List<String> getSkillsApplied() {
        return skillsApplied;
    }
    public void setSkillsApplied(List<String> skillsApplied) {
        this.skillsApplied = skillsApplied;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    @Override
    public String toString() {
        return String.format("UnifiedChatResponse{type=%s, requestId='%s'}", type, requestId);
    }
}
