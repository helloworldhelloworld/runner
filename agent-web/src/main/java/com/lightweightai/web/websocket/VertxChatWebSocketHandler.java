package com.lightweightai.web.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lightweightai.assessment.AssessmentService;
import com.lightweightai.assessment.model.AssessmentResult;
import com.lightweightai.assessment.model.AssessmentSubmission;
import com.lightweightai.assessment.model.ScaleDefinition;
import com.lightweightai.assessment.model.ScaleType;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.directive.ClientCapability;
import com.lightweightai.kernel.agent.directive.ClientManifest;
import com.lightweightai.kernel.agent.directive.DefaultDirectiveManager;
import com.lightweightai.kernel.agent.directive.DirectiveToolBridge;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.gateway.Gateway;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.gateway.GatewayResponse;
import com.lightweightai.kernel.gateway.GatewayStreamHandler;
import com.lightweightai.kernel.gateway.SessionManager;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.ResilientLLMProvider;
import com.lightweightai.safety.CrisisDetector;
import com.lightweightai.safety.CrisisResource;
import com.lightweightai.safety.SafetyResult;
import com.lightweightai.user.UserService;
import com.lightweightai.user.model.EmotionRecord;
import com.lightweightai.user.model.SoulUser;
import com.lightweightai.web.config.McpConfig.McpToolRegistrar;
import com.lightweightai.web.service.ChatService;
import com.lightweightai.web.service.SoulComfortChatService;
import io.vertx.core.http.ServerWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    /** 活跃的 reactive 流订阅，用于 WebSocket 断开时取消流 */
    private final Map<String, Disposable> activeStreams = new ConcurrentHashMap<>();

    private final Gateway gateway;
    private final CrisisDetector crisisDetector;
    private final ToolRegistry toolRegistry;
    private final ChatService chatService;
    private final SoulComfortChatService soulComfortChatService;
    private final AssessmentService assessmentService;
    private final UserService userService;
    private final LLMProvider llmProvider;
    private final McpToolRegistrar mcpToolRegistrar;
    private final ClientToolResultRouter clientToolResultRouter;
    private final ClientToolSessionBinder clientToolSessionBinder;

    /** 活跃连接数计数器 */
    private final AtomicLong activeConnections = new AtomicLong(0);
    /** 接收消息总数（监控指标） */
    private final AtomicLong totalMessagesReceived = new AtomicLong(0);
    /** 发送消息总数（监控指标） */
    private final AtomicLong totalMessagesSent = new AtomicLong(0);

    public VertxChatWebSocketHandler(Gateway gateway,
                                     CrisisDetector crisisDetector,
                                     ToolRegistry toolRegistry,
                                     ChatService chatService,
                                     SoulComfortChatService soulComfortChatService,
                                     AssessmentService assessmentService,
                                     UserService userService,
                                     LLMProvider llmProvider,
                                     McpToolRegistrar mcpToolRegistrar) {
        this.gateway = gateway;
        this.crisisDetector = crisisDetector;
        this.toolRegistry = toolRegistry;
        this.chatService = chatService;
        this.soulComfortChatService = soulComfortChatService;
        this.assessmentService = assessmentService;
        this.userService = userService;
        this.llmProvider = llmProvider;
        this.mcpToolRegistrar = mcpToolRegistrar;
        this.clientToolResultRouter = new ClientToolResultRouter(MAPPER, toolRegistry);
        this.clientToolSessionBinder = new ClientToolSessionBinder(toolRegistry);
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
        VertxWebSocketClientToolDispatcher dispatcher =
            new VertxWebSocketClientToolDispatcher(ws, new DefaultDirectiveManager());
        dispatchers.put(socketId, dispatcher);
        clientToolSessionBinder.bind(socketId, dispatcher);
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

            String requestId = payload.path("requestId").asText(null);

            switch (type) {
                case "chat" -> handleChatMessage(ws, payload);
                case "client_tool_result" -> handleClientToolResult(ws, payload);
                case "client_manifest" -> handleClientManifest(ws, payload);
                case "directive_result" -> handleDirectiveResult(ws, payload);
                // Session management
                case "get_history" -> handleGetHistory(ws, payload, requestId);
                case "get_summary" -> handleGetSummary(ws, payload, requestId);
                case "clear_session" -> handleClearSession(ws, payload, requestId);
                // Skills & Tools
                case "get_skills" -> handleGetSkills(ws, requestId);
                case "get_tools" -> handleGetTools(ws, requestId);
                // Health
                case "health" -> handleHealth(ws, requestId);
                // Assessment
                case "get_scales" -> handleGetScales(ws, requestId);
                case "get_scale" -> handleGetScale(ws, payload, requestId);
                case "submit_assessment" -> handleSubmitAssessment(ws, payload, requestId);
                case "get_assessment_history" -> handleGetAssessmentHistory(ws, payload, requestId);
                // User
                case "create_user" -> handleCreateUser(ws, requestId);
                case "checkin" -> handleCheckin(ws, payload, requestId);
                case "get_emotions" -> handleGetEmotions(ws, payload, requestId);
                case "get_stats" -> handleGetStats(ws, payload, requestId);
                // Memory
                case "search_memory" -> handleSearchMemory(ws, payload, requestId);
                case "remember_durable" -> handleRememberDurable(ws, payload, requestId);
                // MCP Tools detail
                case "get_tools_detail" -> handleGetToolsDetail(ws, requestId);
                case "get_tool_detail" -> handleGetToolDetail(ws, payload, requestId);
                case "get_mcp_servers" -> handleGetMcpServers(ws, requestId);
                case "get_tool_definitions" -> handleGetToolDefinitions(ws, requestId);
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
        String model = payload.path("model").asText(null);
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
        GatewayRequest.Builder builder = GatewayRequest.builder()
            .sessionId(sessionId)
            .message(message)
            .metadata("protocol", "websocket");
        if (model != null && !model.isEmpty()) {
            builder.metadata("model", model);
        }
        GatewayRequest request = builder.build();

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
        String socketId = ws.textHandlerID();
        logger.info("[WS-STREAM] handleChatMessageReactive START session={} socketId={} wsClosed={}",
            sessionId, socketId, ws.isClosed());

        // 取消之前的流（如果有），防止并发流冲突
        Disposable prev = activeStreams.remove(socketId);
        if (prev != null && !prev.isDisposed()) {
            prev.dispose();
        }

        Disposable subscription = gateway.handleStreamReactive(request)
            .doOnSubscribe(s -> logger.info("[WS-STREAM] Flux subscribed for session={}", sessionId))
            .subscribe(
                event -> {
                    logger.info("[WS-STREAM] Event received: type={} session={} wsClosed={}",
                        event.getType(), sessionId, ws.isClosed());
                    switch (event.getType()) {
                        case TEXT_DELTA -> {
                            String delta = event.getTextDelta();
                            logger.info("[WS-STREAM] TEXT_DELTA len={} preview='{}'",
                                delta != null ? delta.length() : 0,
                                delta != null ? delta.substring(0, Math.min(50, delta.length())) : "null");
                            if ("reasoning".equals(event.getMetadata().get("type"))) {
                                sendReasoningToken(ws, delta);
                            } else {
                                sendToken(ws, delta);
                            }
                        }
                        case TOOL_CALL_START -> sendToolCallStart(ws, event);
                        case TOOL_PROGRESS -> sendToolProgress(ws, event);
                        case TOOL_LOG -> sendToolLog(ws, event);
                        case TOOL_RESULT -> { /* 工具结果已喂回 LLM，不需要额外通知 */ }
                        case TOOL_ERROR -> sendToolError(ws, event);
                        case POST_PROCESS_DATA -> sendPostProcessData(ws, event);
                        case TRACE -> sendTrace(ws, event);
                        case LLM_COMPLETE -> {
                            logger.info("[WS-STREAM] LLM_COMPLETE hasToolCalls={} session={}",
                                event.getResponse() != null && event.getResponse().hasToolCalls(), sessionId);
                        }
                        case ERROR -> sendError(ws,
                                event.getError() != null ? event.getError().getMessage() : "Unknown error");
                    }
                },
                error -> {
                    logger.error("[WS-STREAM] Flux ERROR for session={}", sessionId, error);
                    sendError(ws, "处理失败: " + error.getMessage());
                    activeStreams.remove(socketId);
                },
                () -> {
                    logger.info("[WS-STREAM] Flux COMPLETE for session={} wsClosed={}", sessionId, ws.isClosed());
                    sendStreamEnd(ws, "");
                    activeStreams.remove(socketId);
                }
            );
        activeStreams.put(socketId, subscription);
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
        String socketId = ws.textHandlerID();
        VertxWebSocketClientToolDispatcher dispatcher = dispatchers.get(socketId);
        if (dispatcher == null) {
            logger.warn("No dispatcher for connection: {}", socketId);
            return;
        }

        boolean matched = clientToolResultRouter.routeClientToolResult(dispatcher, payload);
        if (!matched) {
            logger.warn("No pending client tool call matched for socket={}", socketId);
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
        String socketId = ws.textHandlerID();
        VertxWebSocketClientToolDispatcher dispatcher = dispatchers.get(socketId);
        if (dispatcher == null) {
            logger.warn("No dispatcher for connection: {}", socketId);
            return;
        }

        boolean matched = clientToolResultRouter.routeDirectiveResult(dispatcher, payload);
        if (!matched) {
            logger.warn("No pending directive call matched for socket={}", socketId);
        }
    }

    // ==================== 请求-响应式消息处理 ====================

    /**
     * 发送带 requestId 的响应消息
     */
    private void sendResponse(ServerWebSocket ws, String responseType, String requestId, Object data) {
        try {
            ObjectNode msg = MAPPER.createObjectNode();
            msg.put("type", responseType);
            if (requestId != null) {
                msg.put("requestId", requestId);
            }
            if (data != null) {
                msg.set("data", MAPPER.valueToTree(data));
            }
            safeSend(ws, MAPPER.writeValueAsString(msg));
        } catch (Exception e) {
            logger.error("Failed to serialize {} response", responseType, e);
            sendError(ws, "响应序列化失败: " + e.getMessage());
        }
    }

    private void handleGetHistory(ServerWebSocket ws, JsonNode payload, String requestId) {
        String sessionId = payload.path("sessionId").asText("");
        SessionManager sm = gateway.getSessionManager();
        List<Map<String, String>> history = (sm != null) ? sm.getSessionHistory(sessionId) : List.of();
        sendResponse(ws, "history", requestId, history);
    }

    private void handleGetSummary(ServerWebSocket ws, JsonNode payload, String requestId) {
        String sessionId = payload.path("sessionId").asText("");
        SessionManager sm = gateway.getSessionManager();
        Map<String, Object> summary = (sm != null)
            ? sm.getSessionSummary(sessionId)
            : Map.of("sessionId", sessionId);
        sendResponse(ws, "summary", requestId, summary);
    }

    private void handleClearSession(ServerWebSocket ws, JsonNode payload, String requestId) {
        String sessionId = payload.path("sessionId").asText("");
        SessionManager sm = gateway.getSessionManager();
        if (sm != null) {
            sm.clearSession(sessionId);
        }
        sendResponse(ws, "session_cleared", requestId, Map.of("status", "cleared", "sessionId", sessionId));
    }

    private void handleGetSkills(ServerWebSocket ws, String requestId) {
        List<Map<String, String>> skills = chatService.getAvailableSkills();
        sendResponse(ws, "skills", requestId, skills);
    }

    private void handleGetTools(ServerWebSocket ws, String requestId) {
        List<Map<String, Object>> tools = chatService.getAvailableTools();
        sendResponse(ws, "tools", requestId, tools);
    }

    private void handleHealth(ServerWebSocket ws, String requestId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("skills", chatService.getAvailableSkills().size());
        result.put("tools", chatService.getAvailableTools().size());

        Map<String, Object> llmHealth = new LinkedHashMap<>();
        if (llmProvider instanceof ResilientLLMProvider resilient) {
            ResilientLLMProvider.HealthInfo info = resilient.getHealthInfo();
            llmHealth.put("status", info.status().name());
            llmHealth.put("provider", info.providerName());
            llmHealth.put("totalRequests", info.totalRequests());
            llmHealth.put("totalFailures", info.totalFailures());
        } else {
            llmHealth.put("provider", llmProvider.getProviderName());
            llmHealth.put("status", "NOT_MONITORED");
        }
        result.put("llm", llmHealth);
        result.put("wsConnections", activeConnections.get());

        sendResponse(ws, "health", requestId, result);
    }

    private void handleGetScales(ServerWebSocket ws, String requestId) {
        Map<String, ScaleDefinition> scales = assessmentService.getScales();
        sendResponse(ws, "scales", requestId, scales);
    }

    private void handleGetScale(ServerWebSocket ws, JsonNode payload, String requestId) {
        String scaleTypeStr = payload.path("scaleType").asText("");
        try {
            ScaleType scaleType = ScaleType.valueOf(scaleTypeStr.toUpperCase());
            ScaleDefinition scale = assessmentService.getScale(scaleType);
            sendResponse(ws, "scale", requestId, scale);
        } catch (IllegalArgumentException e) {
            sendResponse(ws, "error_response", requestId, Map.of("error", "Invalid scale type: " + scaleTypeStr));
        }
    }

    private void handleSubmitAssessment(ServerWebSocket ws, JsonNode payload, String requestId) {
        try {
            String userId = payload.path("userId").asText("");
            String scaleTypeStr = payload.path("scaleType").asText("");
            ScaleType scaleType = ScaleType.valueOf(scaleTypeStr.toUpperCase());

            List<Integer> answers = new ArrayList<>();
            JsonNode answersNode = payload.path("answers");
            if (answersNode.isArray()) {
                for (JsonNode a : answersNode) {
                    answers.add(a.asInt());
                }
            }

            AssessmentSubmission submission = new AssessmentSubmission(userId, scaleType, answers);
            AssessmentResult result = assessmentService.submit(submission);
            sendResponse(ws, "assessment_result", requestId, result);
        } catch (IllegalArgumentException e) {
            sendResponse(ws, "error_response", requestId, Map.of("error", "Invalid scale type"));
        } catch (Exception e) {
            logger.error("Assessment submission failed", e);
            sendResponse(ws, "error_response", requestId, Map.of("error", e.getMessage()));
        }
    }

    private void handleGetAssessmentHistory(ServerWebSocket ws, JsonNode payload, String requestId) {
        String userId = payload.path("userId").asText("");
        List<AssessmentResult> history = assessmentService.getHistory(userId);
        sendResponse(ws, "assessment_history", requestId, history);
    }

    private void handleCreateUser(ServerWebSocket ws, String requestId) {
        SoulUser user = userService.createAnonymousUser();
        sendResponse(ws, "user_created", requestId, user);
    }

    private void handleCheckin(ServerWebSocket ws, JsonNode payload, String requestId) {
        try {
            String userId = payload.path("userId").asText("");
            String emotion = payload.path("emotion").asText("");
            String note = payload.path("note").asText(null);
            EmotionRecord record = userService.checkin(userId, emotion, note);
            sendResponse(ws, "checkin_result", requestId, record);
        } catch (Exception e) {
            logger.error("Checkin failed", e);
            sendResponse(ws, "error_response", requestId, Map.of("error", e.getMessage()));
        }
    }

    private void handleGetEmotions(ServerWebSocket ws, JsonNode payload, String requestId) {
        String userId = payload.path("userId").asText("");
        int days = payload.path("days").asInt(7);
        List<EmotionRecord> emotions = userService.getEmotions(userId, days);
        sendResponse(ws, "emotions", requestId, emotions);
    }

    private void handleGetStats(ServerWebSocket ws, JsonNode payload, String requestId) {
        String userId = payload.path("userId").asText("");
        Map<String, Object> stats = userService.getStats(userId);
        sendResponse(ws, "stats", requestId, stats);
    }

    // ==================== Memory ====================

    private void handleSearchMemory(ServerWebSocket ws, JsonNode payload, String requestId) {
        String query = payload.path("query").asText("");
        int topK = payload.path("topK").asInt(10);
        try {
            List<Map<String, Object>> results = soulComfortChatService.searchMemory(query, topK);
            sendResponse(ws, "memory_results", requestId, results);
        } catch (Exception e) {
            logger.error("Memory search failed", e);
            sendResponse(ws, "error_response", requestId, Map.of("error", e.getMessage()));
        }
    }

    private void handleRememberDurable(ServerWebSocket ws, JsonNode payload, String requestId) {
        String section = payload.path("section").asText("");
        String content = payload.path("content").asText("");
        try {
            soulComfortChatService.rememberDurable(section, content);
            sendResponse(ws, "remember_result", requestId, Map.of("status", "saved", "section", section));
        } catch (Exception e) {
            logger.error("Remember durable failed", e);
            sendResponse(ws, "error_response", requestId, Map.of("error", e.getMessage()));
        }
    }

    // ==================== MCP Tools Detail ====================

    private void handleGetToolsDetail(ServerWebSocket ws, String requestId) {
        List<Map<String, Object>> toolList = new ArrayList<>();
        for (com.lightweightai.kernel.agent.Tool tool : toolRegistry.getAll()) {
            Map<String, Object> toolInfo = new LinkedHashMap<>();
            toolInfo.put("name", tool.getName());
            toolInfo.put("description", tool.getDescription());
            toolInfo.put("enabled", toolRegistry.isEnabled(tool.getName()));

            if (tool instanceof com.lightweightai.mcp.McpToolWrapper mcpTool) {
                toolInfo.put("source", "mcp");
                toolInfo.put("server", mcpTool.getServerName());
            } else {
                toolInfo.put("source", "local");
            }

            if (tool instanceof com.lightweightai.kernel.agent.ToolMetadata meta) {
                toolInfo.put("category", meta.getCategory());
                toolInfo.put("tags", meta.getTags());
                toolInfo.put("readOnly", meta.isReadOnly());
                toolInfo.put("destructive", meta.isDestructive());
                toolInfo.put("clientSide", meta.isClientSide());
            }

            if (tool.getSchema() != null) {
                toolInfo.put("input_schema", tool.getSchema().toMap());
            }
            toolList.add(toolInfo);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", toolList.size());
        result.put("enabled", toolRegistry.enabledCount());
        result.put("tools", toolList);
        sendResponse(ws, "tools_detail", requestId, result);
    }

    private void handleGetToolDetail(ServerWebSocket ws, JsonNode payload, String requestId) {
        String toolName = payload.path("toolName").asText("");
        toolRegistry.get(toolName)
            .ifPresentOrElse(
                tool -> {
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("name", tool.getName());
                    detail.put("description", tool.getDescription());
                    detail.put("enabled", toolRegistry.isEnabled(tool.getName()));

                    if (tool instanceof com.lightweightai.mcp.McpToolWrapper mcpTool) {
                        detail.put("source", "mcp");
                        detail.put("server", mcpTool.getServerName());
                    } else {
                        detail.put("source", "local");
                    }

                    if (tool instanceof com.lightweightai.kernel.agent.ToolMetadata meta) {
                        detail.put("category", meta.getCategory());
                        detail.put("tags", meta.getTags());
                        detail.put("readOnly", meta.isReadOnly());
                        detail.put("destructive", meta.isDestructive());
                        detail.put("idempotent", meta.isIdempotent());
                        detail.put("openWorld", meta.isOpenWorld());
                        detail.put("clientSide", meta.isClientSide());
                    }

                    if (tool.getSchema() != null) {
                        detail.put("input_schema", tool.getSchema().toMap());
                    }
                    sendResponse(ws, "tool_detail", requestId, detail);
                },
                () -> sendResponse(ws, "error_response", requestId, Map.of("error", "Tool not found: " + toolName))
            );
    }

    private void handleGetMcpServers(ServerWebSocket ws, String requestId) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (mcpToolRegistrar == null) {
            result.put("enabled", false);
            result.put("servers", List.of());
        } else {
            result.put("enabled", true);
            result.put("serverCount", mcpToolRegistrar.getServerCount());
            result.put("totalToolCount", mcpToolRegistrar.getTotalToolCount());

            List<Map<String, Object>> servers = new ArrayList<>();
            mcpToolRegistrar.getClients().forEach(client -> {
                Map<String, Object> serverInfo = new LinkedHashMap<>();
                serverInfo.put("name", client.getServerName());
                List<Map<String, Object>> tools = new ArrayList<>();
                client.getDiscoveredTools().forEach(wrapper -> {
                    Map<String, Object> toolInfo = new LinkedHashMap<>();
                    toolInfo.put("name", wrapper.getName());
                    toolInfo.put("description", wrapper.getDescription());
                    toolInfo.put("enabled", toolRegistry.isEnabled(wrapper.getName()));
                    tools.add(toolInfo);
                });
                serverInfo.put("toolCount", tools.size());
                serverInfo.put("tools", tools);
                servers.add(serverInfo);
            });
            result.put("servers", servers);
        }
        sendResponse(ws, "mcp_servers", requestId, result);
    }

    private void handleGetToolDefinitions(ServerWebSocket ws, String requestId) {
        List<Map<String, Object>> definitions = toolRegistry.getToolDefinitions();
        sendResponse(ws, "tool_definitions", requestId, Map.of("count", definitions.size(), "tools", definitions));
    }

    // ==================== WebSocket 消息发送（线程安全） ====================

    /**
     * 安全发送文本消息到 WebSocket。
     * Vert.x ServerWebSocket.writeTextMessage 是线程安全的（内部使用 event loop 调度），
     * 无需外部同步。写入失败时仅记录日志，不影响其他连接。
     */
    private void safeSend(ServerWebSocket ws, String json) {
        if (ws.isClosed()) {
            logger.warn("[WS-STREAM] safeSend DROPPED (ws closed): {}",
                json.substring(0, Math.min(120, json.length())));
            return;
        }
        totalMessagesSent.incrementAndGet();
        logger.debug("[WS-STREAM] safeSend: {}", json.substring(0, Math.min(120, json.length())));
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

    private void sendReasoningToken(ServerWebSocket ws, String delta) {
        try {
            ObjectNode msg = MAPPER.createObjectNode();
            msg.put("type", "reasoning_token");
            msg.put("data", delta);
            safeSend(ws, MAPPER.writeValueAsString(msg));
        } catch (Exception e) {
            logger.error("Failed to serialize reasoning token message", e);
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

    private void sendPostProcessData(ServerWebSocket ws, StreamEvent event) {
        try {
            ObjectNode msg = MAPPER.createObjectNode();
            msg.put("type", "post_process");
            msg.put("category", event.getCategory() != null ? event.getCategory() : "");
            if (event.getData() != null) {
                msg.set("data", MAPPER.valueToTree(event.getData()));
            }
            safeSend(ws, MAPPER.writeValueAsString(msg));
        } catch (Exception e) {
            logger.error("Failed to serialize post_process message", e);
        }
    }

    private void sendTrace(ServerWebSocket ws, StreamEvent event) {
        try {
            ObjectNode msg = MAPPER.createObjectNode();
            msg.put("type", "trace");
            ObjectNode data = MAPPER.createObjectNode();
            data.put("phase", event.getTracePhase());
            data.put("message", event.getTraceMessage());
            data.put("timestamp", event.getTraceTimestamp());
            if (event.getData() != null) {
                data.set("extra", MAPPER.valueToTree(event.getData()));
            }
            msg.set("data", data);
            safeSend(ws, MAPPER.writeValueAsString(msg));
        } catch (Exception e) {
            logger.error("Failed to serialize trace message", e);
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

        // 取消正在进行的 reactive 流，防止 WebSocket 断开后继续消耗资源
        Disposable stream = activeStreams.remove(socketId);
        if (stream != null && !stream.isDisposed()) {
            stream.dispose();
        }

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
        clientToolSessionBinder.unbind(socketId);
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
        activeStreams.values().forEach(d -> { if (!d.isDisposed()) d.dispose(); });
        activeStreams.clear();
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
