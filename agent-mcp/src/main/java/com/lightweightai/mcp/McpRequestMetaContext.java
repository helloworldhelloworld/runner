package com.lightweightai.mcp;

import reactor.util.context.ContextView;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * MCP 请求级 metadata 上下文 — 通过 JSON-RPC 消息体的 {@code _meta.requestHeaders}
 * 传递请求级上下文（如 trace-id、device-id）。
 *
 * <p>与 {@link McpHeaderContext}（走 HTTP header）并列：
 * <ul>
 *   <li>{@code McpHeaderContext} → HTTP/SSE 的连接/请求 header（transport 层）</li>
 *   <li>{@code McpRequestMetaContext} → JSON-RPC body 的 {@code params._meta.requestHeaders}（消息体）</li>
 * </ul>
 *
 * <p>因为 metadata 随消息体传输，对任意 transport 均生效；忽略该字段的 HTTP Server 不受影响。
 * 复用 Reactor Context 机制，按调用传播，并发安全（不会在并发调用间串台）。
 *
 * <h2>调用方 API</h2>
 * <pre>
 * flux.contextWrite(McpRequestMetaContext.of(Map.of(
 *     "x-trace-id", traceId,
 *     "x-device-id", deviceId
 * )));
 * </pre>
 *
 * @see McpToolWrapper#executeReactive
 * @see McpToolClient#callToolReactive
 */
public final class McpRequestMetaContext {

    /** Reactor Context key */
    public static final String CTX_KEY = "mcp.request.meta";

    /** JSON-RPC {@code _meta} 中承载请求级 headers 的字段名 */
    public static final String META_FIELD = "requestHeaders";

    private McpRequestMetaContext() {}

    /**
     * 创建 Reactor Context 修改器 — 多个 metadata
     */
    public static Function<reactor.util.context.Context, reactor.util.context.Context>
            of(Map<String, String> meta) {
        Map<String, String> copy = Collections.unmodifiableMap(new HashMap<>(meta));
        return ctx -> ctx.put(CTX_KEY, copy);
    }

    /**
     * 从 Reactor Context 读取请求级 metadata
     *
     * @return 当前请求的 metadata，不为 null（无绑定时返回空 map）
     */
    @SuppressWarnings("unchecked")
    public static Map<String, String> current(ContextView ctx) {
        return ctx.getOrDefault(CTX_KEY, Map.of());
    }
}
