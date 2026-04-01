package com.lightweightai.mcp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.util.context.Context;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("McpHeaderContext - 请求级 Header 上下文")
class McpHeaderContextTest {

    @AfterEach
    void cleanup() {
        McpHeaderContext.unbind();
    }

    @Test
    @DisplayName("of 单个 header 写入 Reactor Context")
    void ofSingleHeader() {
        var modifier = McpHeaderContext.of("Authorization", "Bearer token123");
        Context ctx = modifier.apply(Context.empty());

        assertTrue(ctx.hasKey(McpHeaderContext.CTX_KEY));
        @SuppressWarnings("unchecked")
        Map<String, String> headers = ctx.get(McpHeaderContext.CTX_KEY);
        assertEquals("Bearer token123", headers.get("Authorization"));
    }

    @Test
    @DisplayName("of 多个 header 写入 Reactor Context")
    void ofMultipleHeaders() {
        var modifier = McpHeaderContext.of(Map.of(
                "Authorization", "Bearer abc",
                "X-Tenant-Id", "tenant-1"
        ));
        Context ctx = modifier.apply(Context.empty());

        @SuppressWarnings("unchecked")
        Map<String, String> headers = ctx.get(McpHeaderContext.CTX_KEY);
        assertEquals("Bearer abc", headers.get("Authorization"));
        assertEquals("tenant-1", headers.get("X-Tenant-Id"));
    }

    @Test
    @DisplayName("bind 和 current 配合使用")
    void bindAndCurrent() {
        Context ctx = McpHeaderContext.of("X-Test", "value1")
                .apply(Context.empty());

        McpHeaderContext.bind(ctx);
        Map<String, String> headers = McpHeaderContext.current();
        assertEquals("value1", headers.get("X-Test"));
    }

    @Test
    @DisplayName("unbind 后 current 返回空 map")
    void unbindClearsHeaders() {
        Context ctx = McpHeaderContext.of("X-Test", "value1")
                .apply(Context.empty());

        McpHeaderContext.bind(ctx);
        McpHeaderContext.unbind();

        Map<String, String> headers = McpHeaderContext.current();
        assertTrue(headers.isEmpty());
    }

    @Test
    @DisplayName("未 bind 时 current 返回空 map")
    void currentWithoutBind() {
        Map<String, String> headers = McpHeaderContext.current();
        assertNotNull(headers);
        assertTrue(headers.isEmpty());
    }

    @Test
    @DisplayName("bind 空 context 不设置 ThreadLocal")
    void bindEmptyContext() {
        McpHeaderContext.bind(Context.empty());
        assertTrue(McpHeaderContext.current().isEmpty());
    }

    @Test
    @DisplayName("of 多 header 返回不可变 map")
    void ofMultipleHeadersImmutable() {
        var modifier = McpHeaderContext.of(Map.of("k", "v"));
        Context ctx = modifier.apply(Context.empty());

        @SuppressWarnings("unchecked")
        Map<String, String> headers = ctx.get(McpHeaderContext.CTX_KEY);
        assertThrows(UnsupportedOperationException.class, () ->
                headers.put("new", "val"));
    }
}
