package com.lightweightai.web.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lightweightai.assessment.AssessmentService;
import com.lightweightai.assessment.model.AssessmentResult;
import com.lightweightai.assessment.model.AssessmentSubmission;
import com.lightweightai.assessment.model.ScaleDefinition;
import com.lightweightai.assessment.model.ScaleType;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.gateway.Gateway;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.gateway.SessionManager;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.ResilientLLMProvider;
import com.lightweightai.mcp.McpToolWrapper;
import com.lightweightai.safety.CrisisDetector;
import com.lightweightai.safety.SafetyResult;
import com.lightweightai.user.UserService;
import com.lightweightai.user.model.EmotionRecord;
import com.lightweightai.user.model.SoulUser;
import com.lightweightai.web.config.McpConfig;
import com.lightweightai.web.service.ChatService;
import com.lightweightai.web.service.SoulComfortChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import reactor.core.Disposable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Spring WebSocket handler — 运行在 Tomcat 8080 端口 /ws/chat
 *
 * 直接调用 Gateway 和各 Service，提供与 VertxChatWebSocketHandler 相同的功能。
 */
public class SpringChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(SpringChatWebSocketHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Gateway gateway;
    private final CrisisDetector crisisDetector;
    private final ToolRegistry toolRegistry;
    private final ChatService chatService;
    private final SoulComfortChatService soulComfortChatService;
    private final AssessmentService assessmentService;
    private final UserService userService;
    private final LLMProvider llmProvider;
    private final McpConfig.McpToolRegistrar mcpToolRegistrar;

    private final Map<String, Disposable> activeStreams = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final AtomicLong activeConnections = new AtomicLong(0);
    private final java.util.concurrent.ExecutorService chatExecutor =
        java.util.concurrent.Executors.newCachedThreadPool();

    public SpringChatWebSocketHandler(Gateway gateway,
                                       CrisisDetector crisisDetector,
                                       ToolRegistry toolRegistry,
                                       ChatService chatService,
                                       SoulComfortChatService soulComfortChatService,
                                       AssessmentService assessmentService,
                                       UserService userService,
                                       LLMProvider llmProvider,
                                       McpConfig.McpToolRegistrar mcpToolRegistrar) {
        this.gateway = gateway;
        this.crisisDetector = crisisDetector;
        this.toolRegistry = toolRegistry;
        this.chatService = chatService;
        this.soulComfortChatService = soulComfortChatService;
        this.assessmentService = assessmentService;
        this.userService = userService;
        this.llmProvider = llmProvider;
        this.mcpToolRegistrar = mcpToolRegistrar;
    }

    // Legacy constructor for compatibility
    public SpringChatWebSocketHandler(VertxChatWebSocketHandler ignored) {
        throw new UnsupportedOperationException("Use the full constructor");
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        activeConnections.incrementAndGet();
        logger.info("Spring WS connected: {} (active: {})", session.getId(), activeConnections.get());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        Disposable d = activeStreams.remove(session.getId());
        if (d != null && !d.isDisposed()) d.dispose();
        activeConnections.decrementAndGet();
        logger.info("Spring WS disconnected: {} status={} (active: {})", session.getId(), status, activeConnections.get());
    }

    @Override
    protected void handleTextMessage(WebSocketSession rawSession, TextMessage message) {
        WebSocketSession session = sessions.getOrDefault(rawSession.getId(), rawSession);
        try {
            JsonNode payload = MAPPER.readTree(message.getPayload());
            String type = payload.path("type").asText("chat");
            String requestId = payload.path("requestId").asText(null);

            switch (type) {
                case "chat" -> chatExecutor.submit(() -> {
                    try {
                        handleChat(session, payload);
                    } catch (Exception e) {
                        logger.error("handleChat EXCEPTION", e);
                        safeSend(session, errorJson("处理失败: " + e.getMessage()));
                    }
                });
                case "get_history" -> sendResponse(session, "history", requestId,
                    getSessionManager().map(sm -> sm.getSessionHistory(payload.path("sessionId").asText(""))).orElse(List.of()));
                case "get_summary" -> sendResponse(session, "summary", requestId,
                    getSessionManager().map(sm -> sm.getSessionSummary(payload.path("sessionId").asText(""))).orElse(Map.of()));
                case "clear_session" -> {
                    String sid = payload.path("sessionId").asText("");
                    getSessionManager().ifPresent(sm -> sm.clearSession(sid));
                    sendResponse(session, "session_cleared", requestId, Map.of("status", "cleared", "sessionId", sid));
                }
                case "get_skills" -> sendResponse(session, "skills", requestId, chatService.getAvailableSkills());
                case "get_tools" -> sendResponse(session, "tools", requestId, chatService.getAvailableTools());
                case "health" -> sendResponse(session, "health", requestId, buildHealth());
                case "get_scales" -> sendResponse(session, "scales", requestId, assessmentService.getScales());
                case "get_scale" -> {
                    try {
                        ScaleType st = ScaleType.valueOf(payload.path("scaleType").asText("").toUpperCase());
                        sendResponse(session, "scale", requestId, assessmentService.getScale(st));
                    } catch (IllegalArgumentException e) {
                        sendResponse(session, "error_response", requestId, Map.of("error", "Invalid scale type"));
                    }
                }
                case "submit_assessment" -> {
                    try {
                        String userId = payload.path("userId").asText("");
                        ScaleType st = ScaleType.valueOf(payload.path("scaleType").asText("").toUpperCase());
                        List<Integer> answers = new ArrayList<>();
                        payload.path("answers").forEach(a -> answers.add(a.asInt()));
                        AssessmentResult result = assessmentService.submit(new AssessmentSubmission(userId, st, answers));
                        sendResponse(session, "assessment_result", requestId, result);
                    } catch (Exception e) {
                        sendResponse(session, "error_response", requestId, Map.of("error", e.getMessage()));
                    }
                }
                case "get_assessment_history" -> sendResponse(session, "assessment_history", requestId,
                    assessmentService.getHistory(payload.path("userId").asText("")));
                case "create_user" -> sendResponse(session, "user_created", requestId, userService.createAnonymousUser());
                case "checkin" -> {
                    try {
                        EmotionRecord r = userService.checkin(payload.path("userId").asText(""),
                            payload.path("emotion").asText(""), payload.path("note").asText(null));
                        sendResponse(session, "checkin_result", requestId, r);
                    } catch (Exception e) {
                        sendResponse(session, "error_response", requestId, Map.of("error", e.getMessage()));
                    }
                }
                case "get_emotions" -> sendResponse(session, "emotions", requestId,
                    userService.getEmotions(payload.path("userId").asText(""), payload.path("days").asInt(7)));
                case "get_stats" -> sendResponse(session, "stats", requestId,
                    userService.getStats(payload.path("userId").asText("")));
                case "search_memory" -> {
                    try {
                        sendResponse(session, "memory_results", requestId,
                            soulComfortChatService.searchMemory(payload.path("query").asText(""), payload.path("topK").asInt(10)));
                    } catch (Exception e) {
                        sendResponse(session, "error_response", requestId, Map.of("error", e.getMessage()));
                    }
                }
                case "remember_durable" -> {
                    try {
                        soulComfortChatService.rememberDurable(payload.path("section").asText(""), payload.path("content").asText(""));
                        sendResponse(session, "remember_result", requestId, Map.of("status", "saved"));
                    } catch (Exception e) {
                        sendResponse(session, "error_response", requestId, Map.of("error", e.getMessage()));
                    }
                }
                case "get_tools_detail" -> sendResponse(session, "tools_detail", requestId, buildToolsDetail());
                case "get_tool_definitions" -> {
                    List<Map<String, Object>> defs = toolRegistry.getToolDefinitions();
                    sendResponse(session, "tool_definitions", requestId, Map.of("count", defs.size(), "tools", defs));
                }
                case "get_mcp_servers" -> sendResponse(session, "mcp_servers", requestId, buildMcpServers());
                default -> logger.warn("Unknown WS type: {}", type);
            }
        } catch (Exception e) {
            logger.error("Error handling WS message", e);
            safeSend(session, errorJson("消息处理失败: " + e.getMessage()));
        }
    }

    // ==================== Chat Streaming ====================

    private void handleChat(WebSocketSession session, JsonNode payload) {
        String sessionId = payload.path("sessionId").asText(session.getId());
        String message = payload.path("message").asText("").trim();
        String model = payload.path("model").asText(null);

        if (message.isEmpty()) {
            safeSend(session, errorJson("消息不能为空"));
            return;
        }

        SafetyResult safety = crisisDetector.check(message);
        if (safety.isCrisis()) {
            try {
                ObjectNode msg = MAPPER.createObjectNode();
                msg.put("type", "crisis_alert");
                msg.set("resources", MAPPER.valueToTree(safety.resources()));
                safeSend(session, MAPPER.writeValueAsString(msg));
            } catch (Exception e) { logger.error("Failed to send crisis alert", e); }
            return;
        }

        GatewayRequest.Builder builder = GatewayRequest.builder()
            .sessionId(sessionId).message(message).metadata("protocol", "websocket");
        if (model != null && !model.isEmpty()) builder.metadata("model", model);

        String sid = session.getId();
        Disposable prev = activeStreams.remove(sid);
        if (prev != null && !prev.isDisposed()) prev.dispose();

        logger.info("handleChat: building flux, session={}", sid);
        reactor.core.publisher.Flux<StreamEvent> flux = gateway.handleStreamReactive(builder.build());
        logger.info("handleChat: flux built, subscribing, session={}", sid);
        Disposable sub = flux
            .doOnError(e -> logger.error("handleChat: flux error session={}", sid, e))
            .subscribe(
                event -> {
                    logger.info("handleChat: event type={} session={} thread={}", event.getType(), sid, Thread.currentThread().getName());
                    handleStreamEvent(session, event);
                },
                error -> {
                    logger.error("handleChat: subscribe error session={}", sid, error);
                    safeSend(session, errorJson("处理失败: " + error.getMessage()));
                    activeStreams.remove(sid);
                },
                () -> {
                    try {
                        ObjectNode msg = MAPPER.createObjectNode();
                        msg.put("type", "stream_end");
                        msg.set("meta", MAPPER.createObjectNode().put("emotion", ""));
                        safeSend(session, MAPPER.writeValueAsString(msg));
                    } catch (Exception e) { logger.error("Failed to send stream_end", e); }
                    activeStreams.remove(sid);
                }
            );
        activeStreams.put(sid, sub);
        logger.info("handleChat: subscribed, disposed={}, session={}", sub.isDisposed(), sid);
    }

    private void handleStreamEvent(WebSocketSession session, StreamEvent event) {
        try {
            switch (event.getType()) {
                case TEXT_DELTA -> {
                    ObjectNode msg = MAPPER.createObjectNode();
                    msg.put("type", "token");
                    msg.put("data", event.getTextDelta());
                    safeSend(session, MAPPER.writeValueAsString(msg));
                }
                case TOOL_CALL_START -> {
                    ObjectNode msg = MAPPER.createObjectNode();
                    msg.put("type", "tool_call_start");
                    ObjectNode data = MAPPER.createObjectNode();
                    if (event.getToolCall() != null) {
                        data.put("toolName", event.getToolCall().getName());
                        data.put("toolCallId", event.getToolCall().getId());
                    }
                    msg.set("data", data);
                    safeSend(session, MAPPER.writeValueAsString(msg));
                }
                case TOOL_PROGRESS, TOOL_LOG -> {
                    ObjectNode msg = MAPPER.createObjectNode();
                    msg.put("type", event.getType() == StreamEvent.EventType.TOOL_PROGRESS ? "tool_progress" : "tool_log");
                    ObjectNode data = MAPPER.createObjectNode();
                    ToolResultChunk chunk = event.getChunk();
                    if (chunk != null) {
                        data.put("toolName", chunk.getToolName());
                        data.put("message", chunk.getMessage() != null ? chunk.getMessage() : "");
                    }
                    msg.set("data", data);
                    safeSend(session, MAPPER.writeValueAsString(msg));
                }
                case TOOL_ERROR -> {
                    ObjectNode msg = MAPPER.createObjectNode();
                    msg.put("type", "tool_error");
                    ObjectNode data = MAPPER.createObjectNode();
                    if (event.getChunk() != null) {
                        data.put("toolName", event.getChunk().getToolName());
                        data.put("message", event.getChunk().getMessage() != null ? event.getChunk().getMessage() : "");
                    }
                    msg.set("data", data);
                    safeSend(session, MAPPER.writeValueAsString(msg));
                }
                case POST_PROCESS_DATA -> {
                    ObjectNode msg = MAPPER.createObjectNode();
                    msg.put("type", "post_process");
                    msg.put("category", event.getCategory() != null ? event.getCategory() : "");
                    if (event.getData() != null) msg.set("data", MAPPER.valueToTree(event.getData()));
                    safeSend(session, MAPPER.writeValueAsString(msg));
                }
                case ERROR -> safeSend(session, errorJson(
                    event.getError() != null ? event.getError().getMessage() : "Unknown error"));
                case TRACE -> {
                    ObjectNode msg = MAPPER.createObjectNode();
                    msg.put("type", "trace");
                    ObjectNode data = MAPPER.createObjectNode();
                    data.put("phase", event.getTracePhase());
                    data.put("message", event.getTraceMessage());
                    data.put("timestamp", event.getTraceTimestamp());
                    if (event.getData() != null) data.set("extra", MAPPER.valueToTree(event.getData()));
                    msg.set("data", data);
                    safeSend(session, MAPPER.writeValueAsString(msg));
                }
                default -> {} // TOOL_RESULT, LLM_COMPLETE 不需要额外推送
            }
        } catch (Exception e) {
            logger.error("Failed to handle stream event", e);
        }
    }

    // ==================== Helpers ====================

    private Optional<SessionManager> getSessionManager() {
        return Optional.ofNullable(gateway.getSessionManager());
    }

    private void sendResponse(WebSocketSession session, String type, String requestId, Object data) {
        try {
            ObjectNode msg = MAPPER.createObjectNode();
            msg.put("type", type);
            if (requestId != null) msg.put("requestId", requestId);
            if (data != null) msg.set("data", MAPPER.valueToTree(data));
            safeSend(session, MAPPER.writeValueAsString(msg));
        } catch (Exception e) {
            logger.error("Failed to send response", e);
        }
    }

    private void safeSend(WebSocketSession session, String json) {
        if (!session.isOpen()) return;
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            logger.warn("Failed to send WS message to {}: {}", session.getId(), e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error sending WS message to {}", session.getId(), e);
        }
    }

    private String errorJson(String message) {
        try {
            ObjectNode msg = MAPPER.createObjectNode();
            msg.put("type", "error");
            msg.put("message", message);
            return MAPPER.writeValueAsString(msg);
        } catch (Exception e) { return "{\"type\":\"error\",\"message\":\"serialize failed\"}"; }
    }

    private Map<String, Object> buildHealth() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("skills", chatService.getAvailableSkills().size());
        result.put("tools", chatService.getAvailableTools().size());
        Map<String, Object> llm = new LinkedHashMap<>();
        if (llmProvider instanceof ResilientLLMProvider r) {
            var info = r.getHealthInfo();
            llm.put("status", info.status().name());
            llm.put("provider", info.providerName());
        } else {
            llm.put("provider", llmProvider.getProviderName());
            llm.put("status", "NOT_MONITORED");
        }
        result.put("llm", llm);
        result.put("wsConnections", activeConnections.get());
        return result;
    }

    private Map<String, Object> buildToolsDetail() {
        List<Map<String, Object>> toolList = new ArrayList<>();
        for (Tool tool : toolRegistry.getAll()) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", tool.getName());
            info.put("description", tool.getDescription());
            info.put("enabled", toolRegistry.isEnabled(tool.getName()));
            info.put("source", tool instanceof McpToolWrapper ? "mcp" : "local");
            if (tool instanceof ToolMetadata meta) {
                info.put("category", meta.getCategory());
                info.put("tags", meta.getTags());
                info.put("readOnly", meta.isReadOnly());
                info.put("destructive", meta.isDestructive());
                info.put("clientSide", meta.isClientSide());
            }
            if (tool.getSchema() != null) info.put("input_schema", tool.getSchema().toMap());
            toolList.add(info);
        }
        return Map.of("total", toolList.size(), "enabled", toolRegistry.enabledCount(), "tools", toolList);
    }

    private Map<String, Object> buildMcpServers() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (mcpToolRegistrar == null) {
            result.put("enabled", false);
            result.put("servers", List.of());
            return result;
        }
        result.put("enabled", true);
        result.put("serverCount", mcpToolRegistrar.getServerCount());
        result.put("totalToolCount", mcpToolRegistrar.getTotalToolCount());
        List<Map<String, Object>> servers = new ArrayList<>();
        mcpToolRegistrar.getClients().forEach(client -> {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("name", client.getServerName());
            List<Map<String, Object>> tools = new ArrayList<>();
            client.getDiscoveredTools().forEach(w -> tools.add(Map.of("name", w.getName(), "description", w.getDescription(), "enabled", toolRegistry.isEnabled(w.getName()))));
            s.put("toolCount", tools.size());
            s.put("tools", tools);
            servers.add(s);
        });
        result.put("servers", servers);
        return result;
    }

    public long getActiveConnectionCount() { return activeConnections.get(); }
}
