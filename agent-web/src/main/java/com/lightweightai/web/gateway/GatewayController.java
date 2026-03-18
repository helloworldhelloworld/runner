package com.lightweightai.web.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.gateway.Gateway;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.gateway.GatewayResponse;
import com.lightweightai.kernel.gateway.GatewayStreamHandler;
import com.lightweightai.kernel.gateway.SessionManager;
import com.lightweightai.kernel.llm.ToolCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Gateway Controller - 纯协议适配器
 *
 * 仅负责 HTTP/SSE 协议适配，所有业务逻辑委托 Gateway（kernel）。
 * 支持所有客户端：Web / iOS / Android / HarmonyOS
 *
 * <p>工具调用事件通过 {@link GatewayStreamHandler} 的扩展回调传递：
 * <ul>
 *   <li>{@code onToolCallStart} — LLM 决定调用工具</li>
 *   <li>{@code onToolProgress} — 工具执行进度（MCP ProgressNotification）</li>
 *   <li>{@code onToolLog} — 工具执行日志（MCP LoggingNotification）</li>
 *   <li>{@code onToolResult} — 工具执行完成</li>
 *   <li>{@code onToolError} — 工具执行错误</li>
 * </ul>
 */
@RestController
@RequestMapping("/gateway")
@CrossOrigin(origins = "*")
public class GatewayController {

    private static final Logger logger = LoggerFactory.getLogger(GatewayController.class);

