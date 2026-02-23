package com.lightweightai.kernel.gateway;

import com.lightweightai.kernel.agent.AgentLoop;
import com.lightweightai.kernel.agent.AgentResponse;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Gateway - 客户端无关入口
 *
 * Gateway 是 AgentLoop 的协议无关封装：
 * - 支持同步/异步/流式调用
 * - 统一的请求/响应格式
 * - 会话管理
 * - 错误处理
 *
 * 使用方式：
 * 1. 直接嵌入应用（本地调用）
 * 2. 通过 HTTP REST 暴露
 * 3. 通过 WebSocket 暴露
 * 4. 通过 gRPC 暴露
 */
public class Gateway {

    private final AgentLoop agentLoop;

    private Gateway(Builder builder) {
        this.agentLoop = Objects.requireNonNull(builder.agentLoop, "AgentLoop required");
    }

    // ==================== 同步调用 ====================

    /**
     * 同步处理请求
     */
    public GatewayResponse handle(GatewayRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            AgentResponse agentResponse = agentLoop.run(
                request.getMessage(),
                request.getSessionId()
            );

            long latencyMs = System.currentTimeMillis() - startTime;

            return GatewayResponse.fromAgentResponse(
                agentResponse,
                request.getRequestId(),
                request.getSessionId(),
                latencyMs
            );
        } catch (Exception e) {
            return GatewayResponse.error(
                request.getRequestId(),
                request.getSessionId(),
                e.getMessage()
            );
        }
    }

    // ==================== 异步调用 ====================

    /**
     * 异步处理请求
     */
    public CompletableFuture<GatewayResponse> handleAsync(GatewayRequest request) {
        return CompletableFuture.supplyAsync(() -> handle(request));
    }

    // ==================== 流式调用 ====================

    /**
     * 流式处理请求
     */
    public void handleStream(GatewayRequest request, GatewayStreamHandler handler) {
        long startTime = System.currentTimeMillis();

        StringBuilder fullText = new StringBuilder();

        agentLoop.runStream(
            request.getMessage(),
            request.getSessionId(),
            delta -> {
                fullText.append(delta);
                handler.onDelta(delta);
            }
        ).thenAccept(agentResponse -> {
            long latencyMs = System.currentTimeMillis() - startTime;
            GatewayResponse response = GatewayResponse.builder()
                .requestId(request.getRequestId())
                .sessionId(request.getSessionId())
                .text(fullText.toString())
                .latencyMs(latencyMs)
                .build();
            handler.onComplete(response);
        }).exceptionally(error -> {
            handler.onError(error);
            return null;
        });
    }

    // ==================== 会话管理 ====================

    /**
     * 获取 AgentLoop（用于高级操作）
     */
    public AgentLoop getAgentLoop() {
        return agentLoop;
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private AgentLoop agentLoop;

        public Builder agentLoop(AgentLoop agentLoop) {
            this.agentLoop = agentLoop;
            return this;
        }

        public Gateway build() {
            return new Gateway(this);
        }
    }
}
