package com.lightweightai.web.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lightweightai.web.model.ChatRequest;
import com.lightweightai.web.model.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Gateway Controller - 统一入口
 *
 * 提供客户端无关的 API：
 * - POST /gateway/chat - 同步聊天
 * - POST /gateway/chat/stream - 流式聊天 (SSE)
 * - GET  /gateway/health - 健康检查
 *
 * 支持所有客户端：Web / iOS / Android / HarmonyOS
 */
@RestController
@RequestMapping("/gateway")
@CrossOrigin(origins = "*")
public class GatewayController {

    private static final Logger logger = LoggerFactory.getLogger(GatewayController.class);

    private final GatewayService gatewayService;
    private final ObjectMapper compactMapper;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public GatewayController(GatewayService gatewayService, ObjectMapper objectMapper) {
        this.gatewayService = gatewayService;
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
            // 转换为内部请求
            ChatRequest internal = convertToInternal(request);
            ChatResponse response = gatewayService.chat(internal);

            // 转换为统一响应
            UnifiedChatResponse result = UnifiedChatResponse.full(
                requestId,
                request.getSessionId(),
                response.getResponse()
            );
            result.setLatencyMs(System.currentTimeMillis() - startTime);
            result.setSkillsApplied(response.getSkillsApplied());
            result.setMetadata(response.getMetadata());

            return result;

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
                ChatRequest internal = convertToInternal(request);
                internal.setSessionId(sessionId);

                gatewayService.chatStream(internal, new GatewayService.StreamCallback() {
                    @Override
                    public void onDelta(String delta, String emotion) {
                        try {
                            UnifiedChatResponse deltaResponse = UnifiedChatResponse.delta(requestId, delta);
                            if (emotion != null && !emotion.isEmpty()) {
                                deltaResponse.setMetadata(Map.of("emotion", emotion));
                            }
                            emitter.send(SseEmitter.event()
                                .name("message")
                                .data(compactMapper.writeValueAsString(deltaResponse)));
                        } catch (IOException e) {
                            logger.error("Failed to send SSE delta", e);
                        }
                    }

                    @Override
                    public void onComplete(ChatResponse response) {
                        try {
                            UnifiedChatResponse completeResponse = UnifiedChatResponse.complete(
                                requestId,
                                sessionId,
                                response.getResponse()
                            );
                            completeResponse.setSkillsApplied(response.getSkillsApplied());
                            completeResponse.setMetadata(response.getMetadata());

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
    public Map<String, Object> getSessionHistory(@PathVariable String sessionId) {
        logger.info("Gateway get history - session: {}", sessionId);
        // 委托给 SoulComfortChatService
        return Map.of(
            "sessionId", sessionId,
            "history", gatewayService.chat(new ChatRequest()) // TODO: 实现历史获取
        );
    }

    // ==================== 私有方法 ====================

    /**
     * 转换为内部请求格式
     */
    private ChatRequest convertToInternal(UnifiedChatRequest unified) {
        ChatRequest internal = new ChatRequest();
        internal.setMessage(unified.getMessage());
        internal.setSessionId(unified.getSessionId());
        internal.setModel(unified.getModel());
        internal.setSoulComfortMode(true); // 默认使用心灵引导模式

        // 根据客户端类型调整
        if (unified.getClientType() != null) {
            switch (unified.getClientType()) {
                case HARMONYOS:
                case IOS:
                case ANDROID:
                    // 移动端可能有特殊配置
                    break;
                case WEB:
                case MINIPROGRAM:
                default:
                    break;
            }
        }

        return internal;
    }
}
