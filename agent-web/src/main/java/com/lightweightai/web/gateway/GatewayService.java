package com.lightweightai.web.gateway;

import com.lightweightai.kernel.gateway.ChatHandler;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.gateway.GatewayResponse;
import com.lightweightai.kernel.gateway.SessionManager;
import com.lightweightai.web.service.SoulComfortChatService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Gateway 服务层 - ChatHandler + SessionManager 实现
 *
 * 将业务逻辑（SoulComfortChatService）适配为 kernel 层的通用接口。
 * Gateway / Controller / WebSocket 只依赖 ChatHandler 和 SessionManager，
 * 不再直接依赖 SoulComfortChatService。
 */
@Service
public class GatewayService implements ChatHandler, SessionManager {

    private static final Logger logger = LoggerFactory.getLogger(GatewayService.class);

    private final SoulComfortChatService soulComfortService;

    public GatewayService(SoulComfortChatService soulComfortService) {
        this.soulComfortService = soulComfortService;
    }

    // ==================== ChatHandler ====================

    @Override
    public GatewayResponse chat(GatewayRequest request) {
        logger.info("ChatHandler.chat - session: {}", request.getSessionId());

        try {
            com.lightweightai.web.model.ChatRequest internal = toInternal(request);
            com.lightweightai.web.model.ChatResponse response = soulComfortService.chat(internal);

            return toGatewayResponse(response, request);
        } catch (Exception e) {
            logger.error("ChatHandler.chat failed", e);
            return GatewayResponse.error(
                request.getRequestId(),
                request.getSessionId(),
                e.getMessage()
            );
        }
    }

    @Override
    public CompletableFuture<GatewayResponse> chatStream(GatewayRequest request, StreamCallback callback) {
        String sessionId = request.getSessionId() != null ? request.getSessionId() : "default";
        logger.info("ChatHandler.chatStream - session: {}", sessionId);

        CompletableFuture<String> future = soulComfortService.chat(
            request.getMessage(),
            sessionId,
            chunk -> {
                if (!chunk.getDelta().isEmpty()) {
                    Map<String, Object> metadata = new HashMap<>();
                    if (chunk.getEmotion() != null && !chunk.getEmotion().isEmpty()) {
                        metadata.put("emotion", chunk.getEmotion());
                    }
                    callback.onDelta(chunk.getDelta(), metadata);
                }
            }
        );

        CompletableFuture<GatewayResponse> result = new CompletableFuture<>();

        future.thenAccept(fullText -> {
            GatewayResponse response = GatewayResponse.builder()
                .requestId(request.getRequestId())
                .sessionId(sessionId)
                .text(fullText)
                .metadata("mode", "soul-comfort")
                .metadata("hasMemory", true)
                .metadata("skillsApplied", List.of("soul-comfort", "openclaw-memory"))
                .build();
            callback.onComplete(response);
            result.complete(response);
        }).exceptionally(e -> {
            callback.onError(e);
            result.completeExceptionally(e);
            return null;
        });

        return result;
    }

    // ==================== SessionManager ====================

    @Override
    public List<Map<String, String>> getSessionHistory(String sessionId) {
        return soulComfortService.getSessionHistory(sessionId);
    }

    @Override
    public Map<String, Object> getSessionSummary(String sessionId) {
        return soulComfortService.getSessionSummary(sessionId);
    }

    @Override
    public void clearSession(String sessionId) {
        soulComfortService.clearSession(sessionId);
    }

    // ==================== 内部转换 ====================

    private com.lightweightai.web.model.ChatRequest toInternal(GatewayRequest request) {
        com.lightweightai.web.model.ChatRequest internal = new com.lightweightai.web.model.ChatRequest();
        internal.setMessage(request.getMessage());
        internal.setSessionId(request.getSessionId());
        internal.setSoulComfortMode(true);

        Object model = request.getMetadata().get("model");
        if (model instanceof String) {
            internal.setModel((String) model);
        }

        return internal;
    }

    private GatewayResponse toGatewayResponse(
            com.lightweightai.web.model.ChatResponse response,
            GatewayRequest request) {

        if (response.getError() != null) {
            return GatewayResponse.error(
                request.getRequestId(),
                request.getSessionId(),
                response.getError()
            );
        }

        GatewayResponse.Builder builder = GatewayResponse.builder()
            .requestId(request.getRequestId())
            .sessionId(request.getSessionId())
            .text(response.getResponse());

        if (response.getSkillsApplied() != null) {
            builder.metadata("skillsApplied", response.getSkillsApplied());
        }
        if (response.getMetadata() != null) {
            builder.metadata(response.getMetadata());
        }

        return builder.build();
    }
}
