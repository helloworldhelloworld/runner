package com.lightweightai.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.gateway.Gateway;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.gateway.GatewayResponse;
import com.lightweightai.kernel.gateway.GatewayStreamHandler;
import com.lightweightai.kernel.gateway.SessionManager;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.ResilientLLMProvider;
import com.lightweightai.web.model.ChatRequest;
import com.lightweightai.web.model.ChatResponse;
import com.lightweightai.web.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Chat API controller
 *
 * /api/chat 和 /api/chat/stream 通过 Gateway 委托，
 * /api/skills、/api/tools、/api/health 保留原有逻辑。
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final Gateway gateway;
    private final LLMProvider llmProvider;
    private final ObjectMapper compactMapper;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ChatController(ChatService chatService,
                         Gateway gateway,
                         LLMProvider llmProvider,
                         ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.gateway = gateway;
        this.llmProvider = llmProvider;
        this.compactMapper = objectMapper.copy()
            .disable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Chat endpoint - delegates to Gateway
     */
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        logger.info("Received chat request (soul comfort mode: {})", request.isSoulComfortMode());

        GatewayRequest gatewayRequest = GatewayRequest.builder()
            .sessionId(request.getSessionId())
            .message(request.getMessage())
            .metadata("model", request.getModel())
            .build();

        GatewayResponse response = gateway.handle(gatewayRequest);

        ChatResponse chatResponse = new ChatResponse();
        if (response.isError()) {
            return ChatResponse.error(response.getErrorMessage());
        }
        chatResponse.setResponse(response.getText());

        Map<String, Object> meta = response.getMetadata();
        if (meta != null) {
            Object skills = meta.get("skillsApplied");
            if (skills instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> skillsList = (List<String>) skills;
                chatResponse.setSkillsApplied(skillsList);
            }
            chatResponse.setMetadata(meta);
        }

        return chatResponse;
    }

    /**
     * Streaming chat endpoint (SSE) - delegates to Gateway
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        logger.info("Received streaming chat request (session: {})", request.getSessionId());

        SseEmitter emitter = new SseEmitter(60000L);

        executor.submit(() -> {
            try {
                String sessionId = request.getSessionId() != null ? request.getSessionId() : "default";

                GatewayRequest gatewayRequest = GatewayRequest.builder()
                    .sessionId(sessionId)
                    .message(request.getMessage())
                    .metadata("model", request.getModel())
                    .metadata("debug", request.isDebug())
                    .build();

                gateway.handleStream(gatewayRequest, new GatewayStreamHandler() {
                    @Override
                    public void onDelta(String delta) {
                        // 不会被调用
                    }

                    @Override
                    public void onDelta(String delta, Map<String, Object> metadata) {
                        try {
                            Map<String, Object> event = new java.util.HashMap<>();
                            event.put("type", "delta");
                            event.put("delta", delta);
                            if (metadata != null && metadata.containsKey("emotion")) {
                                event.put("emotion", metadata.get("emotion"));
                            } else {
                                event.put("emotion", "");
                            }
                            emitter.send(SseEmitter.event()
                                .name("message")
                                .data(compactMapper.writeValueAsString(event)));
                        } catch (IOException e) {
                            logger.error("Failed to send SSE event", e);
                        }
                    }

                    @Override
                    public void onComplete(GatewayResponse response) {
                        try {
                            Map<String, Object> completeEvent = new java.util.HashMap<>();
                            completeEvent.put("type", "complete");
                            completeEvent.put("response", response.getText());

                            Map<String, Object> meta = response.getMetadata();
                            Object skills = meta != null ? meta.get("skillsApplied") : null;
                            completeEvent.put("skillsApplied",
                                skills != null ? skills : List.of("soul-comfort", "openclaw-memory"));

                            emitter.send(SseEmitter.event()
                                .name("message")
                                .data(compactMapper.writeValueAsString(completeEvent)));
                            emitter.complete();
                        } catch (IOException e) {
                            logger.error("Failed to send complete event", e);
                            emitter.completeWithError(e);
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        logger.error("Streaming chat error", error);
                        try {
                            Map<String, Object> errorEvent = Map.of(
                                "type", "error",
                                "message", error.getMessage() != null ? error.getMessage() : "Unknown error"
                            );
                            emitter.send(SseEmitter.event()
                                .name("error")
                                .data(compactMapper.writeValueAsString(errorEvent)));
                        } catch (IOException e) {
                            logger.error("Failed to send error event", e);
                        }
                        emitter.completeWithError(error);
                    }
                });

            } catch (Exception e) {
                logger.error("Failed to start streaming chat", e);
                emitter.completeWithError(e);
            }
        });

        emitter.onCompletion(() -> logger.debug("SSE connection completed"));
        emitter.onTimeout(() -> logger.warn("SSE connection timed out"));
        emitter.onError(e -> logger.error("SSE error", e));

        return emitter;
    }

    /**
     * Reactive streaming chat endpoint (SSE)
     *
     * 支持工具进度（tool_progress）、工具日志（tool_log）等全链路流式事件。
     */
    @PostMapping(value = "/chat/stream/reactive", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStreamReactive(@RequestBody ChatRequest request) {
        logger.info("Received reactive streaming chat request (session: {})", request.getSessionId());

        SseEmitter emitter = new SseEmitter(120000L);

        executor.submit(() -> {
            try {
                String sessionId = request.getSessionId() != null ? request.getSessionId() : "default";

                GatewayRequest gatewayRequest = GatewayRequest.builder()
                    .sessionId(sessionId)
                    .message(request.getMessage())
                    .metadata("model", request.getModel())
                    .metadata("debug", request.isDebug())
                    .build();

                gateway.handleStreamReactive(gatewayRequest)
                    .subscribe(
                        event -> {
                            try {
                                Map<String, Object> eventData = toEventMap(event);
                                emitter.send(SseEmitter.event()
                                    .name("message")
                                    .data(compactMapper.writeValueAsString(eventData)));
                            } catch (IOException e) {
                                logger.error("Failed to send SSE event", e);
                            }
                        },
                        error -> {
                            logger.error("Reactive streaming chat error", error);
                            try {
                                Map<String, Object> errorEvent = Map.of(
                                    "type", "error",
                                    "message", error.getMessage() != null ? error.getMessage() : "Unknown error"
                                );
                                emitter.send(SseEmitter.event()
                                    .name("error")
                                    .data(compactMapper.writeValueAsString(errorEvent)));
                            } catch (IOException e) {
                                logger.error("Failed to send error event", e);
                            }
                            emitter.completeWithError(error);
                        },
                        () -> {
                            try {
                                emitter.complete();
                            } catch (Exception e) {
                                logger.debug("Emitter already completed", e);
                            }
                        }
                    );
            } catch (Exception e) {
                logger.error("Failed to start reactive streaming chat", e);
                emitter.completeWithError(e);
            }
        });

        emitter.onCompletion(() -> logger.debug("Reactive SSE connection completed"));
        emitter.onTimeout(() -> logger.warn("Reactive SSE connection timed out"));
        emitter.onError(e -> logger.error("Reactive SSE error", e));

        return emitter;
    }

    /**
     * 将 StreamEvent 转换为可序列化的 Map
     */
    private Map<String, Object> toEventMap(StreamEvent event) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("type", event.getType().name().toLowerCase());

        switch (event.getType()) {
            case TEXT_DELTA -> map.put("delta", event.getTextDelta());
            case TOOL_CALL_START -> {
                if (event.getToolCall() != null) {
                    map.put("toolName", event.getToolCall().getName());
                    map.put("toolCallId", event.getToolCall().getId());
                }
            }
            case TOOL_PROGRESS -> {
                ToolResultChunk chunk = event.getChunk();
                if (chunk != null) {
                    map.put("toolName", chunk.getToolName());
                    map.put("progress", chunk.getProgress());
                    map.put("total", chunk.getTotal());
                    map.put("message", chunk.getMessage());
                    if (chunk.getMeta() != null && !chunk.getMeta().isEmpty()) {
                        map.put("meta", chunk.getMeta());
                    }
                }
            }
            case TOOL_LOG -> {
                ToolResultChunk chunk = event.getChunk();
                if (chunk != null) {
                    map.put("toolName", chunk.getToolName());
                    map.put("message", chunk.getMessage());
                    if (chunk.getMeta() != null && !chunk.getMeta().isEmpty()) {
                        map.put("meta", chunk.getMeta());
                    }
                }
            }
            case TOOL_RESULT, TOOL_ERROR -> {
                ToolResultChunk chunk = event.getChunk();
                if (chunk != null) {
                    map.put("toolName", chunk.getToolName());
                    map.put("message", chunk.getMessage());
                }
            }
            case POST_PROCESS_DATA -> {
                map.put("category", event.getCategory());
                if (event.getData() != null) {
                    map.put("data", event.getData());
                }
            }
            case LLM_COMPLETE -> map.put("complete", true);
            case ERROR -> {
                if (event.getError() != null) {
                    map.put("message", event.getError().getMessage());
                }
            }
        }
        return map;
    }

    /**
     * Get available skills
     */
    @GetMapping("/skills")
    public List<Map<String, String>> getSkills() {
        return chatService.getAvailableSkills();
    }

    /**
     * Get available tools
     */
    @GetMapping("/tools")
    public List<Map<String, Object>> getTools() {
        return chatService.getAvailableTools();
    }

    /**
     * Health check — 包含 LLM Provider 健康状态
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("status", "UP");
        result.put("skills", chatService.getAvailableSkills().size());
        result.put("tools", chatService.getAvailableTools().size());
        result.put("soulComfortMode", "enabled");

        // LLM Provider 健康信息
        Map<String, Object> llmHealth = new java.util.LinkedHashMap<>();
        if (llmProvider instanceof ResilientLLMProvider resilient) {
            ResilientLLMProvider.HealthInfo info = resilient.getHealthInfo();
            llmHealth.put("status", info.status().name());
            llmHealth.put("provider", info.providerName());
            llmHealth.put("totalRequests", info.totalRequests());
            llmHealth.put("totalFailures", info.totalFailures());
            llmHealth.put("consecutiveFailures", info.consecutiveFailures());
            if (info.lastSuccessTime() > 0) {
                llmHealth.put("lastSuccess", new java.util.Date(info.lastSuccessTime()).toString());
            }
            if (info.lastFailureTime() > 0) {
                llmHealth.put("lastFailure", new java.util.Date(info.lastFailureTime()).toString());
                llmHealth.put("lastError", info.lastError());
            }
        } else {
            llmHealth.put("provider", llmProvider.getProviderName());
            llmHealth.put("status", "NOT_MONITORED");
        }
        result.put("llm", llmHealth);

        return result;
    }

    /**
     * Get session history
     */
    @GetMapping("/session/{sessionId}/history")
    public List<Map<String, String>> getSessionHistory(@PathVariable("sessionId") String sessionId) {
        logger.info("Getting session history for: {}", sessionId);
        SessionManager sm = gateway.getSessionManager();
        if (sm == null) {
            return List.of();
        }
        return sm.getSessionHistory(sessionId);
    }

    /**
     * Get session summary
     */
    @GetMapping("/session/{sessionId}/summary")
    public Map<String, Object> getSessionSummary(@PathVariable("sessionId") String sessionId) {
        logger.info("Getting session summary for: {}", sessionId);
        SessionManager sm = gateway.getSessionManager();
        if (sm == null) {
            return Map.of("sessionId", sessionId);
        }
        return sm.getSessionSummary(sessionId);
    }

    /**
     * Clear session
     */
    @DeleteMapping("/session/{sessionId}")
    public Map<String, Object> clearSession(@PathVariable("sessionId") String sessionId) {
        logger.info("Clearing session: {}", sessionId);
        SessionManager sm = gateway.getSessionManager();
        if (sm != null) {
            sm.clearSession(sessionId);
        }
        return Map.of("status", "cleared", "sessionId", sessionId);
    }
}
