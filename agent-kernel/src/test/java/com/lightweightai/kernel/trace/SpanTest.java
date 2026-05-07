package com.lightweightai.kernel.trace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Span - 追踪记录值对象")
class SpanTest {

    private Span createSpan(String name, long startMs, long endMs, SpanStatus status) {
        return new Span(
                "trace-001", "span-001", null, name,
                startMs, endMs,
                Map.of("key", "value"), status, null
        );
    }

    @Test
    @DisplayName("所有字段正确存储")
    void fieldsStoredCorrectly() {
        Span span = new Span(
                "t1", "s1", "ps1", "llm_call",
                1000L, 2500L,
                Map.of("model", "claude"), SpanStatus.OK, null
        );

        assertEquals("t1", span.getTraceId());
        assertEquals("s1", span.getSpanId());
        assertEquals("ps1", span.getParentSpanId());
        assertEquals("llm_call", span.getName());
        assertEquals(1000L, span.getStartTimeMs());
        assertEquals(2500L, span.getEndTimeMs());
        assertEquals("claude", span.getAttributes().get("model"));
        assertEquals(SpanStatus.OK, span.getStatus());
        assertNull(span.getErrorMessage());
    }

    @Test
    @DisplayName("getDurationMs → end - start")
    void durationCalculation() {
        Span span = createSpan("test", 100, 350, SpanStatus.OK);

        assertEquals(250, span.getDurationMs());
    }

    @Test
    @DisplayName("parentSpanId 为 null → root span")
    void rootSpanHasNullParent() {
        Span span = createSpan("root", 0, 100, SpanStatus.OK);

        assertNull(span.getParentSpanId());
    }

    @Test
    @DisplayName("ERROR 状态 + errorMessage")
    void errorSpan() {
        Span span = new Span(
                "t1", "s1", null, "failed_call",
                0, 50,
                Map.of(), SpanStatus.ERROR, "Timeout"
        );

        assertEquals(SpanStatus.ERROR, span.getStatus());
        assertEquals("Timeout", span.getErrorMessage());
    }

    @Test
    @DisplayName("attributes 不可变")
    void attributesUnmodifiable() {
        Span span = createSpan("test", 0, 10, SpanStatus.OK);

        assertThrows(UnsupportedOperationException.class,
                () -> span.getAttributes().put("new", "val"));
    }

    @Test
    @DisplayName("toString 包含关键信息")
    void toStringContainsInfo() {
        Span span = new Span(
                "t-abc", "s-xyz", "s-parent", "tool_exec",
                100, 200,
                Map.of("tool", "calc"), SpanStatus.OK, null
        );

        String str = span.toString();
        assertTrue(str.contains("tool_exec"));
        assertTrue(str.contains("t-abc"));
        assertTrue(str.contains("s-xyz"));
        assertTrue(str.contains("100ms"));
    }

    @Test
    @DisplayName("零时长 span (start == end)")
    void zeroDuration() {
        Span span = createSpan("instant", 100, 100, SpanStatus.OK);
        assertEquals(0, span.getDurationMs());
    }
}
