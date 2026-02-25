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
        logger.info("WebSocket connected: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        logger.info("WebSocket disconnected: {} ({})", session.getId(), status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode payload = MAPPER.readTree(message.getPayload());
            String type = payload.path("type").asText("chat");

            if ("chat".equals(type)) {
                handleChatMessage(session, payload);
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
    }
}
