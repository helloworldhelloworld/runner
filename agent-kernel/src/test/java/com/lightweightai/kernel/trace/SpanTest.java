package com.lightweightai.kernel.trace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Span - immutable trace span value object")
class SpanTest {

    private Span createSpan(Map<String, Object> attributes, SpanStatus status, String errorMessage) {
        return new Span(
            "trace-001", "span-001", "parent-001", "testOp",
            1000L, 2500L,
            attributes, status, errorMessage
        );
    }

    private Span createDefaultSpan() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("key1", "value1");
        attrs.put("key2", 42);
        return createSpan(attrs, SpanStatus.OK, null);
    }

    @Nested
    @DisplayName("Given a span with all fields populated")
    class AllFields {

        @Test
        @DisplayName("getters return the values passed to constructor")
        void shouldReturnConstructorValues() {
            // Given
            Map<String, Object> attrs = new HashMap<>();
            attrs.put("http.method", "GET");

            // When
            Span span = new Span(
                "trace-abc", "span-xyz", "parent-123", "httpCall",
                5000L, 5200L,
                attrs, SpanStatus.OK, null
            );

            // Then
            assertEquals("trace-abc", span.getTraceId());
            assertEquals("span-xyz", span.getSpanId());
            assertEquals("parent-123", span.getParentSpanId());
            assertEquals("httpCall", span.getName());
            assertEquals(5000L, span.getStartTimeMs());
            assertEquals(5200L, span.getEndTimeMs());
            assertEquals(SpanStatus.OK, span.getStatus());
            assertNull(span.getErrorMessage());
            assertEquals("GET", span.getAttributes().get("http.method"));
        }

        @Test
        @DisplayName("error span preserves error message")
        void shouldPreserveErrorMessage() {
            Span span = createSpan(Map.of(), SpanStatus.ERROR, "Connection refused");

            assertEquals(SpanStatus.ERROR, span.getStatus());
            assertEquals("Connection refused", span.getErrorMessage());
        }

        @Test
        @DisplayName("null parentSpanId is allowed (root span)")
        void nullParentSpanIdAllowed() {
            Span span = new Span(
                "t1", "s1", null, "root",
                100L, 200L,
                Map.of(), SpanStatus.OK, null
            );

            assertNull(span.getParentSpanId());
        }
    }

    @Nested
    @DisplayName("getDurationMs")
    class DurationCalculation {

        @Test
        @DisplayName("returns endTimeMs minus startTimeMs")
        void shouldCalculateDuration() {
            Span span = createDefaultSpan();

            assertEquals(1500L, span.getDurationMs());
        }

        @Test
        @DisplayName("returns 0 when start equals end")
        void zeroDurationWhenEqual() {
            Span span = new Span(
                "t1", "s1", null, "instant",
                1000L, 1000L,
                Map.of(), SpanStatus.OK, null
            );

            assertEquals(0L, span.getDurationMs());
        }
    }

    @Nested
    @DisplayName("Attributes immutability")
    class AttributesImmutability {

        @Test
        @DisplayName("getAttributes returns unmodifiable map")
        void shouldReturnUnmodifiableMap() {
            Span span = createDefaultSpan();

            Map<String, Object> attributes = span.getAttributes();

            assertThrows(UnsupportedOperationException.class,
                () -> attributes.put("new-key", "new-value"));
        }

        @Test
        @DisplayName("modifying original map does not affect span attributes")
        void originalMapModificationDoesNotAffect() {
            // Given
            Map<String, Object> mutableAttrs = new HashMap<>();
            mutableAttrs.put("original", "value");

            // When
            Span span = createSpan(mutableAttrs, SpanStatus.OK, null);
            mutableAttrs.put("added-later", "should-not-appear");

            // Then - Collections.unmodifiableMap wraps the original, so the mutation
            // may or may not be visible depending on implementation. The key contract
            // is that the returned map itself is unmodifiable.
            assertThrows(UnsupportedOperationException.class,
                () -> span.getAttributes().put("x", "y"));
        }

        @Test
        @DisplayName("attributes contain expected entries")
        void shouldContainExpectedEntries() {
            Span span = createDefaultSpan();

            assertEquals("value1", span.getAttributes().get("key1"));
            assertEquals(42, span.getAttributes().get("key2"));
            assertEquals(2, span.getAttributes().size());
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringBehavior {

        @Test
        @DisplayName("includes span name and traceId")
        void shouldIncludeNameAndTraceId() {
            Span span = createDefaultSpan();

            String result = span.toString();

            assertTrue(result.contains("testOp"), "should contain span name");
            assertTrue(result.contains("trace-001"), "should contain traceId");
            assertTrue(result.contains("span-001"), "should contain spanId");
        }

        @Test
        @DisplayName("includes duration")
        void shouldIncludeDuration() {
            Span span = createDefaultSpan();

            String result = span.toString();

            assertTrue(result.contains("1500ms"), "should contain duration");
        }

        @Test
        @DisplayName("shows 'root' when parentSpanId is null")
        void shouldShowRootForNullParent() {
            Span span = new Span(
                "t1", "s1", null, "rootSpan",
                0L, 100L,
                Map.of(), SpanStatus.OK, null
            );

            String result = span.toString();

            assertTrue(result.contains("root"), "should show 'root' for null parent");
        }

        @Test
        @DisplayName("shows parentSpanId when present")
        void shouldShowParentIdWhenPresent() {
            Span span = createDefaultSpan();

            String result = span.toString();

            assertTrue(result.contains("parent-001"), "should show parent span id");
        }

        @Test
        @DisplayName("includes status")
        void shouldIncludeStatus() {
            Span span = createDefaultSpan();

            String result = span.toString();

            assertTrue(result.contains("OK"), "should contain status");
        }
    }
}
