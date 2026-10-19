package com.lightweightai.mcp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.util.context.Context;

import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("McpHeaderContext — per-request header bridging via ThreadLocal + Reactor Context")
class McpHeaderContextTest {

    @AfterEach
    void cleanup() {
        McpHeaderContext.unbind();
    }

    @Test
    @DisplayName("current() returns empty map when no binding")
    void currentWithoutBinding() {
        Map<String, String> headers = McpHeaderContext.current();
        assertNotNull(headers);
        assertTrue(headers.isEmpty());
    }

    @Test
    @DisplayName("of(key, value) creates context modifier with single header")
    void ofSingleHeader() {
        Function<Context, Context> modifier = McpHeaderContext.of("Authorization", "Bearer token123");
        Context ctx = modifier.apply(Context.empty());

        assertTrue(ctx.hasKey(McpHeaderContext.CTX_KEY));
        @SuppressWarnings("unchecked")
        Map<String, String> headers = ctx.get(McpHeaderContext.CTX_KEY);
        assertEquals("Bearer token123", headers.get("Authorization"));
    }

    @Test
    @DisplayName("of(map) creates context modifier with multiple headers")
    void ofMultipleHeaders() {
        Map<String, String> input = Map.of(
                "Authorization", "Bearer token",
                "X-Tenant-Id", "tenant-42"
        );
        Function<Context, Context> modifier = McpHeaderContext.of(input);
        Context ctx = modifier.apply(Context.empty());

        @SuppressWarnings("unchecked")
        Map<String, String> headers = ctx.get(McpHeaderContext.CTX_KEY);
        assertEquals("Bearer token", headers.get("Authorization"));
        assertEquals("tenant-42", headers.get("X-Tenant-Id"));
    }

    @Test
    @DisplayName("of(map) creates defensive copy — mutation of input map is safe")
    void ofDefensiveCopy() {
        java.util.HashMap<String, String> input = new java.util.HashMap<>();
        input.put("k", "v");

        Function<Context, Context> modifier = McpHeaderContext.of(input);
        input.put("injected", "bad");

        Context ctx = modifier.apply(Context.empty());
        @SuppressWarnings("unchecked")
        Map<String, String> headers = ctx.get(McpHeaderContext.CTX_KEY);
        assertFalse(headers.containsKey("injected"), "input map mutation should not affect context");
    }

    @Test
    @DisplayName("bind + current round-trip: headers set via bind are visible via current()")
    void bindAndCurrentRoundTrip() {
        Map<String, String> expected = Map.of("X-Custom", "value1");
        Context ctx = Context.of(McpHeaderContext.CTX_KEY, expected);

        McpHeaderContext.bind(ctx);

        Map<String, String> actual = McpHeaderContext.current();
        assertEquals("value1", actual.get("X-Custom"));
    }

    @Test
    @DisplayName("unbind clears the ThreadLocal binding")
    void unbindClears() {
        Context ctx = Context.of(McpHeaderContext.CTX_KEY, Map.of("k", "v"));
        McpHeaderContext.bind(ctx);
        assertEquals("v", McpHeaderContext.current().get("k"));

        McpHeaderContext.unbind();

        assertTrue(McpHeaderContext.current().isEmpty(), "after unbind, current() should be empty");
    }

    @Test
    @DisplayName("bind with empty headers does not set ThreadLocal")
    void bindEmptyHeaders() {
        Context ctx = Context.of(McpHeaderContext.CTX_KEY, Map.of());
        McpHeaderContext.bind(ctx);

        assertTrue(McpHeaderContext.current().isEmpty());
    }

    @Test
    @DisplayName("bind with missing context key uses empty map")
    void bindMissingKey() {
        Context ctx = Context.empty();
        McpHeaderContext.bind(ctx);

        assertTrue(McpHeaderContext.current().isEmpty());
    }

    @Test
    @DisplayName("CTX_KEY constant is stable")
    void ctxKeyStable() {
        assertEquals("mcp.request.headers", McpHeaderContext.CTX_KEY);
    }
}
