package com.lightweightai.kernel.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Trace 中央协调器。
 *
 * 持有 SpanExporter 列表，负责创建 trace 和导出 span。
 * 未配置时使用 NOOP 单例，所有操作均安全无副作用。
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
     * 结束 span 并导出到所有 exporter
     */
    public void endSpan(SpanContext ctx) {
        if (exporters.isEmpty()) return;
        Span span = ctx.end();
        doExport(span);
    }

    /**
     * 以错误结束 span 并导出
     */
    public void endSpanWithError(SpanContext ctx, Throwable t) {
        if (exporters.isEmpty()) return;
        Span span = ctx.endWithError(t);
        doExport(span);
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
