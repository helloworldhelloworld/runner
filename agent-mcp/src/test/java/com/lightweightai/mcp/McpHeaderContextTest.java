package com.lightweightai.mcp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.util.context.Context;

import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("McpHeaderContext")
class McpHeaderContextTest {

    @AfterEach
    void cleanup() {
        // Always unbind after each test to prevent ThreadLocal leaks
        McpHeaderContext.unbind();
    }

    @Nested
    @DisplayName("of(String, String) -- single header")
    class SingleHeaderTests {

        @Test
        @DisplayName("writes a single header into Reactor Context")
        void singleHeaderWritten() {
            Function<Context, Context> modifier = McpHeaderContext.of("Authorization", "Bearer tok-123");
            Context ctx = modifier.apply(Context.empty());

            @SuppressWarnings("unchecked")
            Map<String, String> headers = ctx.get(McpHeaderContext.CTX_KEY);
            assertEquals(1, headers.size());
            assertEquals("Bearer tok-123", headers.get("Authorization"));
        }

        @Test
        @DisplayName("overwrites previous headers in context")
        void overwritesPrevious() {
            Context ctx = McpHeaderContext.of("X-First", "one").apply(Context.empty());
            ctx = McpHeaderContext.of("X-Second", "two").apply(ctx);

            @SuppressWarnings("unchecked")
            Map<String, String> headers = ctx.get(McpHeaderContext.CTX_KEY);
            // Second call replaces the entire map, not merges
            assertEquals(1, headers.size());
            assertEquals("two", headers.get("X-Second"));
            assertNull(headers.get("X-First"));
        }
    }

    @Nested
    @DisplayName("of(Map) -- multiple headers")
    class MultiHeaderTests {

        @Test
        @DisplayName("writes multiple headers into Reactor Context")
        void multipleHeaders() {
            Map<String, String> input = Map.of(
                    "Authorization", "Bearer abc",
                    "X-Tenant-Id", "tenant-42"
            );
            Context ctx = McpHeaderContext.of(input).apply(Context.empty());

            @SuppressWarnings("unchecked")
            Map<String, String> headers = ctx.get(McpHeaderContext.CTX_KEY);
            assertEquals(2, headers.size());
            assertEquals("Bearer abc", headers.get("Authorization"));
            assertEquals("tenant-42", headers.get("X-Tenant-Id"));
        }

        @Test
        @DisplayName("stores a defensive copy -- mutations to input map do not propagate")
        void defensiveCopy() {
            java.util.HashMap<String, String> input = new java.util.HashMap<>();
            input.put("X-Key", "original");

            Function<Context, Context> modifier = McpHeaderContext.of(input);
            input.put("X-Key", "mutated");

            Context ctx = modifier.apply(Context.empty());
            @SuppressWarnings("unchecked")
            Map<String, String> headers = ctx.get(McpHeaderContext.CTX_KEY);
            assertEquals("original", headers.get("X-Key"),
                    "should have stored the original value, not the mutated one");
        }

        @Test
        @DisplayName("stored map is unmodifiable")
        void unmodifiable() {
            Context ctx = McpHeaderContext.of(Map.of("K", "V")).apply(Context.empty());

            @SuppressWarnings("unchecked")
            Map<String, String> headers = ctx.get(McpHeaderContext.CTX_KEY);
            assertThrows(UnsupportedOperationException.class, () -> headers.put("new", "val"));
        }
    }

    @Nested
    @DisplayName("bind / unbind / current -- ThreadLocal bridge")
    class ThreadLocalTests {

        @Test
        @DisplayName("bind makes headers available via current()")
        void bindAndCurrent() {
            Context ctx = McpHeaderContext.of("X-Trace", "trace-001").apply(Context.empty());
            McpHeaderContext.bind(ctx);

            Map<String, String> current = McpHeaderContext.current();
            assertEquals("trace-001", current.get("X-Trace"));
        }

        @Test
        @DisplayName("unbind clears ThreadLocal -- current() returns empty map")
        void unbindClears() {
            Context ctx = McpHeaderContext.of("X-Key", "val").apply(Context.empty());
            McpHeaderContext.bind(ctx);
            McpHeaderContext.unbind();

            Map<String, String> current = McpHeaderContext.current();
            assertTrue(current.isEmpty());
        }

        @Test
        @DisplayName("current() returns empty map when nothing was bound")
        void currentWithoutBind() {
            Map<String, String> current = McpHeaderContext.current();
            assertNotNull(current);
            assertTrue(current.isEmpty());
        }

        @Test
        @DisplayName("bind with empty headers does not set ThreadLocal -- current() returns empty")
        void bindEmptyHeaders() {
            // Context with no CTX_KEY set at all
            McpHeaderContext.bind(Context.empty());

            Map<String, String> current = McpHeaderContext.current();
            assertTrue(current.isEmpty());
        }

        @Test
        @DisplayName("bind replaces previously bound headers")
        void bindReplace() {
            Context ctx1 = McpHeaderContext.of("X-First", "1").apply(Context.empty());
            McpHeaderContext.bind(ctx1);
            assertEquals("1", McpHeaderContext.current().get("X-First"));

            Context ctx2 = McpHeaderContext.of("X-Second", "2").apply(Context.empty());
            McpHeaderContext.bind(ctx2);
            Map<String, String> current = McpHeaderContext.current();
            assertEquals("2", current.get("X-Second"));
            assertNull(current.get("X-First"), "old headers should be replaced");
        }
    }

    @Nested
    @DisplayName("Full round-trip: of -> bind -> current -> unbind")
    class RoundTripTests {

        @Test
        @DisplayName("single header survives full round-trip")
        void singleRoundTrip() {
            // Step 1: Create context with header
            Context ctx = McpHeaderContext.of("Authorization", "Bearer secret").apply(Context.empty());

            // Step 2: Bind to thread
            McpHeaderContext.bind(ctx);

            // Step 3: Read from current
            Map<String, String> headers = McpHeaderContext.current();
            assertEquals(1, headers.size());
            assertEquals("Bearer secret", headers.get("Authorization"));

            // Step 4: Unbind
            McpHeaderContext.unbind();
            assertTrue(McpHeaderContext.current().isEmpty());
        }

        @Test
        @DisplayName("multiple headers survive full round-trip")
        void multiRoundTrip() {
            Map<String, String> input = Map.of(
                    "Authorization", "Bearer tok",
                    "X-Request-Id", "req-999",
                    "X-Tenant-Id", "t-5"
            );
            Context ctx = McpHeaderContext.of(input).apply(Context.empty());
            McpHeaderContext.bind(ctx);

            Map<String, String> current = McpHeaderContext.current();
            assertEquals(3, current.size());
            assertEquals("Bearer tok", current.get("Authorization"));
            assertEquals("req-999", current.get("X-Request-Id"));
            assertEquals("t-5", current.get("X-Tenant-Id"));

            McpHeaderContext.unbind();
            assertTrue(McpHeaderContext.current().isEmpty());
        }
    }

    @Nested
    @DisplayName("CTX_KEY constant")
    class ConstantTests {

        @Test
        @DisplayName("CTX_KEY has expected value")
        void ctxKeyValue() {
            assertEquals("mcp.request.headers", McpHeaderContext.CTX_KEY);
        }
    }
}
