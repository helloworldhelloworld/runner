package com.lightweightai.web.postprocess;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.core.postprocess.StreamPostProcessor;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 调用链追踪后处理器
 *
 * 以 PostProcessor 方式注入 TRACE 事件到流中，不改变上游订阅模型。
 * 在关键事件前注入 TRACE 事件，并在 data（extra）字段中携带实际内容：
 * - user.message: 用户输入（由 Gateway 注入，直接透传）
 * - llm.first_token: 首 token 延迟
 * - tool.start: 工具名 + 调用参数（模型输入）
 * - tool.result: 工具名 + 返回内容
 * - tool.error: 工具名 + 错误信息
 * - llm.complete: 模型输出文本 + token 用量
 * - error: 管道错误信息
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
        AtomicBoolean firstChunk = new AtomicBoolean(false);
        AtomicLong subscribeTime = new AtomicLong(0);
        StringBuilder outputAccumulator = new StringBuilder();

        return source
            .doOnSubscribe(s -> subscribeTime.set(System.currentTimeMillis()))
            .concatMap(event -> {
                List<StreamEvent> events = new ArrayList<>(2);

                switch (event.getType()) {
                    case TEXT_DELTA -> {
                        if (event.getTextDelta() != null) {
                            outputAccumulator.append(event.getTextDelta());
                        }
                        if (firstToken.compareAndSet(false, true)) {
                            long elapsed = System.currentTimeMillis() - subscribeTime.get();
                            events.add(StreamEvent.trace("llm.first_token",
                                "First token received, latency=" + elapsed + "ms",
                                Map.of("latencyMs", elapsed)));
                        }
                    }
                    // 首个可说块延迟（chunk_form）：首响关键段，与 llm.first_token 同口径（从订阅起算）。
                    // SpeakableChunkProcessor(order=100) 在本处理器(order=1000)之前注入 SPEAKABLE_CHUNK，故此处可见。
                    case SPEAKABLE_CHUNK -> {
                        if (firstChunk.compareAndSet(false, true)) {
                            long elapsed = System.currentTimeMillis() - subscribeTime.get();
                            events.add(StreamEvent.trace("speakable.first_chunk",
                                "First speakable chunk, latency=" + elapsed + "ms",
                                Map.of("latencyMs", elapsed)));
                        }
                    }
                    case TOOL_CALL_START -> {
                        ToolCall tc = event.getToolCall();
                        String toolName = tc != null ? tc.getName() : "unknown";
                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("toolName", toolName);
                        if (tc != null) {
                            data.put("toolCallId", tc.getId());
                            data.put("arguments", tc.getArguments());
                        }
                        events.add(StreamEvent.trace("tool.start",
                            "Tool call: " + toolName, data));
                    }
                    case TOOL_RESULT -> {
                        ToolResultChunk chunk = event.getChunk();
                        String toolName = chunk != null ? chunk.getToolName() : "unknown";
                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("toolName", toolName);
                        if (chunk != null && chunk.getResult() != null) {
                            ToolResult result = chunk.getResult();
                            data.put("content", result.getContent());
                            data.put("isError", result.isError());
                        }
                        events.add(StreamEvent.trace("tool.result",
                            "Tool completed: " + toolName, data));
                    }
                    case TOOL_ERROR -> {
                        ToolResultChunk chunk = event.getChunk();
                        String toolName = chunk != null ? chunk.getToolName() : "unknown";
                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("toolName", toolName);
                        if (chunk != null) {
                            data.put("errorMessage", chunk.getMessage());
                        }
                        events.add(StreamEvent.trace("tool.error",
                            "Tool failed: " + toolName, data));
                    }
                    case LLM_COMPLETE -> {
                        LLMResponse resp = event.getResponse();
                        boolean hasToolCalls = resp != null && resp.hasToolCalls();
                        long elapsed = System.currentTimeMillis() - subscribeTime.get();
                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("output", outputAccumulator.toString());
                        data.put("hasToolCalls", hasToolCalls);
                        data.put("elapsedMs", elapsed);
                        if (resp != null && resp.getUsage() != null) {
                            data.put("inputTokens", resp.getUsage().getInputTokens());
                            data.put("outputTokens", resp.getUsage().getOutputTokens());
                        }
                        if (resp != null && resp.getStopReason() != null) {
                            data.put("stopReason", resp.getStopReason());
                        }
                        events.add(StreamEvent.trace("llm.complete",
                            "LLM complete, hasToolCalls=" + hasToolCalls
                                + ", elapsed=" + elapsed + "ms", data));
                    }
                    case ERROR -> {
                        String msg = event.getError() != null
                            ? event.getError().getMessage() : "unknown";
                        events.add(StreamEvent.trace("error",
                            "Pipeline error: " + msg,
                            Map.of("errorMessage", msg)));
                    }
                    case TRACE -> {
                        // user.message 等上游 trace 直接透传，不再包装
                    }
                    default -> {} // TOOL_PROGRESS, TOOL_LOG, POST_PROCESS_DATA 不注入
                }

                events.add(event); // 原始事件始终保留
                return Flux.fromIterable(events);
            });
    }
}
