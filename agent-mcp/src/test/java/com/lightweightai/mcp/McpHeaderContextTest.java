package com.lightweightai.mcp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.util.context.Context;

import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("McpHeaderContext — Reactor Context ↔ ThreadLocal bridge")
class McpHeaderContextTest {

    @AfterEach
    void cleanup() {
        McpHeaderContext.unbind();
    }

    @Test
    @DisplayName("of(key, value) creates Context modifier with single header")
    void ofSingleHeader() {
        Function<Context, Context> modifier = McpHeaderContext.of("Authorization", "Bearer token123");
        Context ctx = modifier.apply(Context.empty());

        assertTrue(ctx.hasKey(McpHeaderContext.CTX_KEY));
        @SuppressWarnings("unchecked")
        Map<String, String> headers = ctx.get(McpHeaderContext.CTX_KEY);
        assertEquals("Bearer token123", headers.get("Authorization"));
    }

    @Test
    @DisplayName("of(map) creates Context modifier with multiple headers")
    void ofMultipleHeaders() {
        Map<String, String> headerMap = Map.of(
                "Authorization", "Bearer abc",
                "X-Tenant-Id", "tenant-42"
        );
        Function<Context, Context> modifier = McpHeaderContext.of(headerMap);
        Context ctx = modifier.apply(Context.empty());

        @SuppressWarnings("unchecked")
        Map<String, String> headers = ctx.get(McpHeaderContext.CTX_KEY);
        assertEquals("Bearer abc", headers.get("Authorization"));
        assertEquals("tenant-42", headers.get("X-Tenant-Id"));
    }

    @Test
    @DisplayName("of(map) creates defensive copy — original map changes don't affect context")
    void ofMapIsDefensiveCopy() {
        java.util.HashMap<String, String> mutable = new java.util.HashMap<>();
        mutable.put("key", "val");
        Function<Context, Context> modifier = McpHeaderContext.of(mutable);
        Context ctx = modifier.apply(Context.empty());

        mutable.put("key", "changed");

        @SuppressWarnings("unchecked")
        Map<String, String> headers = ctx.get(McpHeaderContext.CTX_KEY);
        assertEquals("val", headers.get("key"));
    }

    @Test
    @DisplayName("bind() sets ThreadLocal from ContextView, current() reads it")
    void bindAndCurrent() {
        Context ctx = Context.of(McpHeaderContext.CTX_KEY, Map.of("X-Test", "value"));

        McpHeaderContext.bind(ctx);
        Map<String, String> current = McpHeaderContext.current();

        assertEquals("value", current.get("X-Test"));
    }

    @Test
    @DisplayName("bind() with empty headers does not set ThreadLocal")
    void bindWithEmptyHeaders() {
        Context ctx = Context.of(McpHeaderContext.CTX_KEY, Map.of());

        McpHeaderContext.bind(ctx);
        Map<String, String> current = McpHeaderContext.current();

        assertTrue(current.isEmpty());
    }

    @Test
    @DisplayName("bind() with no CTX_KEY in context does not set ThreadLocal")
    void bindWithNoKey() {
        Context ctx = Context.empty();

        McpHeaderContext.bind(ctx);
        Map<String, String> current = McpHeaderContext.current();

        assertTrue(current.isEmpty());
    }

    @Test
    @DisplayName("unbind() clears ThreadLocal — current() returns empty after unbind")
    void unbindClearsThreadLocal() {
        Context ctx = Context.of(McpHeaderContext.CTX_KEY, Map.of("key", "val"));
        McpHeaderContext.bind(ctx);

        assertEquals("val", McpHeaderContext.current().get("key"));

        McpHeaderContext.unbind();
        assertTrue(McpHeaderContext.current().isEmpty());
    }

    @Test
    @DisplayName("current() returns empty map when nothing is bound")
    void currentReturnsEmptyWhenUnbound() {
        Map<String, String> current = McpHeaderContext.current();
        assertNotNull(current);
        assertTrue(current.isEmpty());
    }

    @Test
    @DisplayName("CTX_KEY constant is 'mcp.request.headers'")
    void ctxKeyConstant() {
        assertEquals("mcp.request.headers", McpHeaderContext.CTX_KEY);
    }
}
