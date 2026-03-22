package com.lightweightai.kernel.trace;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tracer 核心测试
 */
class TracerTest {

    @Test
    void tracer_exportsToAllExporters() {
        List<Span> exported1 = new ArrayList<>();
        List<Span> exported2 = new ArrayList<>();

        Tracer tracer = Tracer.builder()
            .addExporter(exported1::add)
            .addExporter(exported2::add)
            .build();

        SpanContext ctx = tracer.startTrace("gateway.handle");
        ctx.setAttribute("sessionId", "s1");
        tracer.endSpan(ctx);

        assertEquals(1, exported1.size());
        assertEquals(1, exported2.size());
        assertEquals("gateway.handle", exported1.get(0).getName());
        assertEquals("s1", exported1.get(0).getAttributes().get("sessionId"));
        assertEquals(SpanStatus.OK, exported1.get(0).getStatus());
    }

    @Test
    void tracer_endSpanWithError() {
        List<Span> exported = new ArrayList<>();

        Tracer tracer = Tracer.builder()
            .addExporter(exported::add)
            .build();

        SpanContext ctx = tracer.startTrace("tool.execute");
        tracer.endSpanWithError(ctx, new RuntimeException("timeout"));

        assertEquals(1, exported.size());
        assertEquals(SpanStatus.ERROR, exported.get(0).getStatus());
        assertEquals("timeout", exported.get(0).getErrorMessage());
    }

    @Test
    void tracer_noop_doesNothing() {
        Tracer noop = Tracer.NOOP;

        SpanContext ctx = noop.startTrace("test");
        assertNotNull(ctx); // returns valid context but no export
        ctx.setAttribute("key", "value");
        noop.endSpan(ctx); // should not throw
        noop.endSpanWithError(ctx, new RuntimeException("err")); // should not throw
    }

    @Test
    void tracer_exporterException_doesNotPropagate() {
        List<Span> exported = new ArrayList<>();

        Tracer tracer = Tracer.builder()
            .addExporter(span -> { throw new RuntimeException("exporter crash"); })
            .addExporter(exported::add) // second exporter should still receive
            .build();

        SpanContext ctx = tracer.startTrace("test");
        tracer.endSpan(ctx);

        assertEquals(1, exported.size()); // second exporter received the span
    }

    @Test
    void tracer_childSpanExport() {
        List<Span> exported = new CopyOnWriteArrayList<>();

        Tracer tracer = Tracer.builder()
            .addExporter(exported::add)
            .build();

        SpanContext root = tracer.startTrace("gateway");
        SpanContext child = root.createChild("iteration");
        child.setAttribute("iteration", 0);

        tracer.endSpan(child);
        tracer.endSpan(root);

        assertEquals(2, exported.size());
        // Child first, root second
        assertEquals("iteration", exported.get(0).getName());
        assertEquals("gateway", exported.get(1).getName());
        // Parent-child relationship
        assertEquals(root.getSpanId(), exported.get(0).getParentSpanId());
        assertNull(exported.get(1).getParentSpanId());
    }
}
