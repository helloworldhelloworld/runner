package com.lightweightai.mcp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.util.context.Context;

import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("McpHeaderContext - per-request header bridging")
class McpHeaderContextTest {

    @AfterEach
    void cleanup() {
        McpHeaderContext.unbind();
    }

    @Test
    void current_returnsEmptyMapByDefault() {
        Map<String, String> headers = McpHeaderContext.current();
        assertNotNull(headers);
        assertTrue(headers.isEmpty());
    }

    @Test
    void of_singleHeader_createsContextModifier() {
        Function<Context, Context> modifier = McpHeaderContext.of("Authorization", "Bearer token123");
        Context ctx = modifier.apply(Context.empty());

        assertTrue(ctx.hasKey(McpHeaderContext.CTX_KEY));
        @SuppressWarnings("unchecked")
        Map<String, String> headers = ctx.get(McpHeaderContext.CTX_KEY);
        assertEquals("Bearer token123", headers.get("Authorization"));
    }

    @Test
    void of_multipleHeaders_createsContextModifier() {
        Map<String, String> headerMap = Map.of(
            "Authorization", "Bearer xyz",
            "X-Tenant-Id", "tenant-1"
        );
        Function<Context, Context> modifier = McpHeaderContext.of(headerMap);
        Context ctx = modifier.apply(Context.empty());

        @SuppressWarnings("unchecked")
        Map<String, String> headers = ctx.get(McpHeaderContext.CTX_KEY);
        assertEquals("Bearer xyz", headers.get("Authorization"));
        assertEquals("tenant-1", headers.get("X-Tenant-Id"));
    }

    @Test
    void bind_makesHeadersAvailableViaCurrent() {
        Context ctx = Context.of(McpHeaderContext.CTX_KEY,
            Map.of("X-Custom", "value"));

        McpHeaderContext.bind(ctx);
        Map<String, String> current = McpHeaderContext.current();
        assertEquals("value", current.get("X-Custom"));
    }

    @Test
    void unbind_clearsThreadLocal() {
        Context ctx = Context.of(McpHeaderContext.CTX_KEY,
            Map.of("X-Custom", "value"));
        McpHeaderContext.bind(ctx);

        McpHeaderContext.unbind();
        assertTrue(McpHeaderContext.current().isEmpty());
    }

    @Test
    void bind_emptyHeaders_doesNotSetThreadLocal() {
        Context ctx = Context.empty();
        McpHeaderContext.bind(ctx);

        assertTrue(McpHeaderContext.current().isEmpty());
    }

    @Test
    void of_multipleHeaders_isUnmodifiable() {
        Map<String, String> headerMap = new java.util.HashMap<>();
        headerMap.put("key", "val");
        Function<Context, Context> modifier = McpHeaderContext.of(headerMap);
        Context ctx = modifier.apply(Context.empty());

        @SuppressWarnings("unchecked")
        Map<String, String> headers = ctx.get(McpHeaderContext.CTX_KEY);
        assertThrows(UnsupportedOperationException.class,
            () -> headers.put("new", "entry"));
    }
}
