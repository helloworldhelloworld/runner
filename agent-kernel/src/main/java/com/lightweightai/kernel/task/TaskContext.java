package com.lightweightai.kernel.task;

import com.lightweightai.kernel.trace.SpanContext;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 任务执行上下文 (Harness Engineering - Entropy Management)。
 *
 * 自动关联 trace span 和 baggage，支持取消和参数传递。
 */
public class TaskContext {

    private final String taskId;
    private final String taskName;
    private final SpanContext spanContext;
    private final Map<String, String> baggage;
    private final Map<String, Object> params;
    private final AtomicBoolean cancelled;

    public TaskContext(String taskId, String taskName, SpanContext spanContext,
                       Map<String, String> baggage, Map<String, Object> params) {
        this.taskId = taskId;
        this.taskName = taskName;
        this.spanContext = spanContext;
        this.baggage = baggage != null ? baggage : Collections.emptyMap();
        this.params = params != null ? params : Collections.emptyMap();
        this.cancelled = new AtomicBoolean(false);
    }

    public String getTaskId() { return taskId; }
    public String getTaskName() { return taskName; }
    public SpanContext getSpanContext() { return spanContext; }
    public Map<String, String> getBaggage() { return baggage; }
    public Map<String, Object> getParams() { return params; }

    public boolean isCancelled() { return cancelled.get(); }
    public void cancel() { cancelled.set(true); }

    @SuppressWarnings("unchecked")
    public <T> T getParam(String key, Class<T> type) {
        Object value = params.get(key);
        return type.isInstance(value) ? (T) value : null;
    }

    public String getParam(String key) {
        Object value = params.get(key);
        return value != null ? value.toString() : null;
    }
}
