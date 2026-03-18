package com.lightweightai.mcp;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import reactor.util.context.ContextView;

/**
 * MCP 请求级 Header 上下文 — 解决「transport 启动时创建，header 请求时才知道」
 *
 * <h2>设计</h2>
 * <pre>
 * 调用方 contextWrite()          Reactor Context 自动传播          McpToolWrapper
 *   ↓                              ↓                              ↓
 * Flux 链入口写入              中间层无需改签名               deferContextual → bind()
 *                                                                ↓
 *                                                          ThreadLocal
 *                                                                ↓
 *                                                     transport customizeRequest
 *                                                        → current() 读取
 * </pre>
 *
 * <h2>调用方 API</h2>
 * <pre>
 * // 单个 header
 * flux.contextWrite(McpHeaderContext.of("Authorization", "Bearer " + token));
 *
 * // 多个 header
 * flux.contextWrite(McpHeaderContext.of(Map.of(
 *     "Authorization", "Bearer " + token,
 *     "X-Tenant-Id",   tenantId
 * )));
 * </pre>
 *
 * <h2>三层 header 优先级（customizeRequest 中）</h2>
 * <ol>
 *   <li>YAML 静态 headers — {@code config.getHeaders()}</li>
 *   <li>per-request headers — {@link #current()}（本类桥接）</li>
 *   <li>全局 McpHeaderProvider — 兜底</li>
 * </ol>
 *
 * @see McpToolWrapper#executeReactive
 * @see ToolClient.Builder#createTransport
 */
public final class McpHeaderContext {

    /** Reactor Context key */
    public static final String CTX_KEY = "mcp.request.headers";

    private static final ThreadLocal<Map<String, String>> HOLDER = new ThreadLocal<>();

    private McpHeaderContext() {}

    // ==================== 调用方 API ====================

    /**
     * 创建 Reactor Context 修改器 — 单个 header
     *
     * <pre>
     * flux.contextWrite(McpHeaderContext.of("Authorization", "Bearer xxx"));
     * </pre>
     */
    public static Function<reactor.util.context.Context, reactor.util.context.Context>
            of(String key, String value) {
        return ctx -> ctx.put(CTX_KEY, Map.of(key, value));
    }

    /**
     * 创建 Reactor Context 修改器 — 多个 header
     *
     * <pre>
     * flux.contextWrite(McpHeaderContext.of(Map.of(
     *     "Authorization", "Bearer xxx",
     *     "X-Tenant-Id",   "tenant-123"
     * )));
     * </pre>
     */
    public static Function<reactor.util.context.Context, reactor.util.context.Context>
            of(Map<String, String> headers) {
        Map<String, String> copy = Collections.unmodifiableMap(new HashMap<>(headers));
        return ctx -> ctx.put(CTX_KEY, copy);
    }

    // ==================== 框架内部 API ====================

    /**
     * 从 Reactor Context 读取 header 并绑定到当前线程 ThreadLocal
     *
     * 由 {@link McpToolWrapper#executeReactive} 在 MCP 调用前调用，
     * 配合 {@link #unbind()} 在 doFinally 中清理。
     */
    public static void bind(ContextView ctx) {
        @SuppressWarnings("unchecked")
        Map<String, String> headers = ctx.getOrDefault(CTX_KEY, Map.of());
        if (!headers.isEmpty()) {
            HOLDER.set(headers);
        }
    }

    /**
     * 清除当前线程绑定
     */
    public static void unbind() {
        HOLDER.remove();
    }

    /**
     * 获取当前线程绑定的 per-request header
     *
     * 由 transport 的 {@code customizeRequest} 回调调用。
     *
     * @return 当前请求的 header，不为 null（无绑定时返回空 map）
     */
    public static Map<String, String> current() {
        Map<String, String> h = HOLDER.get();
        return h != null ? h : Map.of();
    }
}
