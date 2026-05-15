package com.lightweightai.mcp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.util.context.Context;

import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("McpHeaderContext - 请求级 Header 上下文桥接")
class McpHeaderContextTest {

    @AfterEach
    void cleanup() {
        McpHeaderContext.unbind();
    }

    @Nested
    @DisplayName("Reactor Context 修改器")
    class ContextModifiers {

        @Test
        @DisplayName("of(key, value) 创建单个 header 的 Context 修改器")
        void singleHeaderModifier() {
            Function<Context, Context> modifier = McpHeaderContext.of("Authorization", "Bearer abc");
            Context ctx = modifier.apply(Context.empty());

            @SuppressWarnings("unchecked")
            Map<String, String> headers = ctx.get(McpHeaderContext.CTX_KEY);
            assertEquals("Bearer abc", headers.get("Authorization"));
        }

        @Test
        @DisplayName("of(map) 创建多个 header 的 Context 修改器")
        void multiHeaderModifier() {
            Function<Context, Context> modifier = McpHeaderContext.of(Map.of(
                "Authorization", "Bearer token",
                "X-Tenant-Id", "tenant-42"
            ));
            Context ctx = modifier.apply(Context.empty());

            @SuppressWarnings("unchecked")
            Map<String, String> headers = ctx.get(McpHeaderContext.CTX_KEY);
            assertEquals("Bearer token", headers.get("Authorization"));
            assertEquals("tenant-42", headers.get("X-Tenant-Id"));
        }
    }

    @Nested
    @DisplayName("ThreadLocal 绑定与解绑")
    class BindUnbind {

        @Test
        @DisplayName("bind 从 Reactor Context 写入 ThreadLocal")
        void bindWritesToThreadLocal() {
            Context ctx = Context.of(McpHeaderContext.CTX_KEY,
                Map.of("X-Custom", "value123"));

            McpHeaderContext.bind(ctx);

            Map<String, String> current = McpHeaderContext.current();
            assertEquals("value123", current.get("X-Custom"));
        }

        @Test
        @DisplayName("unbind 清除 ThreadLocal")
        void unbindClearsThreadLocal() {
            Context ctx = Context.of(McpHeaderContext.CTX_KEY, Map.of("k", "v"));
            McpHeaderContext.bind(ctx);

            McpHeaderContext.unbind();

            assertTrue(McpHeaderContext.current().isEmpty());
        }

        @Test
        @DisplayName("current 无绑定时返回空 Map")
        void currentReturnsEmptyWhenUnbound() {
            Map<String, String> headers = McpHeaderContext.current();

            assertNotNull(headers);
            assertTrue(headers.isEmpty());
        }

        @Test
        @DisplayName("bind 空 header 不设置 ThreadLocal")
        void bindEmptyHeadersSkipsThreadLocal() {
            Context ctx = Context.of(McpHeaderContext.CTX_KEY, Map.of());

            McpHeaderContext.bind(ctx);

            assertTrue(McpHeaderContext.current().isEmpty());
        }

        @Test
        @DisplayName("bind 无 CTX_KEY 的 Context 不设置 ThreadLocal")
        void bindMissingKeySkipsThreadLocal() {
            Context ctx = Context.empty();

            McpHeaderContext.bind(ctx);

            assertTrue(McpHeaderContext.current().isEmpty());
        }
    }
}
