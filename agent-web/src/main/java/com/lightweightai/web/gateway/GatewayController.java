package com.lightweightai.web.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lightweightai.kernel.gateway.Gateway;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.gateway.GatewayResponse;
import com.lightweightai.kernel.gateway.GatewayStreamHandler;
import com.lightweightai.kernel.gateway.SessionManager;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Gateway Controller - 纯协议适配器
 *
 * 仅负责 HTTP/SSE 协议适配，所有业务逻辑委托 Gateway（kernel）。
 * 支持所有客户端：Web / iOS / Android / HarmonyOS
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
     * 统一聊天接口（流式 SSE）
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody UnifiedChatRequest request) {
        String requestId = UUID.randomUUID().toString();
        String sessionId = request.getSessionId() != null ? request.getSessionId() : "default";

        logger.info("Gateway stream - requestId: {}, client: {}, session: {}",
            requestId, request.getClientType(), sessionId);

        SseEmitter emitter = new SseEmitter(60000L);

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

                            // 从 metadata 提取 skillsApplied
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
