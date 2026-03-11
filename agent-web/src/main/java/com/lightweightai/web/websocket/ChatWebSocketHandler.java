package com.lightweightai.web.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.directive.ClientCapability;
import com.lightweightai.kernel.agent.directive.ClientManifest;
import com.lightweightai.kernel.agent.directive.DirectiveResult;
import com.lightweightai.kernel.agent.directive.DirectiveToolBridge;
import com.lightweightai.kernel.gateway.Gateway;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.gateway.GatewayResponse;
import com.lightweightai.kernel.gateway.GatewayStreamHandler;
import com.lightweightai.safety.CrisisDetector;
import com.lightweightai.safety.CrisisResource;
import com.lightweightai.safety.SafetyResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.lightweightai.kernel.llm.ToolResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Raw WebSocket handler for streaming chat.
 *
 * 纯 WebSocket 协议适配器，通过 Gateway（kernel）委托业务逻辑。
 * 仅保留 crisis detection 作为安全前置检查。
 *
 * Client → Server message format:
 *   { "type": "chat", "sessionId": "...", "userId": "...", "message": "..." }
 *
 * Server → Client message formats:
 *   { "type": "token",        "data": "..." }
 *   { "type": "stream_end",   "meta": { "emotion": "..." } }
 *   { "type": "crisis_alert", "resources": [...] }
 *   { "type": "error",        "message": "..." }
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, WebSocketClientToolDispatcher> dispatchers = new ConcurrentHashMap<>();
    private final Map<String, DirectiveToolBridge> bridges = new ConcurrentHashMap<>();
    private final Gateway gateway;
    private final CrisisDetector crisisDetector;
    private final ToolRegistry toolRegistry;

    public ChatWebSocketHandler(Gateway gateway,
                                CrisisDetector crisisDetector,
                                ToolRegistry toolRegistry) {
        this.gateway = gateway;
        this.crisisDetector = crisisDetector;
        this.toolRegistry = toolRegistry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        dispatchers.put(session.getId(), new WebSocketClientToolDispatcher(session));
        logger.info("WebSocket connected: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        WebSocketClientToolDispatcher dispatcher = dispatchers.remove(session.getId());
        if (dispatcher != null) {
            // Unregister directive tools if manifest was present
            if (dispatcher.hasManifest()) {
                DirectiveToolBridge bridge = bridges.get(session.getId());
                if (bridge != null) {
                    bridge.unregisterCapabilities(dispatcher.getManifest());
                }
            }
            dispatcher.cancelAll();
        }
        bridges.remove(session.getId());
        logger.info("WebSocket disconnected: {} ({})", session.getId(), status);
    }

    /**
     * 获取指定 session 的客户端工具调度器
     */
    public WebSocketClientToolDispatcher getDispatcher(String sessionId) {
        return dispatchers.get(sessionId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode payload = MAPPER.readTree(message.getPayload());
            String type = payload.path("type").asText("chat");

            if ("chat".equals(type)) {
                handleChatMessage(session, payload);
            } else if ("client_tool_result".equals(type)) {
                handleClientToolResult(session, payload);
            } else if ("client_manifest".equals(type)) {
                handleClientManifest(session, payload);
            } else if ("directive_result".equals(type)) {
                handleDirectiveResult(session, payload);
            } else {
                logger.warn("Unknown WS message type: {}", type);
            }
        } catch (Exception e) {
            logger.error("Error handling WebSocket message", e);
            sendError(session, "消息处理失败: " + e.getMessage());
        }
    }

    private void handleChatMessage(WebSocketSession session, JsonNode payload) {
        String sessionId = payload.path("sessionId").asText(session.getId());
        String message = payload.path("message").asText("").trim();

        if (message.isEmpty()) {
            sendError(session, "消息不能为空");
            return;
        }

        // Crisis detection（安全前置检查，保留在协议层）
        SafetyResult safetyResult = crisisDetector.check(message);
        if (safetyResult.isCrisis()) {
            sendCrisisAlert(session, safetyResult.resources());
            return;
        }

        // 通过 Gateway 委托业务逻辑
        GatewayRequest request = GatewayRequest.builder()
            .sessionId(sessionId)
            .message(message)
            .metadata("protocol", "websocket")
            .build();

        // 记录最后的 emotion，用于 stream_end
        final String[] lastEmotion = {""};

        gateway.handleStream(request, new GatewayStreamHandler() {
            @Override
            public void onDelta(String delta) {
                // 不会被调用
            }

            @Override
            public void onDelta(String delta, Map<String, Object> metadata) {
                if (delta != null && !delta.isEmpty()) {
                    sendToken(session, delta);
                }
                // 从 metadata 中读取 emotion
                if (metadata != null && metadata.containsKey("emotion")) {
                    lastEmotion[0] = String.valueOf(metadata.get("emotion"));
                }
            }

            @Override
            public void onComplete(GatewayResponse response) {
                // 从 response metadata 提取 emotion，或用 stream 中最后记录的
                String emotion = lastEmotion[0];
                Map<String, Object> meta = response.getMetadata();
                if (meta != null && meta.containsKey("emotion")) {
                    emotion = String.valueOf(meta.get("emotion"));
                }
                sendStreamEnd(session, emotion);
            }

            @Override
            public void onError(Throwable error) {
                logger.error("Streaming failed for session {}", sessionId, error);
                sendError(session, "处理失败: " + error.getMessage());
            }
        });
    }

    /**
     * 处理客户端回传的工具执行结果
     */
    private void handleClientToolResult(WebSocketSession session, JsonNode payload) {
        String callId = payload.path("callId").asText("");
        String content = payload.path("content").asText("");
        boolean isError = payload.path("isError").asBoolean(false);

        if (callId.isEmpty()) {
            logger.warn("client_tool_result missing callId");
            return;
        }

        WebSocketClientToolDispatcher dispatcher = dispatchers.get(session.getId());
        if (dispatcher == null) {
            logger.warn("No dispatcher for session: {}", session.getId());
            return;
        }

        ToolResult result = isError ? ToolResult.error(content) : ToolResult.success(content);
        boolean matched = dispatcher.completeCall(callId, result);

        if (!matched) {
            logger.warn("No pending client tool call for callId: {}", callId);
        }
    }

    /**
     * 处理端侧上报的能力清单（Directive 协议）
     */
    private void handleClientManifest(WebSocketSession session, JsonNode payload) {
        try {
            String clientType = payload.path("clientType").asText("");
            String clientVersion = payload.path("clientVersion").asText("");
            JsonNode capNode = payload.path("capabilities");

            List<ClientCapability> capabilities = new ArrayList<>();
            if (capNode.isArray()) {
                for (JsonNode cap : capNode) {
                    ClientCapability capability = new ClientCapability();
                    capability.setNamespace(cap.path("namespace").asText(""));
                    capability.setName(cap.path("name").asText(""));
                    capability.setDescription(cap.path("description").asText(""));
                    if (cap.has("input_schema")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> schema = MAPPER.convertValue(
                            cap.get("input_schema"), Map.class);
                        capability.setInputSchema(schema);
                    }
                    capability.setDefaultTimeoutMs(cap.path("default_timeout_ms").asLong(60000));
                    capabilities.add(capability);
                }
            }

            ClientManifest manifest = new ClientManifest(clientType, clientVersion, capabilities);

            // Store manifest on dispatcher (enables Directive protocol for dispatch)
            WebSocketClientToolDispatcher dispatcher = dispatchers.get(session.getId());
            if (dispatcher == null) {
                logger.warn("No dispatcher for session: {}", session.getId());
                return;
            }
            dispatcher.setManifest(manifest);

            // Create bridge and register capabilities as tools
            DirectiveToolBridge bridge = new DirectiveToolBridge(toolRegistry, dispatcher);
            bridges.put(session.getId(), bridge);
            List<String> registered = bridge.registerCapabilities(manifest);

            logger.info("Client manifest processed: {} tools registered from {} {}",
                registered.size(), clientType, clientVersion);

        } catch (Exception e) {
            logger.error("Failed to process client manifest", e);
        }
    }

    /**
     * 处理端侧回传的 Directive 执行结果
     */
    private void handleDirectiveResult(WebSocketSession session, JsonNode payload) {
        String directiveId = payload.path("directiveId").asText("");
        boolean success = payload.path("success").asBoolean(false);
        String content = payload.path("content").asText("");
        Map<String, Object> metadata = null;
        if (payload.has("metadata") && !payload.get("metadata").isNull()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = MAPPER.convertValue(payload.get("metadata"), Map.class);
            metadata = m;
        }

        if (directiveId.isEmpty()) {
            logger.warn("directive_result missing directiveId");
            return;
        }

        WebSocketClientToolDispatcher dispatcher = dispatchers.get(session.getId());
        if (dispatcher == null) {
            logger.warn("No dispatcher for session: {}", session.getId());
            return;
        }

        // Convert DirectiveResult → ToolResult via bridge
        DirectiveResult directiveResult = new DirectiveResult(directiveId, success, content, metadata);
        ToolResult toolResult = directiveResult.toToolResult();
        boolean matched = dispatcher.completeCall(directiveId, toolResult);

        if (!matched) {
            logger.warn("No pending directive call for directiveId: {}", directiveId);
        }
    }

    // ==================== WebSocket 协议序列化（基础设施） ====================

    private void sendToken(WebSocketSession session, String delta) {
        try {
            ObjectNode msg = MAPPER.createObjectNode();
            msg.put("type", "token");
            msg.put("data", delta);
            synchronized (session) {
                session.sendMessage(new TextMessage(MAPPER.writeValueAsString(msg)));
            }
        } catch (Exception e) {
            logger.error("Failed to send token", e);
        }
    }

    private void sendStreamEnd(WebSocketSession session, String emotion) {
        try {
            ObjectNode msg = MAPPER.createObjectNode();
            msg.put("type", "stream_end");
            ObjectNode meta = MAPPER.createObjectNode();
            meta.put("emotion", emotion != null ? emotion : "");
            msg.set("meta", meta);
            synchronized (session) {
                session.sendMessage(new TextMessage(MAPPER.writeValueAsString(msg)));
            }
        } catch (Exception e) {
            logger.error("Failed to send stream_end", e);
        }
    }

    private void sendCrisisAlert(WebSocketSession session, List<CrisisResource> resources) {
        try {
            ObjectNode msg = MAPPER.createObjectNode();
            msg.put("type", "crisis_alert");
            msg.set("resources", MAPPER.valueToTree(resources));
            synchronized (session) {
                session.sendMessage(new TextMessage(MAPPER.writeValueAsString(msg)));
            }
        } catch (Exception e) {
            logger.error("Failed to send crisis_alert", e);
        }
    }

    private void sendError(WebSocketSession session, String errorMessage) {
        try {
            ObjectNode msg = MAPPER.createObjectNode();
            msg.put("type", "error");
            msg.put("message", errorMessage);
            synchronized (session) {
                session.sendMessage(new TextMessage(MAPPER.writeValueAsString(msg)));
            }
        } catch (Exception e) {
            logger.error("Failed to send error message", e);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        logger.error("WebSocket transport error for session {}", session.getId(), exception);
        sessions.remove(session.getId());
        WebSocketClientToolDispatcher dispatcher = dispatchers.remove(session.getId());
        if (dispatcher != null) {
            if (dispatcher.hasManifest()) {
                DirectiveToolBridge bridge = bridges.get(session.getId());
                if (bridge != null) {
                    bridge.unregisterCapabilities(dispatcher.getManifest());
                }
            }
            dispatcher.cancelAll();
        }
        bridges.remove(session.getId());
    }
}
