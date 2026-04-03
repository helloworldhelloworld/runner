package com.lightweightai.kernel.trace;

import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 业务级上下文在 Reactor Context 中的传播。
 *
 * Baggage 用于跨 span 传播业务数据（不同于 attributes 只属于单个 span）：
 * - userId: 用户标识
 * - sessionId: 会话标识
 * - model: 当前使用的 LLM 模型
 * - costBudget: 成本预算
 *
 * 使用方式:
 * <pre>
 *   // 写入
 *   flux.contextWrite(BaggageContext.put(Map.of("userId", "u1")))
 *
 *   // 读取
 *   Map&lt;String, String&gt; baggage = BaggageContext.current(ctx);
 * </pre>
 */
public final class BaggageContext {

    static final String CTX_KEY = "kernel.trace.baggage";

    private BaggageContext() {}

    /**
     * 从 Reactor ContextView 读取当前 baggage
     */
    public static Map<String, String> current(ContextView ctx) {
        return ctx.getOrDefault(CTX_KEY, Collections.emptyMap());
    }

    /**
     * 将 baggage 写入 Reactor Context
     */
    public static Function<Context, Context> put(Map<String, String> baggage) {
        return ctx -> ctx.put(CTX_KEY, Collections.unmodifiableMap(new HashMap<>(baggage)));
    }

    /**
     * 合并新 baggage 到现有 context（不覆盖已有 key）
     */
    public static Function<Context, Context> merge(Map<String, String> additional) {
        return ctx -> {
            Map<String, String> existing = ctx.getOrDefault(CTX_KEY, Collections.emptyMap());
            Map<String, String> merged = new HashMap<>(existing);
            additional.forEach(merged::putIfAbsent);
            return ctx.put(CTX_KEY, Collections.unmodifiableMap(merged));
        };
    }

    /**
     * 设置单个 baggage 值
     */
    public static Function<Context, Context> set(String key, String value) {
        return ctx -> {
            Map<String, String> existing = ctx.getOrDefault(CTX_KEY, Collections.emptyMap());
            Map<String, String> updated = new HashMap<>(existing);
            updated.put(key, value);
            return ctx.put(CTX_KEY, Collections.unmodifiableMap(updated));
        };
    }
}
