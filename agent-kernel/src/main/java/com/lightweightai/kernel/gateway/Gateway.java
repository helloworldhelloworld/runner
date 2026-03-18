package com.lightweightai.kernel.gateway;

import com.lightweightai.kernel.agent.AgentLoop;
import com.lightweightai.kernel.agent.AgentResponse;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.postprocess.StreamPostProcessor;
import com.lightweightai.kernel.core.postprocess.StreamPostProcessorPipeline;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Gateway - 客户端无关入口
 *
 * Gateway 是业务逻辑的协议无关封装：
 * - 支持同步/异步/流式调用
 * - 统一的请求/响应格式
 * - 会话管理
 * - 错误处理
 *
 * 使用方式：
 * 1. 通过 ChatHandler 接入任意业务逻辑（推荐）
 * 2. 通过 AgentLoop 直接接入（向后兼容）
 * 3. 通过 HTTP REST / WebSocket / gRPC 暴露
 */
public class Gateway {

    private final ChatHandler chatHandler;
    private final SessionManager sessionManager;
    private final StreamPostProcessorPipeline postProcessorPipeline;

    private Gateway(Builder builder) {
        if (builder.chatHandler != null) {
            this.chatHandler = builder.chatHandler;
        } else if (builder.agentLoop != null) {
            this.chatHandler = new AgentLoopChatHandler(builder.agentLoop);
        } else {
            throw new IllegalArgumentException("Either chatHandler or agentLoop is required");
        }
        this.sessionManager = builder.sessionManager;
        if (!builder.postProcessors.isEmpty()) {
            this.postProcessorPipeline = StreamPostProcessorPipeline.builder()
                    .addAll(builder.postProcessors)
                    .build();
        } else {
            this.postProcessorPipeline = builder.postProcessorPipeline;
        }
    }

    // ==================== 同步调用 ====================

    /**
     * 同步处理请求
     */
    public GatewayResponse handle(GatewayRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            GatewayResponse response = chatHandler.chat(request);

            // 补充 latency（如果 ChatHandler 未设置）
            if (response.getLatencyMs() == 0) {
                long latencyMs = System.currentTimeMillis() - startTime;
                return GatewayResponse.builder()
                    .requestId(response.getRequestId() != null ? response.getRequestId() : request.getRequestId())
                    .sessionId(response.getSessionId() != null ? response.getSessionId() : request.getSessionId())
                    .text(response.getText())
                    .error(response.isError())
                    .errorMessage(response.getErrorMessage())
                    .latencyMs(latencyMs)
                    .metadata(response.getMetadata())
                    .build();
            }
            return response;
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

        chatHandler.chatStream(request, new ChatHandler.StreamCallback() {
            @Override
            public void onDelta(String delta, Map<String, Object> metadata) {
                handler.onDelta(delta, metadata);
            }

            @Override
            public void onComplete(GatewayResponse response) {
                if (response.getLatencyMs() == 0) {
                    long latencyMs = System.currentTimeMillis() - startTime;
                    GatewayResponse enriched = GatewayResponse.builder()
                        .requestId(response.getRequestId() != null ? response.getRequestId() : request.getRequestId())
                        .sessionId(response.getSessionId() != null ? response.getSessionId() : request.getSessionId())
                        .text(response.getText())
                        .latencyMs(latencyMs)
                        .metadata(response.getMetadata())
                        .build();
                    handler.onComplete(enriched);
                } else {
                    handler.onComplete(response);
                }
            }

            @Override
            public void onError(Throwable error) {
                handler.onError(error);
            }
        }).exceptionally(error -> {
            handler.onError(error);
            return null;
        });
    }

    // ==================== Reactive 流式调用 ====================

    /**
     * Reactive 流式处理请求，返回统一的 Flux&lt;StreamEvent&gt;
     *
     * 包含 LLM 文本片段、工具调用进度、工具结果等全链路事件。
     */
    public Flux<StreamEvent> handleStreamReactive(GatewayRequest request) {
        Flux<StreamEvent> stream = chatHandler.chatStreamReactive(request);
        if (postProcessorPipeline != null) {
            stream = stream.transform(postProcessorPipeline);
        }
        return stream;
    }

    /**
     * 获取后处理器管道（可能为 null）
     */
    public StreamPostProcessorPipeline getPostProcessorPipeline() {
        return postProcessorPipeline;
    }

    // ==================== 会话管理 ====================

    /**
     * 获取 SessionManager（可能为 null）
     */
    public SessionManager getSessionManager() {
        return sessionManager;
    }

    /**
     * 获取 ChatHandler
     */
    public ChatHandler getChatHandler() {
        return chatHandler;
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ChatHandler chatHandler;
        private AgentLoop agentLoop;
        private SessionManager sessionManager;
        private StreamPostProcessorPipeline postProcessorPipeline;
        private final List<StreamPostProcessor> postProcessors = new ArrayList<>();

        public Builder chatHandler(ChatHandler chatHandler) {
            this.chatHandler = chatHandler;
            return this;
        }

        public Builder agentLoop(AgentLoop agentLoop) {
            this.agentLoop = agentLoop;
            return this;
        }

        public Builder sessionManager(SessionManager sessionManager) {
            this.sessionManager = sessionManager;
            return this;
        }

        /**
         * 添加单个后处理器（与其他处理器按 order 排序）
         */
        public Builder addPostProcessor(StreamPostProcessor processor) {
            this.postProcessors.add(processor);
            return this;
        }

        /**
         * 设置完整的后处理器管道（与 addPostProcessor 互斥，后者优先）
         */
        public Builder postProcessors(StreamPostProcessorPipeline pipeline) {
            this.postProcessorPipeline = pipeline;
            return this;
        }

        public Gateway build() {
            return new Gateway(this);
        }
    }

    // ==================== AgentLoop 适配器（向后兼容） ====================

    /**
     * 将 AgentLoop 包装为 ChatHandler，保持向后兼容。
     */
    static class AgentLoopChatHandler implements ChatHandler {

        private final AgentLoop agentLoop;

        AgentLoopChatHandler(AgentLoop agentLoop) {
            this.agentLoop = Objects.requireNonNull(agentLoop, "AgentLoop required");
        }

        @Override
        public GatewayResponse chat(GatewayRequest request) {
            AgentResponse agentResponse = agentLoop.run(
                request.getMessage(),
                request.getSessionId()
            );

            return GatewayResponse.fromAgentResponse(
                agentResponse,
                request.getRequestId(),
                request.getSessionId(),
                0 // latency 由 Gateway 补充
            );
        }

        @Override
        public CompletableFuture<GatewayResponse> chatStream(GatewayRequest request, StreamCallback callback) {
            StringBuilder fullText = new StringBuilder();

            return agentLoop.runStream(
                request.getMessage(),
                request.getSessionId(),
                delta -> {
                    fullText.append(delta);
                    callback.onDelta(delta, Collections.emptyMap());
                }
            ).thenApply(agentResponse -> {
                GatewayResponse response = GatewayResponse.builder()
                    .requestId(request.getRequestId())
                    .sessionId(request.getSessionId())
                    .text(fullText.toString())
                    .build();
                callback.onComplete(response);
                return response;
            });
        }
    }
}
