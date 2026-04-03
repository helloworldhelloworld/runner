package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.logging.StructuredLogger;
import com.lightweightai.kernel.prompt.PromptContext;
import com.lightweightai.kernel.trace.SpanContext;
import com.lightweightai.kernel.trace.SpanKind;
import com.lightweightai.kernel.trace.Tracer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 追踪 AgentObserver — 将 Agent 生命周期事件桥接到 Span (Harness Engineering)。
 *
 * 在 observer 回调中自动创建/结束 span，并记录关键指标：
 * - onAgentStart → 创建 agent.run span
 * - onLLMResponse → 记录 token usage
 * - onToolCall → 创建 tool span 并记录延迟
 * - onAgentComplete → 结束 agent.run span
 * - onError → 以错误结束 span
 */
public class TracingAgentObserver implements AgentObserver {

    private final Tracer tracer;
    private final StructuredLogger logger = StructuredLogger.getLogger(TracingAgentObserver.class);

    // 使用线程安全的 map 存储每个 session 的 span（支持并发请求）
    private final ConcurrentHashMap<String, SpanContext> activeSpans = new ConcurrentHashMap<>();
    private volatile SpanContext currentAgentSpan;
    private volatile long llmStartTimeMs;

    public TracingAgentObserver(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public void onAgentStart(String input, String sessionId) {
        SpanContext span = tracer.startTrace("agent.run", SpanKind.INTERNAL);
        span.setAttribute("sessionId", sessionId);
        span.setAttribute("input.length", input != null ? input.length() : 0);
        currentAgentSpan = span;
        activeSpans.put(sessionId, span);
    }

    @Override
    public void onLLMRequest(List<ConversationMessage> messages) {
        llmStartTimeMs = System.currentTimeMillis();
    }

    @Override
    public void onLLMResponse(LLMResponse response) {
        long latencyMs = System.currentTimeMillis() - llmStartTimeMs;
        if (response != null && currentAgentSpan != null) {
            int inputTokens = 0;
            int outputTokens = 0;
            if (response.getUsage() != null) {
                inputTokens = response.getUsage().getInputTokens();
                outputTokens = response.getUsage().getOutputTokens();
            }
            tracer.recordTokenUsage(currentAgentSpan, inputTokens, outputTokens,
                response.getModel() != null ? response.getModel() : "unknown");
            currentAgentSpan.setAttribute("llm.latency_ms", latencyMs);

            logger.llmCall("unknown", response.getModel() != null ? response.getModel() : "unknown",
                inputTokens, outputTokens, latencyMs);
        }
    }

    @Override
    public void onToolCall(String toolName, Map<String, Object> args, ToolResult result) {
        if (currentAgentSpan != null) {
            boolean success = result != null && !result.isError();
            logger.toolExec(toolName, 0, success);
        }
    }

    @Override
    public void onAgentComplete(AgentResponse response) {
        SpanContext span = currentAgentSpan;
        if (span != null) {
            if (response != null) {
                span.setAttribute("output.length",
                    response.getText() != null ? response.getText().length() : 0);
            }
            tracer.endSpan(span);
            currentAgentSpan = null;
        }
    }

    @Override
    public void onError(Throwable error) {
        SpanContext span = currentAgentSpan;
        if (span != null) {
            tracer.endSpanWithError(span, error);
            currentAgentSpan = null;
        }
    }
}
