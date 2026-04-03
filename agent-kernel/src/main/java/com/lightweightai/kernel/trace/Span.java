package com.lightweightai.kernel.trace;

import java.util.Collections;
import java.util.Map;

/**
 * 不可变的 Span 值对象，表示一个已完成的追踪记录。
 *
 * 增强 (Harness Engineering):
 * - kind: Span 类型 (SERVER/CLIENT/INTERNAL)
 * - baggage: 业务级上下文 (userId, costBudget 等)
 */
public final class Span {

    private final String traceId;
    private final String spanId;
    private final String parentSpanId;
    private final String name;
    private final SpanKind kind;
    private final long startTimeMs;
    private final long endTimeMs;
    private final Map<String, Object> attributes;
    private final Map<String, String> baggage;
    private final SpanStatus status;
    private final String errorMessage;

    Span(String traceId, String spanId, String parentSpanId, String name, SpanKind kind,
         long startTimeMs, long endTimeMs,
         Map<String, Object> attributes, Map<String, String> baggage,
         SpanStatus status, String errorMessage) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.name = name;
        this.kind = kind != null ? kind : SpanKind.INTERNAL;
        this.startTimeMs = startTimeMs;
        this.endTimeMs = endTimeMs;
        this.attributes = Collections.unmodifiableMap(attributes);
        this.baggage = baggage != null ? Collections.unmodifiableMap(baggage) : Collections.emptyMap();
        this.status = status;
        this.errorMessage = errorMessage;
    }

    /**
     * 向后兼容构造（无 kind/baggage）
     */
    Span(String traceId, String spanId, String parentSpanId, String name,
         long startTimeMs, long endTimeMs,
         Map<String, Object> attributes, SpanStatus status, String errorMessage) {
        this(traceId, spanId, parentSpanId, name, SpanKind.INTERNAL,
             startTimeMs, endTimeMs, attributes, Collections.emptyMap(),
             status, errorMessage);
    }

    public String getTraceId() { return traceId; }
    public String getSpanId() { return spanId; }
    public String getParentSpanId() { return parentSpanId; }
    public String getName() { return name; }
    public SpanKind getKind() { return kind; }
    public long getStartTimeMs() { return startTimeMs; }
    public long getEndTimeMs() { return endTimeMs; }
    public long getDurationMs() { return endTimeMs - startTimeMs; }
    public Map<String, Object> getAttributes() { return attributes; }
    public Map<String, String> getBaggage() { return baggage; }
    public SpanStatus getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }

    @Override
    public String toString() {
        return "Span{" +
            "name='" + name + '\'' +
            ", kind=" + kind +
            ", traceId='" + traceId + '\'' +
            ", spanId='" + spanId + '\'' +
            ", parent='" + (parentSpanId != null ? parentSpanId : "root") + '\'' +
            ", duration=" + getDurationMs() + "ms" +
            ", status=" + status +
            ", attributes=" + attributes +
            '}';
    }
}
