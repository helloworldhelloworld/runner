package com.lightweightai.kernel.trace;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SpanContext + Span 核心测试
 */
class SpanContextTest {

    @Test
    void createRoot_generatesUniqueIds() {
        SpanContext root = SpanContext.createRoot("gateway.handle");

        assertNotNull(root.getTraceId());
        assertNotNull(root.getSpanId());
        assertNull(root.getParentSpanId());
        assertEquals("gateway.handle", root.getName());
        assertTrue(root.getStartTimeMs() > 0);
    }

    @Test
    void createChild_inheritsTraceId() {
        SpanContext root = SpanContext.createRoot("gateway.handle");
        SpanContext child = root.createChild("tcl.iteration.0");

        assertEquals(root.getTraceId(), child.getTraceId());
        assertEquals(root.getSpanId(), child.getParentSpanId());
        assertNotEquals(root.getSpanId(), child.getSpanId());
        assertEquals("tcl.iteration.0", child.getName());
    }

    @Test
    void createChild_multiLevel() {
        SpanContext root = SpanContext.createRoot("gateway");
        SpanContext child = root.createChild("iteration");
        SpanContext grandchild = child.createChild("tool.execute");

        assertEquals(root.getTraceId(), grandchild.getTraceId());
        assertEquals(child.getSpanId(), grandchild.getParentSpanId());
    }

    @Test
    void setAttribute_andEnd() {
        SpanContext ctx = SpanContext.createRoot("test");
        ctx.setAttribute("iteration", 0);
        ctx.setAttribute("messagesCount", 5);

        Span span = ctx.end();

        assertEquals("test", span.getName());
        assertEquals(SpanStatus.OK, span.getStatus());
        assertNull(span.getErrorMessage());
        assertTrue(span.getEndTimeMs() >= span.getStartTimeMs());
        assertEquals(0, span.getAttributes().get("iteration"));
        assertEquals(5, span.getAttributes().get("messagesCount"));
    }

    @Test
    void endWithError_capturesErrorInfo() {
        SpanContext ctx = SpanContext.createRoot("test");
        ctx.setAttribute("toolName", "checkWeather");

        Span span = ctx.endWithError(new RuntimeException("connection timeout"));

        assertEquals(SpanStatus.ERROR, span.getStatus());
        assertEquals("connection timeout", span.getErrorMessage());
        assertEquals("checkWeather", span.getAttributes().get("toolName"));
        assertTrue(span.getEndTimeMs() >= span.getStartTimeMs());
    }

    @Test
    void span_isImmutable() {
        SpanContext ctx = SpanContext.createRoot("test");
        ctx.setAttribute("key", "value");
        Span span = ctx.end();

        Map<String, Object> attrs = span.getAttributes();
        assertThrows(UnsupportedOperationException.class, () -> attrs.put("new", "value"));
    }

    @Test
    void uniqueSpanIds_acrossMultipleSpans() {
        SpanContext root = SpanContext.createRoot("root");
        SpanContext c1 = root.createChild("child1");
        SpanContext c2 = root.createChild("child2");
        SpanContext c3 = root.createChild("child3");

        // All span IDs are unique
        long distinctCount = java.util.stream.Stream.of(
            root.getSpanId(), c1.getSpanId(), c2.getSpanId(), c3.getSpanId()
        ).distinct().count();
        assertEquals(4, distinctCount);

        // All share the same trace ID
        assertEquals(root.getTraceId(), c1.getTraceId());
        assertEquals(root.getTraceId(), c2.getTraceId());
        assertEquals(root.getTraceId(), c3.getTraceId());
    }
}
