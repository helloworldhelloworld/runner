package com.lightweightai.kernel.gateway;

import com.lightweightai.kernel.agent.AgentResponse;

import java.util.*;

/**
 * Gateway 响应
 *
 * 协议无关的统一响应格式。
 */
public class GatewayResponse {

    private final String requestId;
    private final String sessionId;
    private final String text;
    private final boolean error;
    private final String errorMessage;
    private final long timestamp;
    private final long latencyMs;
    private final Map<String, Object> metadata;

    private GatewayResponse(Builder builder) {
        this.requestId = builder.requestId;
        this.sessionId = builder.sessionId;
        this.text = builder.text;
        this.error = builder.error;
        this.errorMessage = builder.errorMessage;
        this.timestamp = System.currentTimeMillis();
        this.latencyMs = builder.latencyMs;
        this.metadata = new HashMap<>(builder.metadata);
    }

    public String getRequestId() { return requestId; }
    public String getSessionId() { return sessionId; }
    public String getText() { return text; }
    public boolean isError() { return error; }
    public String getErrorMessage() { return errorMessage; }
    public long getTimestamp() { return timestamp; }
    public long getLatencyMs() { return latencyMs; }
    public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }

    /**
     * 从 AgentResponse 创建
     */
    public static GatewayResponse fromAgentResponse(
            AgentResponse agentResponse,
            String requestId,
            String sessionId,
            long latencyMs) {

        return builder()
            .requestId(requestId)
            .sessionId(sessionId)
            .text(agentResponse.getText())
            .latencyMs(latencyMs)
            .metadata("stopReason", agentResponse.getStopReason())
            .metadata("toolCallCount", agentResponse.getToolCalls().size())
            .build();
    }

    /**
     * 创建错误响应
     */
    public static GatewayResponse error(String requestId, String sessionId, String errorMessage) {
        return builder()
            .requestId(requestId)
            .sessionId(sessionId)
            .error(true)
            .errorMessage(errorMessage)
            .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String requestId;
        private String sessionId;
        private String text = "";
        private boolean error = false;
        private String errorMessage;
        private long latencyMs = 0;
        private Map<String, Object> metadata = new HashMap<>();

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder error(boolean error) {
            this.error = error;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder latencyMs(long latencyMs) {
            this.latencyMs = latencyMs;
            return this;
        }

        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public GatewayResponse build() {
            return new GatewayResponse(this);
        }
    }

    @Override
    public String toString() {
        if (error) {
            return String.format("GatewayResponse{error=true, message='%s'}", errorMessage);
        }
        return String.format("GatewayResponse{requestId='%s', text='%s', latency=%dms}",
            requestId,
            text.length() > 50 ? text.substring(0, 50) + "..." : text,
            latencyMs);
    }
}
