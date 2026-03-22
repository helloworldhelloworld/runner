package com.lightweightai.kernel.trace.export;

import com.lightweightai.kernel.trace.Span;
import com.lightweightai.kernel.trace.SpanContext;
import com.lightweightai.kernel.trace.SpanStatus;
import com.lightweightai.kernel.trace.Tracer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConsoleSpanExporterTest {

    @Test
    void export_doesNotThrow() {
        ConsoleSpanExporter exporter = new ConsoleSpanExporter();

        SpanContext ctx = SpanContext.createRoot("gateway.handle");
        ctx.setAttribute("sessionId", "s1");
        ctx.setAttribute("requestId", "r1");
        Span span = ctx.end();

        assertDoesNotThrow(() -> exporter.export(span));
    }

    @Test
    void export_handlesErrorSpan() {
        ConsoleSpanExporter exporter = new ConsoleSpanExporter();

        SpanContext ctx = SpanContext.createRoot("tool.execute.checkWeather");
        ctx.setAttribute("toolName", "checkWeather");
        Span span = ctx.endWithError(new RuntimeException("API timeout"));

        assertDoesNotThrow(() -> exporter.export(span));
    }

    @Test
    void getName() {
        assertEquals("ConsoleSpanExporter", new ConsoleSpanExporter().getName());
    }

    @Test
    void worksWithTracer() {
        List<Span> collected = new ArrayList<>();
        ConsoleSpanExporter consoleExporter = new ConsoleSpanExporter();

        Tracer tracer = Tracer.builder()
            .addExporter(consoleExporter)
            .addExporter(collected::add)
            .build();

        SpanContext root = tracer.startTrace("gateway");
        root.setAttribute("sessionId", "test");
        tracer.endSpan(root);

        assertEquals(1, collected.size());
        assertEquals("gateway", collected.get(0).getName());
    }
}
