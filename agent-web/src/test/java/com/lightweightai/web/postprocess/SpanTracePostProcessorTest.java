package com.lightweightai.web.postprocess;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.trace.SpanContext;
import com.lightweightai.kernel.trace.export.StreamEventSpanExporter;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpanTracePostProcessorTest {

    @Test
    void appendsSpanEventsAtStreamEnd() {
        StreamEventSpanExporter exporter = new StreamEventSpanExporter();

        // Simulate completed spans
        SpanContext root = SpanContext.createRoot("gateway.handle");
        root.setAttribute("sessionId", "s1");
        SpanContext child = root.createChild("tcl.iteration.0");
        child.setAttribute("iteration", 0);
        exporter.export(child.end());
        exporter.export(root.end());

        SpanTracePostProcessor processor = new SpanTracePostProcessor(exporter);

        // Input stream: just a text delta
        Flux<StreamEvent> input = Flux.just(StreamEvent.textDelta("Hello"));

        List<StreamEvent> result = input.transform(processor).collectList().block();
        assertNotNull(result);

        // 1 text delta + 2 span traces
        assertEquals(3, result.size());
        assertEquals(StreamEvent.EventType.TEXT_DELTA, result.get(0).getType());
        assertEquals(StreamEvent.EventType.TRACE, result.get(1).getType());
        assertEquals(StreamEvent.EventType.TRACE, result.get(2).getType());

        // Span traces have span data
        assertEquals("tcl.iteration.0", result.get(1).getTracePhase());
        assertEquals("gateway.handle", result.get(2).getTracePhase());
        assertNotNull(result.get(1).getData().get("traceId"));
        assertNotNull(result.get(1).getData().get("spanId"));
        assertNotNull(result.get(1).getData().get("durationMs"));
    }

    @Test
    void noSpans_noExtraEvents() {
        StreamEventSpanExporter exporter = new StreamEventSpanExporter();
        SpanTracePostProcessor processor = new SpanTracePostProcessor(exporter);

        Flux<StreamEvent> input = Flux.just(StreamEvent.textDelta("Hello"));
        List<StreamEvent> result = input.transform(processor).collectList().block();

        assertEquals(1, result.size());
    }

    @Test
    void nameAndOrder() {
        SpanTracePostProcessor processor = new SpanTracePostProcessor(new StreamEventSpanExporter());
        assertEquals("span-trace", processor.getName());
        assertTrue(processor.getOrder() > 1000); // After TracingPostProcessor
    }
}
