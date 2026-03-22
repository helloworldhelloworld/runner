package com.lightweightai.kernel.trace.export;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.trace.SpanContext;
import com.lightweightai.kernel.trace.SpanStatus;
import com.lightweightai.kernel.trace.Tracer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StreamEventSpanExporterTest {

    @Test
    void collectsSpansAndDrainsAsStreamEvents() {
        StreamEventSpanExporter exporter = new StreamEventSpanExporter();

        Tracer tracer = Tracer.builder().addExporter(exporter).build();

        SpanContext root = tracer.startTrace("gateway.handle");
        root.setAttribute("sessionId", "s1");
        SpanContext child = root.createChild("tcl.iteration.0");
        child.setAttribute("iteration", 0);

        tracer.endSpan(child);
        tracer.endSpan(root);

        List<StreamEvent> events = exporter.drain();
        assertEquals(2, events.size());

        // First span: child
        assertEquals(StreamEvent.EventType.TRACE, events.get(0).getType());
        assertEquals("tcl.iteration.0", events.get(0).getTracePhase());
        assertNotNull(events.get(0).getData());
        assertEquals(0, events.get(0).getData().get("iteration"));
        assertNotNull(events.get(0).getData().get("durationMs"));
        assertEquals("OK", events.get(0).getData().get("status"));

        // Second span: root
        assertEquals("gateway.handle", events.get(1).getTracePhase());
        assertEquals("s1", events.get(1).getData().get("sessionId"));
    }

    @Test
    void drainClearsCollectedSpans() {
        StreamEventSpanExporter exporter = new StreamEventSpanExporter();

        SpanContext ctx = SpanContext.createRoot("test");
        exporter.export(ctx.end());

        List<StreamEvent> first = exporter.drain();
        assertEquals(1, first.size());

        List<StreamEvent> second = exporter.drain();
        assertTrue(second.isEmpty());
    }

    @Test
    void handlesErrorSpans() {
        StreamEventSpanExporter exporter = new StreamEventSpanExporter();

        SpanContext ctx = SpanContext.createRoot("tool.execute.search");
        ctx.setAttribute("toolName", "search");
        exporter.export(ctx.endWithError(new RuntimeException("timeout")));

        List<StreamEvent> events = exporter.drain();
        assertEquals(1, events.size());
        assertEquals("ERROR", events.get(0).getData().get("status"));
        assertEquals("timeout", events.get(0).getData().get("errorMessage"));
    }

    @Test
    void spanAttributesMergedIntoData() {
        StreamEventSpanExporter exporter = new StreamEventSpanExporter();

        SpanContext ctx = SpanContext.createRoot("llm.stream");
        ctx.setAttribute("eventCount", 42);
        ctx.setAttribute("hasToolCalls", true);
        exporter.export(ctx.end());

        List<StreamEvent> events = exporter.drain();
        assertEquals(42, events.get(0).getData().get("eventCount"));
        assertEquals(true, events.get(0).getData().get("hasToolCalls"));
    }
}
