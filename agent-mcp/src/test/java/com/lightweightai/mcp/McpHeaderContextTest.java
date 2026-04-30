package com.lightweightai.mcp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.util.context.Context;

import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("McpHeaderContext — per-request header 桥接 (Reactor Context ↔ ThreadLocal)")
class McpHeaderContextTest {

    @AfterEach
    void cleanup() {
        McpHeaderContext.unbind();
    }

    @Test
    @DisplayName("of(key, value) 创建单 header 的 Context 修改器")
    void singleHeaderContextModifier() {
        Function<Context, Context> modifier = McpHeaderContext.of("Authorization", "Bearer token123");
        Context ctx = modifier.apply(Context.empty());

        assertTrue(ctx.hasKey(McpHeaderContext.CTX_KEY));
        @SuppressWarnings("unchecked")
        Map<String, String> headers = ctx.get(McpHeaderContext.CTX_KEY);
        assertEquals("Bearer token123", headers.get("Authorization"));
    }

    @Test
    @DisplayName("of(map) 创建多 header 的 Context 修改器")
    void multiHeaderContextModifier() {
        Map<String, String> headers = Map.of(
                "Authorization", "Bearer abc",
                "X-Tenant-Id", "tenant-42"
        );
        Function<Context, Context> modifier = McpHeaderContext.of(headers);
        Context ctx = modifier.apply(Context.empty());

        @SuppressWarnings("unchecked")
        Map<String, String> stored = ctx.get(McpHeaderContext.CTX_KEY);
        assertEquals("Bearer abc", stored.get("Authorization"));
        assertEquals("tenant-42", stored.get("X-Tenant-Id"));
    }

    @Test
    @DisplayName("bind 从 ContextView 读取 header 并写入 ThreadLocal")
    void bindSetsThreadLocal() {
        Context ctx = Context.of(McpHeaderContext.CTX_KEY,
                Map.of("Authorization", "Bearer xyz"));

        McpHeaderContext.bind(ctx);

        Map<String, String> current = McpHeaderContext.current();
        assertEquals("Bearer xyz", current.get("Authorization"));
    }

    @Test
    @DisplayName("unbind 清除 ThreadLocal")
    void unbindClearsThreadLocal() {
        Context ctx = Context.of(McpHeaderContext.CTX_KEY,
                Map.of("Authorization", "Bearer xyz"));
        McpHeaderContext.bind(ctx);
        assertFalse(McpHeaderContext.current().isEmpty());

        McpHeaderContext.unbind();
        assertTrue(McpHeaderContext.current().isEmpty());
    }

    @Test
    @DisplayName("current() 无绑定时返回空 map 而不是 null")
    void currentReturnsEmptyMapWhenUnbound() {
        Map<String, String> current = McpHeaderContext.current();
        assertNotNull(current);
        assertTrue(current.isEmpty());
    }

    @Test
    @DisplayName("bind 空 header map 时 current() 返回空 map")
    void bindEmptyHeadersKeepsCurrentEmpty() {
        Context ctx = Context.of(McpHeaderContext.CTX_KEY, Map.of());
        McpHeaderContext.bind(ctx);

        assertTrue(McpHeaderContext.current().isEmpty());
    }

    @Test
    @DisplayName("ContextView 中无 CTX_KEY 时 bind 不报错")
    void bindWithoutKeyDoesNotThrow() {
        Context ctx = Context.empty();
        assertDoesNotThrow(() -> McpHeaderContext.bind(ctx));
        assertTrue(McpHeaderContext.current().isEmpty());
    }

    @Test
    @DisplayName("of(map) 创建的 header 是防御性拷贝")
    void headerMapIsDefensivelyCopied() {
        Map<String, String> mutable = new java.util.HashMap<>();
        mutable.put("key", "value");

        Function<Context, Context> modifier = McpHeaderContext.of(mutable);
        mutable.put("key2", "value2");

        Context ctx = modifier.apply(Context.empty());
        @SuppressWarnings("unchecked")
        Map<String, String> stored = ctx.get(McpHeaderContext.CTX_KEY);
        assertFalse(stored.containsKey("key2"), "Should be a defensive copy");
    }
}
