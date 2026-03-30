package com.lightweightai.kernel.task;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.gateway.ChatHandler;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.gateway.GatewayResponse;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * ChatHandler 装饰器 — 在 AgentLoop 前后插入 TaskGraph 执行
 *
 * 前置任务：意图分类、安全检查、RAG 检索等。结果注入 GatewayRequest metadata。
 * 后置任务：引用提取、质量检查、分析记录等。
 */
public class TaskOrchestratingChatHandler implements ChatHandler {

    private final ChatHandler delegate;
    private final TaskGraph preProcessGraph;
    private final TaskGraph postProcessGraph;

    private TaskOrchestratingChatHandler(Builder builder) {
        this.delegate = builder.delegate;
        this.preProcessGraph = builder.preProcessGraph;
        this.postProcessGraph = builder.postProcessGraph;
    }

    @Override
    public GatewayResponse chat(GatewayRequest request) {
        TaskContext taskContext = buildTaskContext(request);

        if (preProcessGraph != null) {
            preProcessGraph.execute(taskContext).blockLast();
        }

        GatewayRequest enrichedRequest = enrichRequest(request, taskContext);
        GatewayResponse response = delegate.chat(enrichedRequest);

        if (postProcessGraph != null) {
            taskContext.setAttribute("agentResponse", response.getText());
            postProcessGraph.execute(taskContext).blockLast();
        }

        return response;
    }

    @Override
    public CompletableFuture<GatewayResponse> chatStream(
            GatewayRequest request, StreamCallback callback) {
        return chatStreamReactive(request)
                .collectList()
                .map(events -> {
                    StringBuilder text = new StringBuilder();
                    events.forEach(e -> {
                        if (e.getType() == StreamEvent.EventType.TEXT_DELTA) {
                            String delta = e.getTextDelta();
                            text.append(delta);
                            callback.onDelta(delta, Map.of());
                        }
                    });
                    GatewayResponse resp = GatewayResponse.builder()
                            .requestId(request.getRequestId())
                            .sessionId(request.getSessionId())
                            .text(text.toString())
                            .build();
                    callback.onComplete(resp);
                    return resp;
                })
                .toFuture();
    }

    @Override
    public Flux<StreamEvent> chatStreamReactive(GatewayRequest request) {
        TaskContext taskContext = buildTaskContext(request);

        Flux<StreamEvent> preEvents = preProcessGraph != null
                ? preProcessGraph.execute(taskContext)
                : Flux.empty();

        Flux<StreamEvent> agentEvents = Flux.defer(() -> {
            GatewayRequest enriched = enrichRequest(request, taskContext);
            return delegate.chatStreamReactive(enriched);
        });

        Flux<StreamEvent> postEvents = Flux.defer(() -> {
            if (postProcessGraph == null) return Flux.empty();
            return postProcessGraph.execute(taskContext);
        });

        return Flux.concat(preEvents, agentEvents, postEvents);
    }

    private TaskContext buildTaskContext(GatewayRequest request) {
        return TaskContext.builder()
                .sessionId(request.getSessionId())
                .requestId(request.getRequestId())
                .userInput(request.getMessage())
                .build();
    }

    private GatewayRequest enrichRequest(GatewayRequest original, TaskContext taskContext) {
        GatewayRequest.Builder builder = GatewayRequest.builder()
                .requestId(original.getRequestId())
                .sessionId(original.getSessionId())
                .message(original.getMessage())
                .metadata(original.getMetadata());

        // 注入所有任务结果到 metadata
        taskContext.getAllResults().forEach((taskName, result) ->
                builder.metadata("task:" + taskName, result));

        // 注入任务属性
        taskContext.getAttributes().forEach(builder::metadata);

        return builder.build();
    }

    // ==================== Builder ====================

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private ChatHandler delegate;
        private TaskGraph preProcessGraph;
        private TaskGraph postProcessGraph;

        public Builder delegate(ChatHandler handler) {
            this.delegate = handler;
            return this;
        }

        public Builder preProcess(TaskGraph graph) {
            this.preProcessGraph = graph;
            return this;
        }

        public Builder postProcess(TaskGraph graph) {
            this.postProcessGraph = graph;
            return this;
        }

        public TaskOrchestratingChatHandler build() {
            if (delegate == null) throw new IllegalArgumentException("delegate ChatHandler is required");
            return new TaskOrchestratingChatHandler(this);
        }
    }
}