    private final Gateway gateway;
    private final ObjectMapper compactMapper;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public GatewayController(Gateway gateway, ObjectMapper objectMapper) {
        this.gateway = gateway;
        this.compactMapper = objectMapper.copy()
            .disable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * 统一聊天接口（同步）
     */
    @PostMapping("/chat")
    public UnifiedChatResponse chat(@RequestBody UnifiedChatRequest request) {
        String requestId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();

        logger.info("Gateway chat - requestId: {}, client: {}, session: {}",
            requestId, request.getClientType(), request.getSessionId());

        try {
            GatewayRequest gatewayRequest = toGatewayRequest(request, requestId);
            GatewayResponse response = gateway.handle(gatewayRequest);

            return toUnifiedResponse(response, request.getSessionId(), startTime);
        } catch (Exception e) {
            logger.error("Gateway chat failed", e);
            return UnifiedChatResponse.error(requestId, e.getMessage());
        }
    }

    /**
     * 统一聊天接口（流式 SSE — 支持 MCP 工具调用事件）
     *
     * 通过 Gateway.handleStream() → GatewayStreamHandler 回调接收全链路事件，
     * 包括文本片段、工具调用开始/进度/日志/结果/错误。
     *
     * SSE event name 对应关系：
     * <ul>
     *   <li>{@code message} — 文本片段 (TEXT_DELTA) 和完成 (COMPLETE)</li>
     *   <li>{@code tool_call_start} — 工具调用开始</li>
     *   <li>{@code tool_progress} — 工具执行进度</li>
     *   <li>{@code tool_log} — 工具执行日志</li>
     *   <li>{@code tool_result} — 工具执行结果</li>
     *   <li>{@code tool_error} — 工具执行错误</li>
     *   <li>{@code error} — 错误</li>
     * </ul>
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody UnifiedChatRequest request) {
        String requestId = UUID.randomUUID().toString();
        String sessionId = request.getSessionId() != null ? request.getSessionId() : "default";

        logger.info("Gateway stream - requestId: {}, client: {}, session: {}",
            requestId, request.getClientType(), sessionId);

        SseEmitter emitter = new SseEmitter(120000L);

        executor.submit(() -> {
            try {
                GatewayRequest gatewayRequest = toGatewayRequest(request, requestId);

                gateway.handleStream(gatewayRequest, new GatewayStreamHandler() {
                    @Override
                    public void onDelta(String delta) {
                        // 不会被调用（Gateway 使用带 metadata 的版本）
                    }

                    @Override
                    public void onDelta(String delta, Map<String, Object> metadata) {
                        try {
                            UnifiedChatResponse deltaResponse = UnifiedChatResponse.delta(requestId, delta);
                            if (metadata != null && !metadata.isEmpty()) {
                                deltaResponse.setMetadata(metadata);
                            }
                            emitter.send(SseEmitter.event()
                                .name("message")
                                .data(compactMapper.writeValueAsString(deltaResponse)));
                        } catch (IOException e) {
                            logger.error("Failed to send SSE delta", e);
                        }
                    }

                    @Override
                    public void onComplete(GatewayResponse response) {
                        try {
                            UnifiedChatResponse completeResponse = UnifiedChatResponse.complete(
                                requestId,
                                sessionId,
                                response.getText()
                            );
                            completeResponse.setLatencyMs(response.getLatencyMs());

                            Map<String, Object> meta = response.getMetadata();
                            if (meta != null) {
                                Object skills = meta.get("skillsApplied");
                                if (skills instanceof List) {
                                    @SuppressWarnings("unchecked")
                                    List<String> skillsList = (List<String>) skills;
                                    completeResponse.setSkillsApplied(skillsList);
                                }
                                completeResponse.setMetadata(meta);
                            }

                            emitter.send(SseEmitter.event()
                                .name("message")
                                .data(compactMapper.writeValueAsString(completeResponse)));
                            emitter.complete();
                        } catch (IOException e) {
                            logger.error("Failed to send SSE complete", e);
                            emitter.completeWithError(e);
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        try {
                            UnifiedChatResponse errorResponse = UnifiedChatResponse.error(
                                requestId,
                                error.getMessage()
                            );
                            emitter.send(SseEmitter.event()
                                .name("error")
                                .data(compactMapper.writeValueAsString(errorResponse)));
                        } catch (IOException e) {
                            logger.error("Failed to send SSE error", e);
                        }
                        emitter.completeWithError(error);
                    }

                    // ==================== 工具事件回调 ====================

                    @Override
                    public void onToolCallStart(ToolCall toolCall) {
                        try {
                            UnifiedChatResponse response = UnifiedChatResponse.toolCallStart(
                                requestId,
                                toolCall.getName(),
                                toolCall.getId());
                            emitter.send(SseEmitter.event()
                                .name("tool_call_start")
                                .data(compactMapper.writeValueAsString(response)));
                        } catch (IOException e) {
                            logger.error("Failed to send SSE tool_call_start", e);
                        }
                    }

                    @Override
                    public void onToolProgress(ToolResultChunk chunk) {
                        try {
                            UnifiedChatResponse response = UnifiedChatResponse.toolProgress(
                                requestId,
                                chunk.getToolName(),
                                chunk.getProgress(),
                                chunk.getTotal(),
                                chunk.getMessage() != null ? chunk.getMessage() : "");
                            emitter.send(SseEmitter.event()
                                .name("tool_progress")
                                .data(compactMapper.writeValueAsString(response)));
                        } catch (IOException e) {
                            logger.error("Failed to send SSE tool_progress", e);
                        }
                    }

                    @Override
                    public void onToolLog(ToolResultChunk chunk) {
                        try {
                            UnifiedChatResponse response = UnifiedChatResponse.toolLog(
                                requestId,
                                chunk.getToolName(),
                                chunk.getMessage() != null ? chunk.getMessage() : "");
                            emitter.send(SseEmitter.event()
                                .name("tool_log")
                                .data(compactMapper.writeValueAsString(response)));
                        } catch (IOException e) {
                            logger.error("Failed to send SSE tool_log", e);
                        }
                    }

                    @Override
                    public void onToolResult(ToolResultChunk chunk) {
                        if (chunk.getResult() == null) return;
                        try {
                            UnifiedChatResponse response = UnifiedChatResponse.toolResult(
                                requestId,
                                chunk.getToolName(),
                                chunk.getResult().getContent(),
                                chunk.getResult().isError());
                            emitter.send(SseEmitter.event()
                                .name("tool_result")
                                .data(compactMapper.writeValueAsString(response)));
                        } catch (IOException e) {
                            logger.error("Failed to send SSE tool_result", e);
                        }
                    }

                    @Override
                    public void onToolError(ToolResultChunk chunk) {
                        try {
                            String msg = chunk.getMessage() != null
                                ? chunk.getMessage() : "Tool execution failed";
                            UnifiedChatResponse response = UnifiedChatResponse.toolError(
                                requestId, chunk.getToolName(), msg);
                            emitter.send(SseEmitter.event()
                                .name("tool_error")
                                .data(compactMapper.writeValueAsString(response)));
                        } catch (IOException e) {
                            logger.error("Failed to send SSE tool_error", e);
                        }
                    }
                });

            } catch (Exception e) {
                logger.error("Gateway stream failed", e);
                emitter.completeWithError(e);
            }
        });

        emitter.onCompletion(() -> logger.debug("SSE completed - {}", requestId));
        emitter.onTimeout(() -> logger.warn("SSE timeout - {}", requestId));
        emitter.onError(e -> logger.error("SSE error - {}", requestId, e));

        return emitter;
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "gateway", "active",
            "version", "1.0.0",
            "supportedClients", new String[]{"web", "ios", "android", "harmonyos", "miniprogram"}
        );
    }

    /**
     * 获取会话历史
     */
    @GetMapping("/session/{sessionId}/history")
    public Map<String, Object> getSessionHistory(@PathVariable("sessionId") String sessionId) {
        logger.info("Gateway get history - session: {}", sessionId);
        SessionManager sm = gateway.getSessionManager();
        if (sm == null) {
            return Map.of("sessionId", sessionId, "history", List.of());
        }
        return Map.of(
            "sessionId", sessionId,
            "history", sm.getSessionHistory(sessionId)
        );
    }

    /**
     * 获取会话摘要
     */
    @GetMapping("/session/{sessionId}/summary")
    public Map<String, Object> getSessionSummary(@PathVariable("sessionId") String sessionId) {
        logger.info("Gateway get summary - session: {}", sessionId);
        SessionManager sm = gateway.getSessionManager();
        if (sm == null) {
            return Map.of("sessionId", sessionId);
        }
        return sm.getSessionSummary(sessionId);
    }

    /**
     * 清空会话
     */
    @DeleteMapping("/session/{sessionId}")
    public Map<String, Object> clearSession(@PathVariable("sessionId") String sessionId) {
        logger.info("Gateway clear session: {}", sessionId);
        SessionManager sm = gateway.getSessionManager();
        if (sm != null) {
            sm.clearSession(sessionId);
        }
        return Map.of("sessionId", sessionId, "cleared", true);
    }

    // ==================== 协议转换（业务无关） ====================

    private GatewayRequest toGatewayRequest(UnifiedChatRequest unified, String requestId) {
        GatewayRequest.Builder builder = GatewayRequest.builder()
            .requestId(requestId)
            .sessionId(unified.getSessionId())
            .message(unified.getMessage());

        if (unified.getModel() != null) {
            builder.metadata("model", unified.getModel());
        }
        if (unified.getClientType() != null) {
            builder.metadata("clientType", unified.getClientType().name());
        }
        if (unified.getExtra() != null) {
            builder.metadata(unified.getExtra());
        }

        return builder.build();
    }

    private UnifiedChatResponse toUnifiedResponse(GatewayResponse response, String sessionId, long startTime) {
        if (response.isError()) {
            return UnifiedChatResponse.error(response.getRequestId(), response.getErrorMessage());
        }

        UnifiedChatResponse result = UnifiedChatResponse.full(
            response.getRequestId(),
            sessionId,
            response.getText()
        );
        result.setLatencyMs(response.getLatencyMs() > 0
            ? response.getLatencyMs()
            : System.currentTimeMillis() - startTime);

        Map<String, Object> meta = response.getMetadata();
        if (meta != null) {
            Object skills = meta.get("skillsApplied");
            if (skills instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> skillsList = (List<String>) skills;
                result.setSkillsApplied(skillsList);
            }
            result.setMetadata(meta);
        }

        return result;
    }
}
