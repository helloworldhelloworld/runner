package com.lightweightai.kernel.trace.export;

import com.lightweightai.kernel.trace.Span;
import com.lightweightai.kernel.trace.SpanExporter;
import com.lightweightai.kernel.trace.SpanStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 控制台 Span 导出器 — 通过 SLF4J 输出结构化日志。
 *
 * 支持两种输出模式：
 * 1. 默认文本模式：[TRACE] name traceId=X spanId=Y duration=Zms status
 * 2. JSON 模式：{"name":"...", "traceId":"...", ...}（配合 logstash-logback-encoder 使用）
 */
public class ConsoleSpanExporter implements SpanExporter {

    private static final Logger logger = LoggerFactory.getLogger("kernel.trace");

    private final boolean jsonMode;

    public ConsoleSpanExporter() {
        this(false);
    }

    public ConsoleSpanExporter(boolean jsonMode) {
        this.jsonMode = jsonMode;
    }

    @Override
    public void export(Span span) {
        if (jsonMode) {
            exportJson(span);
        } else {
            exportText(span);
        }
    }

    private void exportText(Span span) {
        if (span.getStatus() == SpanStatus.ERROR) {
            logger.warn("[TRACE] {} kind={} traceId={} spanId={} parent={} duration={}ms ERROR error=\"{}\" {}",
                span.getName(),
                span.getKind(),
                span.getTraceId(),
                span.getSpanId(),
                span.getParentSpanId() != null ? span.getParentSpanId() : "root",
                span.getDurationMs(),
                span.getErrorMessage(),
                span.getAttributes());
        } else {
            logger.info("[TRACE] {} kind={} traceId={} spanId={} parent={} duration={}ms OK {}",
                span.getName(),
                span.getKind(),
                span.getTraceId(),
                span.getSpanId(),
                span.getParentSpanId() != null ? span.getParentSpanId() : "root",
                span.getDurationMs(),
                span.getAttributes());
        }
    }

    private void exportJson(Span span) {
        // JSON 结构化输出（SLF4J 的结构化参数，配合 logstash-logback-encoder）
        if (span.getStatus() == SpanStatus.ERROR) {
            logger.warn("{{\"type\":\"span\",\"name\":\"{}\",\"kind\":\"{}\",\"traceId\":\"{}\",\"spanId\":\"{}\",\"parentSpanId\":\"{}\",\"durationMs\":{},\"status\":\"ERROR\",\"error\":\"{}\",\"attributes\":{}}}",
                span.getName(), span.getKind(),
                span.getTraceId(), span.getSpanId(),
                span.getParentSpanId() != null ? span.getParentSpanId() : "",
                span.getDurationMs(), span.getErrorMessage(),
                span.getAttributes());
        } else {
            logger.info("{{\"type\":\"span\",\"name\":\"{}\",\"kind\":\"{}\",\"traceId\":\"{}\",\"spanId\":\"{}\",\"parentSpanId\":\"{}\",\"durationMs\":{},\"status\":\"OK\",\"attributes\":{}}}",
                span.getName(), span.getKind(),
                span.getTraceId(), span.getSpanId(),
                span.getParentSpanId() != null ? span.getParentSpanId() : "",
                span.getDurationMs(), span.getAttributes());
        }
    }

    @Override
    public String getName() {
        return jsonMode ? "ConsoleSpanExporter(json)" : "ConsoleSpanExporter";
    }
}
