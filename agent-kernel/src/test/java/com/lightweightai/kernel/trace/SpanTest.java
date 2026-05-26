package com.lightweightai.kernel.trace;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SpanTest {

    private Span span(String traceId, String spanId, String parent, String name,
                      long start, long end, Map<String, Object> attrs, SpanStatus status, String err) {
        return new Span(traceId, spanId, parent, name, start, end, attrs, status, err);
    }

    @Test
    void storesAllFields() {
        Span s = span("t1", "s1", "s0", "llm-call", 1000, 2000, Map.of("model", "claude"), SpanStatus.OK, null);
        assertEquals("t1", s.getTraceId());
        assertEquals("s1", s.getSpanId());
        assertEquals("s0", s.getParentSpanId());
        assertEquals("llm-call", s.getName());
        assertEquals(1000, s.getStartTimeMs());
        assertEquals(2000, s.getEndTimeMs());
        assertEquals(SpanStatus.OK, s.getStatus());
        assertNull(s.getErrorMessage());
    }

    @Test
    void durationCalculation() {
        assertEquals(500, span("t", "s", null, "op", 5000, 5500, Map.of(), SpanStatus.OK, null).getDurationMs());
    }

    @Test
    void attributesImmutable() {
        Span s = span("t", "s", null, "op", 0, 100, Map.of("k", "v"), SpanStatus.OK, null);
        assertThrows(UnsupportedOperationException.class, () -> s.getAttributes().put("x", "y"));
    }

    @Test
    void attributesPayload() {
        Span s = span("t", "s", null, "op", 0, 100, Map.of("model", "claude", "tokens", 42), SpanStatus.OK, null);
        assertEquals("claude", s.getAttributes().get("model"));
        assertEquals(42, s.getAttributes().get("tokens"));
    }

    @Test
    void rootSpanHasNullParent() {
        assertNull(span("t", "s", null, "op", 0, 100, Map.of(), SpanStatus.OK, null).getParentSpanId());
    }

    @Test
    void errorSpanHasMessage() {
        Span s = span("t", "s", null, "op", 0, 100, Map.of(), SpanStatus.ERROR, "timeout");
        assertEquals(SpanStatus.ERROR, s.getStatus());
        assertEquals("timeout", s.getErrorMessage());
    }

    @Test
    void toStringContainsInfo() {
        String str = span("trace-abc", "span-def", null, "my-op", 1000, 2000, Map.of(), SpanStatus.OK, null).toString();
        assertTrue(str.contains("my-op"));
        assertTrue(str.contains("trace-abc"));
    }

    @Test
    void spanStatusValues() {
        assertEquals(2, SpanStatus.values().length);
    }
}
