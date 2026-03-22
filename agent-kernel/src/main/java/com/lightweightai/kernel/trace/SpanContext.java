package com.lightweightai.kernel.trace;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 可变的进行中 Span 上下文。
 *
 * 在构造时生成 spanId 并记录 startTimeMs。
 * 调用 end() 或 endWithError() 冻结为不可变的 Span。
 */
public final class SpanContext {

    private final String traceId;
    private final String spanId;
    private final String parentSpanId;
    private final String name;
    private final long startTimeMs;
    private final ConcurrentHashMap<String, Object> attributes = new ConcurrentHashMap<>();

    private SpanContext(String traceId, String spanId, String parentSpanId, String name) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.name = name;
        this.startTimeMs = System.currentTimeMillis();
    }

    /**
     * 创建根 span（新的 trace）
     */
    public static SpanContext createRoot(String name) {
        String traceId = generateId();
        String spanId = generateId();
        return new SpanContext(traceId, spanId, null, name);
    }

    /**
     * 创建子 span，继承 traceId
     */
    public SpanContext createChild(String childName) {
        String childSpanId = generateId();
        return new SpanContext(this.traceId, childSpanId, this.spanId, childName);
    }

    /**
     * 设置属性
     */
    public SpanContext setAttribute(String key, Object value) {
        attributes.put(key, value);
        return this;
    }

    /**
     * 正常结束，返回不可变 Span
     */
    public Span end() {
        return new Span(traceId, spanId, parentSpanId, name,
            startTimeMs, System.currentTimeMillis(),
            new HashMap<>(attributes), SpanStatus.OK, null);
    }

    /**
     * 异常结束，返回不可变 Span
     */
    public Span endWithError(Throwable t) {
        return new Span(traceId, spanId, parentSpanId, name,
            startTimeMs, System.currentTimeMillis(),
            new HashMap<>(attributes), SpanStatus.ERROR,
            t != null ? t.getMessage() : "unknown error");
    }

    public String getTraceId() { return traceId; }
    public String getSpanId() { return spanId; }
    public String getParentSpanId() { return parentSpanId; }
    public String getName() { return name; }
    public long getStartTimeMs() { return startTimeMs; }

    private static String generateId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
