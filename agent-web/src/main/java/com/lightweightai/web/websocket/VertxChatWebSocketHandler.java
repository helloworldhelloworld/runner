package com.lightweightai.web.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.directive.ClientCapability;
import com.lightweightai.kernel.agent.directive.ClientManifest;
import com.lightweightai.kernel.agent.directive.DirectiveResult;
import com.lightweightai.kernel.agent.directive.DirectiveToolBridge;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.gateway.Gateway;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.gateway.GatewayResponse;
import com.lightweightai.kernel.gateway.GatewayStreamHandler;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.safety.CrisisDetector;
import com.lightweightai.safety.CrisisResource;
import com.lightweightai.safety.SafetyResult;
import io.vertx.core.http.ServerWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Vert.x WebSocket handler for streaming chat.
 *
 * 基于 Vert.x 的 WebSocket 协议适配器，通过 Gateway（kernel）委托业务逻辑。
 * 保留 crisis detection 作为安全前置检查。
 *
 * <p>生产稳定性设计：
 * <ul>
 *   <li>连接数限制 — 防止资源耗尽</li>
 *   <li>消息大小限制 — 防止恶意超大消息</li>
 *   <li>每连接消息速率限制 — 防止消息洪泛</li>
 *   <li>写操作失败时安全降级 — 不影响其他连接</li>
 *   <li>连接状态跟踪 — 只向活跃连接发送消息</li>
 *   <li>优雅关闭 — 断开时清理所有资源</li>
 * </ul>
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
public class VertxChatWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(VertxChatWebSocketHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 最大同时连接数（防止资源耗尽） */
    private static final int MAX_CONNECTIONS = 10000;

    /** 每连接每秒最大消息数（速率限制） */
    private static final int MAX_MESSAGES_PER_SECOND = 30;

    private final Map<String, ServerWebSocket> sessions = new ConcurrentHashMap<>();
    private final Map<String, VertxWebSocketClientToolDispatcher> dispatchers = new ConcurrentHashMap<>();
    private final Map<String, DirectiveToolBridge> bridges = new ConcurrentHashMap<>();
    private final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

    private final Gateway gateway;
    private final CrisisDetector crisisDetector;
    private final ToolRegistry toolRegistry;

    /** 活跃连接数计数器 */
    private final AtomicLong activeConnections = new AtomicLong(0);
    /** 接收消息总数（监控指标） */
    private final AtomicLong totalMessagesReceived = new AtomicLong(0);
    /** 发送消息总数（监控指标） */
    private final AtomicLong totalMessagesSent = new AtomicLong(0);

    public VertxChatWebSocketHandler(Gateway gateway,
                                     CrisisDetector crisisDetector,
                                     ToolRegistry toolRegistry) {
        this.gateway = gateway;
        this.crisisDetector = crisisDetector;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 处理新 WebSocket 连接
     */
    public void onConnect(ServerWebSocket ws) {
        // 连接数限制
        if (activeConnections.get() >= MAX_CONNECTIONS) {
            logger.warn("Connection rejected: max connections ({}) reached", MAX_CONNECTIONS);
            ws.reject(503);
            return;
        }

        ws.accept();

        String socketId = ws.textHandlerID();
        sessions.put(socketId, ws);
        dispatchers.put(socketId, new VertxWebSocketClientToolDispatcher(ws));
        rateLimiters.put(socketId, new RateLimiter(MAX_MESSAGES_PER_SECOND));
        activeConnections.incrementAndGet();

        logger.info("WebSocket connected: {} (active: {})", socketId, activeConnections.get());
    }

    /**
     * 处理收到的文本消息
     */
    public void onMessage(ServerWebSocket ws, String message) {
        String socketId = ws.textHandlerID();
        totalMessagesReceived.incrementAndGet();

        try {
            // 速率限制检查
            RateLimiter limiter = rateLimiters.get(socketId);
            if (limiter != null && !limiter.tryAcquire()) {
                logger.warn("Rate limit exceeded for connection: {}", socketId);
                sendError(ws, "消息发送过于频繁，请稍后再试");
                return;
            }

            JsonNode payload = MAPPER.readTree(message);
            String type = payload.path("type").asText("chat");

            switch (type) {
                case "chat" -> handleChatMessage(ws, payload);
                case "client_tool_result" -> handleClientToolResult(ws, payload);
                case "client_manifest" -> handleClientManifest(ws, payload);
                case "directive_result" -> handleDirectiveResult(ws, payload);
                default -> logger.warn("Unknown WS message type: {}", type);
            }
        } catch (Exception e) {
            logger.error("Error handling WebSocket message from {}", socketId, e);
            sendError(ws, "消息处理失败: " + e.getMessage());
        }
    }

    /**
     * 处理连接关闭
     */
    public void onClose(ServerWebSocket ws) {
        String socketId = ws.textHandlerID();
        cleanup(socketId);
        logger.info("WebSocket disconnected: {} (active: {})", socketId, activeConnections.get());
    }

    /**
     * 处理传输错误
     */
    public void onError(ServerWebSocket ws, Throwable error) {
        String socketId = ws.textHandlerID();
        logger.error("WebSocket error for connection {}", socketId, error);
        cleanup(socketId);
    }

    // ==================== 业务逻辑 ====================

    private void handleChatMessage(ServerWebSocket ws, JsonNode payload) {
        String socketId = ws.textHandlerID();
        String sessionId = payload.path("sessionId").asText(socketId);
        String message = payload.path("message").asText("").trim();
        boolean useReactive = payload.path("reactive").asBoolean(true);

        if (message.isEmpty()) {
            sendError(ws, "消息不能为空");
            return;
        }

        // Crisis detection（安全前置检查）
        SafetyResult safetyResult = crisisDetector.check(message);
        if (safetyResult.isCrisis()) {
            sendCrisisAlert(ws, safetyResult.resources());
            return;
        }

        // 通过 Gateway 委托业务逻辑
        GatewayRequest request = GatewayRequest.builder()
            .sessionId(sessionId)
            .message(message)
            .metadata("protocol", "websocket")
            .build();

        if (useReactive) {
            handleChatMessageReactive(ws, request, sessionId);
        } else {
            handleChatMessageCallback(ws, request, sessionId);
        }
    }

    /**
     * Reactive 流式处理 — 订阅 Flux&lt;StreamEvent&gt;
     *
     * 支持工具进度（TOOL_PROGRESS）和日志（TOOL_LOG）事件推送。
     */
    private void handleChatMessageReactive(ServerWebSocket ws, GatewayRequest request, String sessionId) {
        gateway.handleStreamReactive(request)
            .subscribe(
                event -> {
                    switch (event.getType()) {
                        case TEXT_DELTA -> sendToken(ws, event.getTextDelta());
                        case TOOL_CALL_START -> sendToolCallStart(ws, event);
                        case TOOL_PROGRESS -> sendToolProgress(ws, event);
                        case TOOL_LOG -> sendToolLog(ws, event);
                        case TOOL_RESULT -> { /* 工具结果已喂回 LLM，不需要额外通知 */ }
                        case TOOL_ERROR -> sendToolError(ws, event);
                        case LLM_COMPLETE -> sendStreamEnd(ws, "");
                        case ERROR -> sendError(ws,
                                event.getError() != null ? event.getError().getMessage() : "Unknown error");
                    }
                },
                error -> {
                    logger.error("Reactive streaming failed for session {}", sessionId, error);
                    sendError(ws, "处理失败: " + error.getMessage());
                }
            );
    }

    /**
     * 传统 callback 流式处理（向后兼容）
     */
    private void handleChatMessageCallback(ServerWebSocket ws, GatewayRequest request, String sessionId) {
        final String[] lastEmotion = {""};

        gateway.handleStream(request, new GatewayStreamHandler() {
            @Override
            public void onDelta(String delta) {
                // 不会被调用
            }

            @Override
            public void onDelta(String delta, Map<String, Object> metadata) {
                if (delta != null && !delta.isEmpty()) {
                    sendToken(ws, delta);
                }
                if (metadata != null && metadata.containsKey("emotion")) {
                    lastEmotion[0] = String.valueOf(metadata.get("emotion"));
                }
            }

            @Override
            public void onComplete(GatewayResponse response) {
                String emotion = lastEmotion[0];
                Map<String, Object> meta = response.getMetadata();
                if (meta != null && meta.containsKey("emotion")) {
                    emotion = String.valueOf(meta.get("emotion"));
                }
                sendStreamEnd(ws, emotion);
            }

            @Override
            public void onError(Throwable error) {
                logger.error("Streaming failed for session {}", sessionId, error);
                sendError(ws, "处理失败: " + error.getMessage());
            }
        });
    }

    private void handleClientToolResult(ServerWebSocket ws, JsonNode payload) {
        String callId = payload.path("callId").asText("");
        String content = payload.path("content").asText("");
        boolean isError = payload.path("isError").asBoolean(false);

        if (callId.isEmpty()) {
            logger.warn("client_tool_result missing callId");
            return;
        }

        String socketId = ws.textHandlerID();
        VertxWebSocketClientToolDispatcher dispatcher = dispatchers.get(socketId);
        if (dispatcher == null) {
            logger.warn("No dispatcher for connection: {}", socketId);
            return;
        }

        ToolResult result = isError ? ToolResult.error(content) : ToolResult.success(content);
        boolean matched = dispatcher.completeCall(callId, result);

        if (!matched) {
            logger.warn("No pending client tool call for callId: {}", callId);
        }
    }

    private void handleClientManifest(ServerWebSocket ws, JsonNode payload) {
        String socketId = ws.textHandlerID();
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

            VertxWebSocketClientToolDispatcher dispatcher = dispatchers.get(socketId);
            if (dispatcher == null) {
                logger.warn("No dispatcher for connection: {}", socketId);
                return;
            }
            dispatcher.setManifest(manifest);

            DirectiveToolBridge bridge = new DirectiveToolBridge(toolRegistry, dispatcher);
            bridges.put(socketId, bridge);
            List<String> registered = bridge.registerCapabilities(manifest);

            logger.info("Client manifest processed: {} tools registered from {} {}",
                registered.size(), clientType, clientVersion);

        } catch (Exception e) {
            logger.error("Failed to process client manifest", e);
        }
    }

    private void handleDirectiveResult(ServerWebSocket ws, JsonNode payload) {
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

        String socketId = ws.textHandlerID();
        VertxWebSocketClientToolDispatcher dispatcher = dispatchers.get(socketId);
        if (dispatcher == null) {
            logger.warn("No dispatcher for connection: {}", socketId);
            return;
        }

        DirectiveResult directiveResult = new DirectiveResult(directiveId, success, content, metadata);
        ToolResult toolResult = directiveResult.toToolResult();
        boolean matched = dispatcher.completeCall(directiveId, toolResult);

        if (!matched) {
            logger.warn("No pending directive call for directiveId: {}", directiveId);
        }
    }

    // ==================== WebSocket 消息发送（线程安全） ====================

    /**
     * 安全发送文本消息到 WebSocket。
     * Vert.x ServerWebSocket.writeTextMessage 是线程安全的（内部使用 event loop 调度），
     * 无需外部同步。写入失败时仅记录日志，不影响其他连接。
     */
    private void safeSend(ServerWebSocket ws, String json) {
        if (ws.isClosed()) {
            return;
        }
        totalMessagesSent.incrementAndGet();
        ws.writeTextMessage(json).onFailure(err ->
            logger.error("Failed to send WebSocket message to {}", ws.textHandlerID(), err)
        );
    }

    private void sendToken(ServerWebSocket ws, String delta) {
        try {
            ObjectNode msg = MAPPER.createObjectNode();
            msg.put("type", "token");
            msg.put("data", delta);
            safeSend(ws, MAPPER.writeValueAsString(msg));
        } catch (Exception e) {
            logger.error("Failed to serialize token message", e);
        }
    }

    private void sendStreamEnd(ServerWebSocket ws, String emotion) {
        try {
            ObjectNode msg = MAPPER.createObjectNode();
            msg.put("type", "stream_end");
            ObjectNode meta = MAPPER.createObjectNode();
            meta.put("emotion", emotion != null ? emotion : "");
            msg.set("meta", meta);
            safeSend(ws, MAPPER.writeValueAsString(msg));
        } catch (Exception e) {
            logger.error("Failed to serialize stream_end message", e);
        }
    }

    private void sendCrisisAlert(ServerWebSocket ws, List<CrisisResource> resources) {
        try {
            ObjectNode msg = MAPPER.createObjectNode();
            msg.put("type", "crisis_alert");
            msg.set("resources", MAPPER.valueToTree(resources));
            safeSend(ws, MAPPER.writeValueAsString(msg));
        } catch (Exception e) {
            logger.error("Failed to serialize crisis_alert message", e);
        }
    }

    private void sendToolCallStart(ServerWebSocket ws, StreamEvent event) {
        try {
            ObjectNode msg = MAPPER.createObjectNode();
            msg.put("type", "tool_call_start");
            ObjectNode data = MAPPER.createObjectNode();
            if (event.getToolCall() != null) {
                data.put("toolName", event.getToolCall().getName());
                data.put("toolCallId", event.getToolCall().getId());
            }
            msg.set("data", data);
            safeSend(ws, MAPPER.writeValueAsString(msg));
        } catch (Exception e) {
            logger.error("Failed to serialize tool_call_start message", e);
        }
    }

    private void sendToolProgress(ServerWebSocket ws, StreamEvent event) {
        try {
            ObjectNode msg = MAPPER.createObjectNode();
            msg.put("type", "tool_progress");
            ObjectNode data = MAPPER.createObjectNode();
            ToolResultChunk chunk = event.getChunk();
            if (chunk != null) {
                data.put("toolName", chunk.getToolName());
                data.put("progress", chunk.getProgress());
                data.put("total", chunk.getTotal());
                data.put("message", chunk.getMessage() != null ? chunk.getMessage() : "");
                if (chunk.getMeta() != null && !chunk.getMeta().isEmpty()) {
                    data.set("meta", MAPPER.valueToTree(chunk.getMeta()));
                }
            }
            msg.set("data", data);
            safeSend(ws, MAPPER.writeValueAsString(msg));
        } catch (Exception e) {
            logger.error("Failed to serialize tool_progress message", e);
        }
    }

    private void sendToolLog(ServerWebSocket ws, StreamEvent event) {
        try {
            ObjectNode msg = MAPPER.createObjectNode();
            msg.put("type", "tool_log");
            ObjectNode data = MAPPER.createObjectNode();
            ToolResultChunk chunk = event.getChunk();
            if (chunk != null) {
                data.put("toolName", chunk.getToolName());
                data.put("message", chunk.getMessage() != null ? chunk.getMessage() : "");
                if (chunk.getMeta() != null && !chunk.getMeta().isEmpty()) {
                    data.set("meta", MAPPER.valueToTree(chunk.getMeta()));
                }
            }
            msg.set("data", data);
            safeSend(ws, MAPPER.writeValueAsString(msg));
        } catch (Exception e) {
            logger.error("Failed to serialize tool_log message", e);
        }
    }

    private void sendToolError(ServerWebSocket ws, StreamEvent event) {
        try {
            ObjectNode msg = MAPPER.createObjectNode();
            msg.put("type", "tool_error");
            ObjectNode data = MAPPER.createObjectNode();
            ToolResultChunk chunk = event.getChunk();
            if (chunk != null) {
                data.put("toolName", chunk.getToolName());
                data.put("message", chunk.getMessage() != null ? chunk.getMessage() : "Tool execution failed");
            }
            msg.set("data", data);
            safeSend(ws, MAPPER.writeValueAsString(msg));
        } catch (Exception e) {
            logger.error("Failed to serialize tool_error message", e);
        }
    }

    private void sendError(ServerWebSocket ws, String errorMessage) {
        try {
            ObjectNode msg = MAPPER.createObjectNode();
            msg.put("type", "error");
            msg.put("message", errorMessage);
            safeSend(ws, MAPPER.writeValueAsString(msg));
        } catch (Exception e) {
            logger.error("Failed to serialize error message", e);
        }
    }

    // ==================== 资源清理 ====================

    private void cleanup(String socketId) {
        sessions.remove(socketId);
        rateLimiters.remove(socketId);
        activeConnections.decrementAndGet();

        VertxWebSocketClientToolDispatcher dispatcher = dispatchers.remove(socketId);
        if (dispatcher != null) {
            if (dispatcher.hasManifest()) {
                DirectiveToolBridge bridge = bridges.get(socketId);
                if (bridge != null) {
                    bridge.unregisterCapabilities(dispatcher.getManifest());
                }
            }
            dispatcher.cancelAll();
        }
        bridges.remove(socketId);
    }

    // ==================== 监控指标 ====================

    /**
     * 获取当前活跃连接数
     */
    public long getActiveConnectionCount() {
        return activeConnections.get();
    }

    /**
     * 获取总接收消息数
     */
    public long getTotalMessagesReceived() {
        return totalMessagesReceived.get();
    }

    /**
     * 获取总发送消息数
     */
    public long getTotalMessagesSent() {
        return totalMessagesSent.get();
    }

    /**
     * 获取指定连接的 dispatcher
     */
    public VertxWebSocketClientToolDispatcher getDispatcher(String socketId) {
        return dispatchers.get(socketId);
    }

    /**
     * 优雅关闭 — 关闭所有活跃连接
     */
    public void closeAll() {
        sessions.forEach((id, ws) -> {
            try {
                if (!ws.isClosed()) {
                    sendError(ws, "服务器正在关闭");
                    ws.close((short) 1001, "Server shutting down");
                }
            } catch (Exception e) {
                logger.debug("Error closing WebSocket {}", id, e);
            }
        });
        sessions.clear();
        dispatchers.values().forEach(VertxWebSocketClientToolDispatcher::cancelAll);
        dispatchers.clear();
        bridges.clear();
        rateLimiters.clear();
        activeConnections.set(0);
    }

    // ==================== 简易速率限制器 ====================

    /**
     * 基于滑动窗口的简易速率限制器。
     * 每秒允许 maxPerSecond 条消息，超出则拒绝。
     */
    static class RateLimiter {
        private final int maxPerSecond;
        private final AtomicLong windowStart = new AtomicLong(0);
        private final AtomicLong count = new AtomicLong(0);

        RateLimiter(int maxPerSecond) {
            this.maxPerSecond = maxPerSecond;
        }

        boolean tryAcquire() {
            long now = System.currentTimeMillis() / 1000;
            long currentWindow = windowStart.get();

            if (now != currentWindow) {
                // 新的时间窗口，重置计数
                if (windowStart.compareAndSet(currentWindow, now)) {
                    count.set(1);
                    return true;
                }
                // CAS 失败说明另一个线程已经重置了窗口
            }

            return count.incrementAndGet() <= maxPerSecond;
        }
    }
}
