package com.lightweightai.kernel.trace;

import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.function.Function;

/**
 * Reactor Context 中 SpanContext 的读写辅助工具。
 *
 * 用于在响应式管道中传播 trace 上下文。
 */
public final class TraceContext {

    static final String CTX_KEY = "kernel.trace.span";

    private TraceContext() {}

    /**
     * 从 Reactor ContextView 中读取当前 SpanContext
     *
     * @return SpanContext，无则返回 null
     */
    public static SpanContext current(ContextView ctx) {
        return ctx.getOrDefault(CTX_KEY, null);
    }

    /**
     * 返回一个 contextWrite 函数，将 SpanContext 写入 Reactor Context
     */
    public static Function<Context, Context> put(SpanContext span) {
        return ctx -> ctx.put(CTX_KEY, span);
    }
}
