package com.lightweightai.web.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lightweightai.safety.CrisisDetector;
import com.lightweightai.safety.CrisisResource;
import com.lightweightai.safety.SafetyResult;
import com.lightweightai.web.service.SoulComfortChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.lightweightai.kernel.llm.ToolResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Raw WebSocket handler for streaming chat.
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
    private final SoulComfortChatService chatService;
    private final CrisisDetector crisisDetector;

    public ChatWebSocketHandler(SoulComfortChatService chatService,
                                CrisisDetector crisisDetector) {
        this.chatService = chatService;
        this.crisisDetector = crisisDetector;
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
            dispatcher.cancelAll();
        }
        logger.info("WebSocket disconnected: {} ({})", session.getId(), status);
    }

    /**
     * 获取指定 session 的客户端工具调度器
     *
     * @param sessionId WebSocket session ID
     * @return 调度器实例，不存在时返回 null
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

        // Crisis detection before LLM
        SafetyResult safetyResult = crisisDetector.check(message);
        if (safetyResult.isCrisis()) {
            sendCrisisAlert(session, safetyResult.resources());
            return;
        }

        // Stream via SoulComfortChatService
        chatService.chat(message, sessionId, chunk -> {
            if (chunk.getDelta() != null && !chunk.getDelta().isEmpty()) {
                sendToken(session, chunk.getDelta());
            }
        }).thenAccept(fullResponse -> {
            sendStreamEnd(session, "温柔");
        }).exceptionally(e -> {
            logger.error("Streaming failed for session {}", sessionId, e);
            sendError(session, "处理失败: " + e.getMessage());
            return null;
        });
    }

    /**
     * 处理客户端回传的工具执行结果
     *
     * 客户端消息格式:
     *   { "type": "client_tool_result", "callId": "...", "content": "...", "isError": false }
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
            dispatcher.cancelAll();
        }
    }
}
