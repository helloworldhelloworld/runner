package com.lightweightai.kernel.trace;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 可变的进行中 Span 上下文。
 *
 * 在构造时生成 spanId 并记录 startTimeMs。
 * 调用 end() 或 endWithError() 冻结为不可变的 Span。
 *
 * 增强 (Harness Engineering - Context Engineering):
 * - baggage: 业务级上下文跨 span 传播 (userId, costBudget 等)
 * - kind: Span 类型 (SERVER/CLIENT/INTERNAL)
 */
public final class SpanContext {

    private final String traceId;
    private final String spanId;
    private final String parentSpanId;
    private final String name;
    private final long startTimeMs;
    private final SpanKind kind;
    private final ConcurrentHashMap<String, Object> attributes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> baggage;

    private SpanContext(String traceId, String spanId, String parentSpanId,
                        String name, SpanKind kind, Map<String, String> baggage) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.name = name;
        this.kind = kind;
        this.startTimeMs = System.currentTimeMillis();
        this.baggage = baggage != null
            ? new ConcurrentHashMap<>(baggage)
            : new ConcurrentHashMap<>();
    }

    /**
     * 创建根 span（新的 trace）
     */
    public static SpanContext createRoot(String name) {
        return createRoot(name, SpanKind.SERVER);
    }

    /**
     * 创建根 span（指定 kind）
     */
    public static SpanContext createRoot(String name, SpanKind kind) {
        String traceId = generateId();
        String spanId = generateId();
        return new SpanContext(traceId, spanId, null, name, kind, null);
    }

    /**
     * 从已知 traceId 创建（用于接续外部 trace，如 W3C traceparent）
     */
    public static SpanContext createFromRemote(String traceId, String parentSpanId,
                                                String name, SpanKind kind) {
        String spanId = generateId();
        return new SpanContext(traceId, spanId, parentSpanId, name, kind, null);
    }

    /**
     * 创建子 span，继承 traceId 和 baggage
     */
    public SpanContext createChild(String childName) {
        return createChild(childName, SpanKind.INTERNAL);
    }

    /**
     * 创建子 span，指定 kind，继承 traceId 和 baggage
     */
    public SpanContext createChild(String childName, SpanKind childKind) {
        String childSpanId = generateId();
        return new SpanContext(this.traceId, childSpanId, this.spanId,
            childName, childKind, this.baggage);
    }

    /**
     * 设置属性
     */
    public SpanContext setAttribute(String key, Object value) {
        attributes.put(key, value);
        return this;
    }

    // ==================== Baggage ====================

    /**
     * 设置 baggage（跨 span 传播的业务上下文）
     */
    public SpanContext setBaggage(String key, String value) {
        baggage.put(key, value);
        return this;
    }

    /**
     * 获取 baggage 值
     */
    public String getBaggage(String key) {
        return baggage.get(key);
    }

    /**
     * 获取所有 baggage（不可变视图）
     */
    public Map<String, String> getAllBaggage() {
        return Collections.unmodifiableMap(baggage);
    }

    // ==================== End ====================

    /**
     * 正常结束，返回不可变 Span
     */
    public Span end() {
        return new Span(traceId, spanId, parentSpanId, name, kind,
            startTimeMs, System.currentTimeMillis(),
            new HashMap<>(attributes), new HashMap<>(baggage),
            SpanStatus.OK, null);
    }

    /**
     * 异常结束，返回不可变 Span
     */
    public Span endWithError(Throwable t) {
        return new Span(traceId, spanId, parentSpanId, name, kind,
            startTimeMs, System.currentTimeMillis(),
            new HashMap<>(attributes), new HashMap<>(baggage),
            SpanStatus.ERROR,
            t != null ? t.getMessage() : "unknown error");
    }

    public String getTraceId() { return traceId; }
    public String getSpanId() { return spanId; }
    public String getParentSpanId() { return parentSpanId; }
    public String getName() { return name; }
    public long getStartTimeMs() { return startTimeMs; }
    public SpanKind getKind() { return kind; }

    private static String generateId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
