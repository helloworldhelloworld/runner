package com.lightweightai.mcp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.util.context.Context;

import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("McpHeaderContext — 请求级 Header 上下文桥接")
class McpHeaderContextTest {

    @AfterEach
    void cleanup() {
        McpHeaderContext.unbind();
    }

    @Test
    @DisplayName("of(key, value) 写入 Reactor Context 后 bind 可读取")
    void singleHeaderBindAndRead() {
        Function<Context, Context> modifier = McpHeaderContext.of("Authorization", "Bearer token123");
        Context ctx = modifier.apply(Context.empty());

        McpHeaderContext.bind(ctx);

        Map<String, String> headers = McpHeaderContext.current();
        assertEquals("Bearer token123", headers.get("Authorization"));
    }

    @Test
    @DisplayName("of(Map) 多个 header 写入后全部可读取")
    void multipleHeadersBindAndRead() {
        Map<String, String> inputHeaders = Map.of(
                "Authorization", "Bearer abc",
                "X-Tenant-Id", "tenant-42"
        );
        Function<Context, Context> modifier = McpHeaderContext.of(inputHeaders);
        Context ctx = modifier.apply(Context.empty());

        McpHeaderContext.bind(ctx);

        Map<String, String> result = McpHeaderContext.current();
        assertEquals("Bearer abc", result.get("Authorization"));
        assertEquals("tenant-42", result.get("X-Tenant-Id"));
    }

    @Test
    @DisplayName("unbind 后 current 返回空 Map")
    void unbindClearsCurrent() {
        Function<Context, Context> modifier = McpHeaderContext.of("X-Key", "value");
        McpHeaderContext.bind(modifier.apply(Context.empty()));

        assertFalse(McpHeaderContext.current().isEmpty());

        McpHeaderContext.unbind();
        assertTrue(McpHeaderContext.current().isEmpty());
    }

    @Test
    @DisplayName("未 bind 时 current 返回空 Map 而非 null")
    void currentReturnsEmptyMapWithoutBind() {
        Map<String, String> result = McpHeaderContext.current();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("bind 空 Context（无 CTX_KEY）不抛异常，current 返回空 Map")
    void bindEmptyContextSafe() {
        McpHeaderContext.bind(Context.empty());
        assertTrue(McpHeaderContext.current().isEmpty());
    }

    @Test
    @DisplayName("of(Map) 创建的是防御性拷贝 — 修改原 Map 不影响 Context")
    void defensiveCopy() {
        java.util.HashMap<String, String> mutable = new java.util.HashMap<>();
        mutable.put("X-Key", "v1");

        Function<Context, Context> modifier = McpHeaderContext.of(mutable);
        Context ctx = modifier.apply(Context.empty());

        mutable.put("X-Key", "v2");

        McpHeaderContext.bind(ctx);
        assertEquals("v1", McpHeaderContext.current().get("X-Key"));
    }

    @Test
    @DisplayName("重复 unbind 不抛异常")
    void doubleUnbindSafe() {
        McpHeaderContext.unbind();
        assertDoesNotThrow(McpHeaderContext::unbind);
    }
}
