package com.lightweightai.kernel.gateway;

import com.lightweightai.kernel.agent.AgentLoop;
import com.lightweightai.kernel.agent.AgentResponse;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.postprocess.StreamPostProcessor;
import com.lightweightai.kernel.core.postprocess.StreamPostProcessorPipeline;
import com.lightweightai.kernel.harness.AgentHarness;
import com.lightweightai.kernel.harness.HarnessContext;
import com.lightweightai.kernel.trace.SpanContext;
import com.lightweightai.kernel.trace.TraceContext;
import com.lightweightai.kernel.trace.Tracer;
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
    private final Tracer tracer;
    private final AgentHarness harness;

    private Gateway(Builder builder) {
        if (builder.chatHandler != null) {
            this.chatHandler = builder.chatHandler;
        } else if (builder.agentLoop != null) {
            this.chatHandler = new AgentLoopChatHandler(builder.agentLoop);
        } else {
            throw new IllegalArgumentException("Either chatHandler or agentLoop is required");
        }
        this.sessionManager = builder.sessionManager;
        this.harness = builder.harness;
        this.tracer = builder.tracer != null ? builder.tracer
            : (harness != null ? harness.getTracer() : Tracer.NOOP);
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
     *
     * 内部统一走 Reactive 路径（handleStreamReactive），将 Flux&lt;StreamEvent&gt;
     * 分发到 GatewayStreamHandler 的各回调方法。
     *
     * 这样无论哪种协议适配器（REST/SSE/WebSocket），都能收到完整的工具调用事件。
     * 旧适配器未覆盖工具回调 → 默认空实现 → 行为不变。
     */
    public void handleStream(GatewayRequest request, GatewayStreamHandler handler) {
        long startTime = System.currentTimeMillis();
        StringBuilder fullText = new StringBuilder();

        handleStreamReactive(request)
            .subscribe(
                event -> dispatchEvent(event, handler, fullText),
                handler::onError,
                () -> {
                    long latencyMs = System.currentTimeMillis() - startTime;
                    GatewayResponse response = GatewayResponse.builder()
                        .requestId(request.getRequestId())
                        .sessionId(request.getSessionId())
                        .text(fullText.toString())
                        .latencyMs(latencyMs)
                        .build();
                    handler.onComplete(response);
                }
            );
    }

    /**
     * 将 StreamEvent 分发到 GatewayStreamHandler 对应的回调
     */
    private void dispatchEvent(StreamEvent event, GatewayStreamHandler handler, StringBuilder fullText) {
        switch (event.getType()) {
            case TEXT_DELTA -> {
                String delta = event.getTextDelta();
                if (delta != null && !delta.isEmpty()) {
                    fullText.append(delta);
                    handler.onDelta(delta, event.getMetadata());
                }
            }
            case TOOL_CALL_START -> {
                if (event.getToolCall() != null) {
                    handler.onToolCallStart(event.getToolCall());
                }
            }
            case TOOL_PROGRESS -> {
                if (event.getChunk() != null) {
                    handler.onToolProgress(event.getChunk());
                }
            }
            case TOOL_LOG -> {
                if (event.getChunk() != null) {
                    handler.onToolLog(event.getChunk());
                }
            }
            case TOOL_RESULT -> {
                if (event.getChunk() != null) {
                    handler.onToolResult(event.getChunk());
                }
            }
            case TOOL_ERROR -> {
                if (event.getChunk() != null) {
                    handler.onToolError(event.getChunk());
                }
            }
            case POST_PROCESS_DATA -> {
                if (event.getData() != null) {
                    handler.onPostProcessData(event.getCategory(), event.getData());
                }
            }
            case LLM_COMPLETE -> {
                // LLM 完成但可能还有后续工具调用轮次
                // 最终 onComplete 由 Flux.onComplete 触发
            }
            case ERROR -> {
                if (event.getError() != null) {
                    handler.onError(event.getError());
                }
            }
        }
    }

    // ==================== Reactive 流式调用 ====================

    /**
     * Reactive 流式处理请求，返回统一的 Flux&lt;StreamEvent&gt;
     *
     * 包含 LLM 文本片段、工具调用进度、工具结果等全链路事件。
     */
    public Flux<StreamEvent> handleStreamReactive(GatewayRequest request) {
        // 通过 Harness 创建 HarnessContext（含 root span + baggage + trace 传播）
        // 如果无 harness 则 fallback 到直接创建 span
        final SpanContext rootSpan;
        final HarnessContext harnessCtx;

        if (harness != null) {
            Map<String, String> headers = null;
            if (request.getMetadata() != null) {
                // 从请求 metadata 中尝试提取 HTTP headers (用于 W3C traceparent 传播)
                Object rawHeaders = request.getMetadata().get("headers");
                if (rawHeaders instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> h = (Map<String, String>) rawHeaders;
                    headers = h;
                }
            }
            harnessCtx = harness.createContext(
                request.getSessionId(), request.getRequestId(), headers);
            rootSpan = harnessCtx.getRootSpan();
        } else {
            harnessCtx = null;
            rootSpan = tracer.startTrace("gateway.handle");
            rootSpan.setAttribute("sessionId", request.getSessionId());
            rootSpan.setAttribute("requestId", request.getRequestId());
        }

        // 在流头部注入用户消息 trace
        StreamEvent userTrace = StreamEvent.trace("user.message", request.getMessage(),
                Map.of("sessionId", request.getSessionId(),
                       "requestId", request.getRequestId()));
        Flux<StreamEvent> stream = Flux.concat(
                Flux.just(userTrace),
                chatHandler.chatStreamReactive(request)
        );
        if (postProcessorPipeline != null) {
            stream = stream.transform(postProcessorPipeline);
        }

        stream = stream
            .doOnComplete(() -> tracer.endSpan(rootSpan))
            .doOnError(e -> tracer.endSpanWithError(rootSpan, e))
            .contextWrite(TraceContext.put(rootSpan));

        // 如果有 HarnessContext，绑定到 Reactor Context
        if (harnessCtx != null) {
            stream = stream.contextWrite(harnessCtx.toReactorContext());
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
        private Tracer tracer;
        private AgentHarness harness;

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

        public Builder tracer(Tracer tracer) {
            this.tracer = tracer;
            return this;
        }

        /**
         * 设置 AgentHarness（推荐，提供完整的 Harness Engineering 能力）
         */
        public Builder harness(AgentHarness harness) {
            this.harness = harness;
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
