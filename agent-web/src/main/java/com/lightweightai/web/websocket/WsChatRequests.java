package com.lightweightai.web.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.lightweightai.kernel.gateway.GatewayRequest;

/**
 * 入站 WebSocket chat payload → {@link GatewayRequest.Builder} 的共享构造。
 *
 * Vert.x 与 Spring 两个 ChatWebSocketHandler 都从同一份 payload 形状
 * （{@code {type, sessionId, message, model?, agentId?}}）建请求；此处收口，避免两处漂移。
 * 调用方在返回的 builder 上追加各自特有项（如 Vert.x 的 deviceContext）后再 build。
 *
 * <p>{@code agentId} 注入 metadata，供 {@code MetadataAgentRouter} 路由到对应 persona
 * （如 minion）。缺失则不注入，router 自然 fallback 到 default。
 */
final class WsChatRequests {

    private WsChatRequests() {
    }

    /**
     * 从 payload 构造基础 builder：sessionId（缺省用 {@code defaultSessionId}）、message、
     * 固定 {@code protocol=websocket}，以及可选 {@code model} / {@code agentId}。
     */
    static GatewayRequest.Builder baseBuilder(JsonNode payload, String defaultSessionId) {
        String sessionId = payload.path("sessionId").asText(defaultSessionId);
        String message = payload.path("message").asText("").trim();

        GatewayRequest.Builder builder = GatewayRequest.builder()
                .sessionId(sessionId)
                .message(message)
                .metadata("protocol", "websocket");

        String model = payload.path("model").asText(null);
        if (model != null && !model.isEmpty()) {
            builder.metadata("model", model);
        }
        String agentId = payload.path("agentId").asText(null);
        if (agentId != null && !agentId.isEmpty()) {
            builder.metadata("agentId", agentId);
        }
        return builder;
    }
}
