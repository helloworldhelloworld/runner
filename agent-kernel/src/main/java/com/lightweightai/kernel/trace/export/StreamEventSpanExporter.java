package com.lightweightai.kernel.trace.export;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.trace.Span;
import com.lightweightai.kernel.trace.SpanExporter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 收集已完成的 Span，转换为 StreamEvent.TRACE 格式。
 *
 * 调用 drain() 获取并清空已收集的事件，可供 PostProcessor 注入流中。
 */
public class StreamEventSpanExporter implements SpanExporter {

    private final CopyOnWriteArrayList<Span> collected = new CopyOnWriteArrayList<>();

    @Override
    public void export(Span span) {
        collected.add(span);
    }

    /**
     * 获取并清空所有已收集的 span，转为 StreamEvent.TRACE 列表
     */
    public List<StreamEvent> drain() {
        List<Span> snapshot = new ArrayList<>(collected);
        collected.removeAll(snapshot);

        return snapshot.stream()
            .map(this::toStreamEvent)
            .toList();
    }

    private StreamEvent toStreamEvent(Span span) {
        Map<String, Object> data = new LinkedHashMap<>(span.getAttributes());
        data.put("traceId", span.getTraceId());
        data.put("spanId", span.getSpanId());
        if (span.getParentSpanId() != null) {
            data.put("parentSpanId", span.getParentSpanId());
        }
        data.put("durationMs", span.getDurationMs());
        data.put("status", span.getStatus().name());
        if (span.getErrorMessage() != null) {
            data.put("errorMessage", span.getErrorMessage());
        }

        return StreamEvent.trace(
            span.getName(),
            "duration=" + span.getDurationMs() + "ms, status=" + span.getStatus(),
            data
        );
    }
}
