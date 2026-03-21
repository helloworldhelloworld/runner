package com.lightweightai.web.postprocess;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.postprocess.StreamPostProcessor;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 调用链追踪后处理器
 *
 * 以 PostProcessor 方式注入 TRACE 事件到流中，不改变上游订阅模型。
 * 在关键事件（首个 token、工具调用、LLM 完成）前注入 TRACE 事件。
 *
 * 运行在 PostProcessor pipeline 的最后（order=1000），
 * 观察经过风控、deeplink 等处理后的最终事件流。
 */
public class TracingPostProcessor implements StreamPostProcessor {

    @Override
    public String getName() {
        return "tracing";
    }

    @Override
    public int getOrder() {
        return 1000; // 最后执行，观察完整的事件流
    }

    @Override
    public Flux<StreamEvent> apply(Flux<StreamEvent> source) {
        AtomicBoolean firstToken = new AtomicBoolean(false);
        AtomicLong subscribeTime = new AtomicLong(0);

        return source
            .doOnSubscribe(s -> subscribeTime.set(System.currentTimeMillis()))
            .concatMap(event -> {
                List<StreamEvent> events = new ArrayList<>(2);

                switch (event.getType()) {
                    case TEXT_DELTA -> {
                        if (firstToken.compareAndSet(false, true)) {
                            long elapsed = System.currentTimeMillis() - subscribeTime.get();
                            events.add(StreamEvent.trace("llm.first_token",
                                "First token received, latency=" + elapsed + "ms"));
                        }
                    }
                    case TOOL_CALL_START -> {
                        String toolName = event.getToolCall() != null
                            ? event.getToolCall().getName() : "unknown";
                        events.add(StreamEvent.trace("tool.start",
                            "Tool call: " + toolName));
                    }
                    case TOOL_RESULT -> {
                        String toolName = event.getChunk() != null
                            ? event.getChunk().getToolName() : "unknown";
                        events.add(StreamEvent.trace("tool.result",
                            "Tool completed: " + toolName));
                    }
                    case TOOL_ERROR -> {
                        String toolName = event.getChunk() != null
                            ? event.getChunk().getToolName() : "unknown";
                        events.add(StreamEvent.trace("tool.error",
                            "Tool failed: " + toolName));
                    }
                    case LLM_COMPLETE -> {
                        boolean hasToolCalls = event.getResponse() != null
                            && event.getResponse().hasToolCalls();
                        long elapsed = System.currentTimeMillis() - subscribeTime.get();
                        events.add(StreamEvent.trace("llm.complete",
                            "LLM complete, hasToolCalls=" + hasToolCalls
                                + ", elapsed=" + elapsed + "ms"));
                    }
                    case ERROR -> {
                        String msg = event.getError() != null
                            ? event.getError().getMessage() : "unknown";
                        events.add(StreamEvent.trace("error",
                            "Pipeline error: " + msg));
                    }
                    default -> {} // TEXT_DELTA(非首个), TOOL_PROGRESS, TOOL_LOG, POST_PROCESS_DATA 不注入
                }

                events.add(event); // 原始事件始终保留
                return Flux.fromIterable(events);
            });
    }
}
