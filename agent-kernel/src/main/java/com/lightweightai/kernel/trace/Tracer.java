package com.lightweightai.kernel.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Trace 中央协调器。
 *
 * 持有 SpanExporter 列表，负责创建 trace 和导出 span。
 * 未配置时使用 NOOP 单例，所有操作均安全无副作用。
 *
 * 增强 (Harness Engineering):
 * - startChildSpan: 直接创建子 span
 * - recordTokenUsage: 记录 LLM token 用量到 span
 * - recordToolLatency: 记录工具调用延迟到 span
 */
public final class Tracer {

    /**
     * 空操作 Tracer，未配置可观测性时使用
     */
    public static final Tracer NOOP = new Tracer(Collections.emptyList());

    private final List<SpanExporter> exporters;

    private Tracer(List<SpanExporter> exporters) {
        this.exporters = Collections.unmodifiableList(exporters);
    }

    /**
     * 创建新的 trace 根 span
     */
    public SpanContext startTrace(String name) {
        return SpanContext.createRoot(name);
    }

    /**
     * 创建新的 trace 根 span（指定 kind）
     */
    public SpanContext startTrace(String name, SpanKind kind) {
        return SpanContext.createRoot(name, kind);
    }

    /**
     * 创建子 span
     */
    public SpanContext startChildSpan(SpanContext parent, String name) {
        if (parent == null) {
            return startTrace(name);
        }
        return parent.createChild(name);
    }

    /**
     * 创建子 span（指定 kind）
     */
    public SpanContext startChildSpan(SpanContext parent, String name, SpanKind kind) {
        if (parent == null) {
            return startTrace(name, kind);
        }
        return parent.createChild(name, kind);
    }

    /**
     * 结束 span 并导出到所有 exporter
     */
    public void endSpan(SpanContext ctx) {
        if (exporters.isEmpty() || ctx == null) return;
        Span span = ctx.end();
        doExport(span);
    }

    /**
     * 以错误结束 span 并导出
     */
    public void endSpanWithError(SpanContext ctx, Throwable t) {
        if (exporters.isEmpty() || ctx == null) return;
        Span span = ctx.endWithError(t);
        doExport(span);
    }

    // ==================== 业务追踪辅助方法 ====================

    /**
     * 记录 LLM token 用量到 span
     */
    public void recordTokenUsage(SpanContext span, int inputTokens, int outputTokens, String model) {
        if (span == null) return;
        span.setAttribute("llm.model", model);
        span.setAttribute("llm.input_tokens", inputTokens);
        span.setAttribute("llm.output_tokens", outputTokens);
        span.setAttribute("llm.total_tokens", inputTokens + outputTokens);
    }

    /**
     * 记录工具调用延迟到 span
     */
    public void recordToolLatency(SpanContext span, String toolName, long durationMs, boolean success) {
        if (span == null) return;
        span.setAttribute("tool.name", toolName);
        span.setAttribute("tool.duration_ms", durationMs);
        span.setAttribute("tool.success", success);
    }

    /**
     * 记录成本到 span
     */
    public void recordCost(SpanContext span, double costUsd) {
        if (span == null) return;
        span.setAttribute("cost.usd", costUsd);
    }

    /**
     * 检查是否有导出器（非 NOOP）
     */
    public boolean isEnabled() {
        return !exporters.isEmpty();
    }

    private void doExport(Span span) {
        for (SpanExporter exporter : exporters) {
            try {
                exporter.export(span);
            } catch (Exception ignored) {
                // exporter 异常不影响主流程
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<SpanExporter> exporters = new ArrayList<>();

        public Builder addExporter(SpanExporter exporter) {
            exporters.add(exporter);
            return this;
        }

        public Tracer build() {
            return new Tracer(new ArrayList<>(exporters));
        }
    }
}
