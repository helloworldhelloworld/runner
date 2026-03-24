package com.lightweightai.kernel.gateway;

import com.lightweightai.kernel.agent.DeviceContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Gateway 请求
 *
 * 协议无关的统一请求格式。
 */
public class GatewayRequest {

    private final String requestId;
    private final String sessionId;
    private final String message;
    private final Map<String, Object> metadata;
    private final long timestamp;

    private GatewayRequest(Builder builder) {
        this.requestId = builder.requestId != null ? builder.requestId : UUID.randomUUID().toString();
        this.sessionId = builder.sessionId != null ? builder.sessionId : "default";
        this.message = Objects.requireNonNull(builder.message, "Message required");
        this.metadata = new HashMap<>(builder.metadata);
        this.timestamp = System.currentTimeMillis();
    }

    public String getRequestId() { return requestId; }
    public String getSessionId() { return sessionId; }
    public String getMessage() { return message; }
    public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
    public long getTimestamp() { return timestamp; }

    /**
     * 获取设备上下文（从 metadata 读取）
     */
    public DeviceContext getDeviceContext() {
        Object ctx = metadata.get("deviceContext");
        return ctx instanceof DeviceContext ? (DeviceContext) ctx : DeviceContext.DEFAULT;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String requestId;
        private String sessionId;
        private String message;
        private Map<String, Object> metadata = new HashMap<>();

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata.putAll(metadata);
            return this;
        }

        public Builder deviceContext(DeviceContext deviceContext) {
            this.metadata.put("deviceContext", deviceContext);
            return this;
        }

        public GatewayRequest build() {
            return new GatewayRequest(this);
        }
    }

    @Override
    public String toString() {
        return String.format("GatewayRequest{requestId='%s', sessionId='%s', message='%s'}",
            requestId, sessionId, message.length() > 50 ? message.substring(0, 50) + "..." : message);
    }
}
