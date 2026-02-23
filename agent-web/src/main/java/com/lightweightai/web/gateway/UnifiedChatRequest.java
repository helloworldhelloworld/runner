package com.lightweightai.web.gateway;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * 统一聊天请求 - 客户端无关
 *
 * 支持：Web / iOS / Android / HarmonyOS / 小程序
 */
public class UnifiedChatRequest {

    /**
     * 会话 ID（客户端生成或服务端分配）
     */
    @JsonProperty("session_id")
    private String sessionId;

    /**
     * 用户消息
     */
    private String message;

    /**
     * 是否流式响应
     */
    private boolean stream;

    /**
     * 客户端类型（用于适配）
     */
    @JsonProperty("client_type")
    private ClientType clientType;

    /**
     * 模型选择（可选）
     */
    private String model;

    /**
     * 扩展参数
     */
    private Map<String, Object> extra;

    // ==================== 客户端类型 ====================

    public enum ClientType {
        WEB,           // 网页
        IOS,           // iOS App
        ANDROID,       // Android App
        HARMONYOS,     // 鸿蒙 App
        MINIPROGRAM,   // 小程序
        CLI,           // 命令行
        UNKNOWN
    }

    // ==================== Getters/Setters ====================

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public boolean isStream() { return stream; }
    public void setStream(boolean stream) { this.stream = stream; }

    public ClientType getClientType() { return clientType != null ? clientType : ClientType.UNKNOWN; }
    public void setClientType(ClientType clientType) { this.clientType = clientType; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Map<String, Object> getExtra() { return extra; }
    public void setExtra(Map<String, Object> extra) { this.extra = extra; }

    /**
     * 获取扩展参数值
     */
    @SuppressWarnings("unchecked")
    public <T> T getExtra(String key, T defaultValue) {
        if (extra == null || !extra.containsKey(key)) {
            return defaultValue;
        }
        return (T) extra.get(key);
    }

    @Override
    public String toString() {
        return String.format("UnifiedChatRequest{sessionId='%s', client=%s, stream=%s}",
            sessionId, clientType, stream);
    }
}
