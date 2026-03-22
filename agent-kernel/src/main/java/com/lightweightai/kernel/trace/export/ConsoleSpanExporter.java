package com.lightweightai.kernel.trace.export;

import com.lightweightai.kernel.trace.Span;
import com.lightweightai.kernel.trace.SpanExporter;
import com.lightweightai.kernel.trace.SpanStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 控制台 Span 导出器 — 通过 SLF4J 输出结构化日志。
 *
 * 替代散落的 logger.info 调用，提供统一的 trace 日志格式。
 */
public class ConsoleSpanExporter implements SpanExporter {

    private static final Logger logger = LoggerFactory.getLogger("kernel.trace");

    @Override
    public void export(Span span) {
        if (span.getStatus() == SpanStatus.ERROR) {
            logger.warn("[TRACE] {} traceId={} spanId={} parent={} duration={}ms ERROR error=\"{}\" {}",
                span.getName(),
                span.getTraceId(),
                span.getSpanId(),
                span.getParentSpanId() != null ? span.getParentSpanId() : "root",
                span.getDurationMs(),
                span.getErrorMessage(),
                span.getAttributes());
        } else {
            logger.info("[TRACE] {} traceId={} spanId={} parent={} duration={}ms OK {}",
                span.getName(),
                span.getTraceId(),
                span.getSpanId(),
                span.getParentSpanId() != null ? span.getParentSpanId() : "root",
                span.getDurationMs(),
                span.getAttributes());
        }
    }
}
