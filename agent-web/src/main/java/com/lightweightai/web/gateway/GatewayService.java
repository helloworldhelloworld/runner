package com.lightweightai.web.gateway;

import com.lightweightai.kernel.gateway.Gateway;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.gateway.GatewayResponse;
import com.lightweightai.kernel.gateway.GatewayStreamHandler;
import com.lightweightai.web.model.ChatRequest;
import com.lightweightai.web.model.ChatResponse;
import com.lightweightai.web.service.SoulComfortChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Gateway 服务层 - 统一入口
 *
 * 适配 kernel-gateway 到 web 层：
 * - 将 HTTP 请求转换为 GatewayRequest
 * - 将 GatewayResponse 转换为 ChatResponse
 * - 支持多种 Agent 模式路由
 */
@Service
public class GatewayService {

    private static final Logger logger = LoggerFactory.getLogger(GatewayService.class);

    private final SoulComfortChatService soulComfortService;
    private final Gateway gateway; // 可选的标准 Gateway

    @org.springframework.beans.factory.annotation.Autowired
    public GatewayService(SoulComfortChatService soulComfortService) {
        this.soulComfortService = soulComfortService;
        this.gateway = null; // 暂时使用 SoulComfortService
    }

    /**
     * 处理聊天请求（同步）
     */
    public ChatResponse chat(ChatRequest request) {
        String mode = determineMode(request);
        logger.info("Gateway chat - mode: {}, session: {}", mode, request.getSessionId());

        switch (mode) {
            case "soul-comfort":
                return soulComfortService.chat(request);

            case "gateway":
                return handleViaGateway(request);

            default:
                return soulComfortService.chat(request);
        }
    }

    /**
     * 处理聊天请求（流式）
     *
     * 修复：链式 thenAccept 在 future 完成后调 callback.onComplete()，
     * 让 GatewayController 可以发送 COMPLETE 事件并关闭 SSE emitter。
     * 空 delta（SoulComfortChatService 的内部完成信号）被过滤，不转发给客户端。
     */
    public CompletableFuture<String> chatStream(
            ChatRequest request,
            StreamCallback callback) {

        String mode = determineMode(request);
        String sessionId = request.getSessionId() != null ? request.getSessionId() : "default";

        logger.info("Gateway stream - mode: {}, session: {}", mode, sessionId);

        switch (mode) {
            case "soul-comfort": {
                CompletableFuture<String> future = soulComfortService.chat(
                    request.getMessage(),
                    sessionId,
                    chunk -> {
                        if (!chunk.getDelta().isEmpty()) {
                            callback.onDelta(chunk.getDelta(), chunk.getEmotion());
                        }
                    }
                );
                future.thenAccept(fullText -> {
                    ChatResponse cr = new ChatResponse();
                    cr.setResponse(fullText);
                    cr.setSkillsApplied(Arrays.asList("soul-comfort", "openclaw-memory"));
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("mode", "soul-comfort");
                    meta.put("hasMemory", true);
                    cr.setMetadata(meta);
                    callback.onComplete(cr);
                }).exceptionally(e -> {
                    callback.onError(e);
                    return null;
                });
                return future;
            }

            case "gateway":
                return handleStreamViaGateway(request, callback);

            default: {
                CompletableFuture<String> future = soulComfortService.chat(
                    request.getMessage(),
                    sessionId,
                    chunk -> {
                        if (!chunk.getDelta().isEmpty()) {
                            callback.onDelta(chunk.getDelta(), chunk.getEmotion());
                        }
                    }
                );
                future.thenAccept(fullText -> {
                    ChatResponse cr = new ChatResponse();
                    cr.setResponse(fullText);
                    callback.onComplete(cr);
                }).exceptionally(e -> {
                    callback.onError(e);
                    return null;
                });
                return future;
            }
        }
    }

    /**
     * 获取会话历史（委托给 SoulComfortChatService）
     */
    public List<Map<String, String>> getSessionHistory(String sessionId) {
        return soulComfortService.getSessionHistory(sessionId);
    }

    /**
     * 获取会话摘要（委托给 SoulComfortChatService）
     */
    public Map<String, Object> getSessionSummary(String sessionId) {
        return soulComfortService.getSessionSummary(sessionId);
    }

    /**
     * 清空会话（委托给 SoulComfortChatService）
     */
    public void clearSession(String sessionId) {
        soulComfortService.clearSession(sessionId);
    }

    /**
     * 流式回调接口
     */
    public interface StreamCallback {
        void onDelta(String delta, String emotion);
        void onComplete(ChatResponse response);
        void onError(Throwable error);
    }

    // ==================== 私有方法 ====================

    /**
     * 确定请求处理模式
     */
    private String determineMode(ChatRequest request) {
        // 优先使用请求中指定的模式
        if (request.isSoulComfortMode()) {
            return "soul-comfort";
        }

        // 如果有标准 Gateway 配置，使用 gateway 模式
        if (gateway != null) {
            return "gateway";
        }

        // 默认使用 soul-comfort
        return "soul-comfort";
    }

    /**
     * 通过标准 Gateway 处理请求
     */
    private ChatResponse handleViaGateway(ChatRequest request) {
        if (gateway == null) {
            return soulComfortService.chat(request);
        }

        GatewayRequest gatewayRequest = GatewayRequest.builder()
            .sessionId(request.getSessionId())
            .message(request.getMessage())
            .metadata("model", request.getModel())
            .build();

        GatewayResponse gatewayResponse = gateway.handle(gatewayRequest);

        return convertToChaChatResponse(gatewayResponse);
    }

    /**
     * 通过标准 Gateway 处理流式请求
     */
    private CompletableFuture<String> handleStreamViaGateway(
            ChatRequest request,
            StreamCallback callback) {

        if (gateway == null) {
            return soulComfortService.chat(
                request.getMessage(),
                request.getSessionId(),
                chunk -> callback.onDelta(chunk.getDelta(), chunk.getEmotion())
            );
        }

        GatewayRequest gatewayRequest = GatewayRequest.builder()
            .sessionId(request.getSessionId())
            .message(request.getMessage())
            .build();

        CompletableFuture<String> future = new CompletableFuture<>();

        gateway.handleStream(gatewayRequest, new GatewayStreamHandler() {
            @Override
            public void onDelta(String delta) {
                callback.onDelta(delta, "");
            }

            @Override
            public void onComplete(GatewayResponse response) {
                callback.onComplete(convertToChaChatResponse(response));
                future.complete(response.getText());
            }

            @Override
            public void onError(Throwable error) {
                callback.onError(error);
                future.completeExceptionally(error);
            }
        });

        return future;
    }

    /**
     * 转换 GatewayResponse 到 ChatResponse
     */
    private ChatResponse convertToChaChatResponse(GatewayResponse gatewayResponse) {
        ChatResponse response = new ChatResponse();

        if (gatewayResponse.isError()) {
            return ChatResponse.error(gatewayResponse.getErrorMessage());
        }

        response.setResponse(gatewayResponse.getText());
        response.setSkillsApplied(Collections.singletonList("gateway"));

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("requestId", gatewayResponse.getRequestId());
        metadata.put("latencyMs", gatewayResponse.getLatencyMs());
        metadata.put("mode", "gateway");
        response.setMetadata(metadata);

        return response;
    }
}
