package com.lightweightai.web.postprocess;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.postprocess.StreamPostProcessor;
import com.lightweightai.kernel.trace.export.StreamEventSpanExporter;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 将 Span 追踪数据注入到流末尾。
 *
 * 在流完成时，从 StreamEventSpanExporter 中 drain 所有已完成的 span，
 * 转为 TRACE 事件追加到流中，供前端展示完整的调用链。
 *
 * 运行在 TracingPostProcessor 之后（order=1100）。
 */
public class SpanTracePostProcessor implements StreamPostProcessor {

    private final StreamEventSpanExporter exporter;

    public SpanTracePostProcessor(StreamEventSpanExporter exporter) {
        this.exporter = exporter;
    }

    @Override
    public String getName() {
        return "span-trace";
    }

    @Override
    public int getOrder() {
        return 1100; // 在 TracingPostProcessor(1000) 之后
    }

    @Override
    public Flux<StreamEvent> apply(Flux<StreamEvent> source) {
        return source.concatWith(Flux.defer(() -> {
            List<StreamEvent> spanEvents = exporter.drain();
            return spanEvents.isEmpty() ? Flux.empty() : Flux.fromIterable(spanEvents);
        }));
    }
}
