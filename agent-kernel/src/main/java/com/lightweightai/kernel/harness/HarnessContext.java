package com.lightweightai.kernel.harness;

import com.lightweightai.kernel.agent.AgentObserver;
import com.lightweightai.kernel.trace.BaggageContext;
import com.lightweightai.kernel.trace.SpanContext;
import com.lightweightai.kernel.trace.SpanKind;
import com.lightweightai.kernel.trace.TraceContext;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 请求级统一上下文。
 *
 * 将 trace span、business baggage、harness 引用整合为一个对象，
 * 通过 Reactor Context 在整个请求链路中传播。
 *
 * 核心职责：
 * 1. 持有本次请求的 root span（自动创建子 span）
 * 2. 持有业务级 baggage（userId, sessionId 等）
 * 3. 持有 AgentHarness 引用（访问 tracer, taskScheduler, observers）
 * 4. Reactor Context 双向绑定
 */
public class HarnessContext {

    static final String CTX_KEY = "kernel.harness.context";

    private final String sessionId;
    private final String requestId;
    private final SpanContext rootSpan;
    private final Map<String, String> baggage;
    private final AgentHarness harness;

    HarnessContext(String sessionId, String requestId, SpanContext rootSpan,
                   Map<String, String> baggage, AgentHarness harness) {
        this.sessionId = sessionId;
        this.requestId = requestId;
        this.rootSpan = rootSpan;
        this.baggage = baggage != null ? new HashMap<>(baggage) : new HashMap<>();
        this.harness = harness;
    }

    // ==================== Span 操作 ====================

    /**
     * 创建子 span（默认 INTERNAL）
     */
    public SpanContext createChildSpan(String name) {
        return rootSpan.createChild(name, SpanKind.INTERNAL);
    }

    /**
     * 创建子 span（指定 kind）
     */
    public SpanContext createChildSpan(String name, SpanKind kind) {
        return rootSpan.createChild(name, kind);
    }

    /**
     * 获取 root span
     */
    public SpanContext getRootSpan() {
        return rootSpan;
    }

    // ==================== Observer 通知 ====================

    /**
     * 通知所有注册的 AgentObserver
     */
    public void notifyObservers(Consumer<AgentObserver> callback) {
        if (harness != null) {
            for (AgentObserver observer : harness.getObservers()) {
                try {
                    callback.accept(observer);
                } catch (Exception ignored) {
                    // observer 异常不影响主流程
                }
            }
        }
    }

    // ==================== Reactor Context 绑定 ====================

    /**
     * 将 HarnessContext 写入 Reactor Context
     */
    public Function<Context, Context> toReactorContext() {
        return ctx -> ctx
            .put(CTX_KEY, this)
            .put(TraceContext.CTX_KEY, rootSpan)
            .put(BaggageContext.CTX_KEY, Collections.unmodifiableMap(baggage));
    }

    /**
     * 从 Reactor ContextView 读取 HarnessContext
     *
     * @return HarnessContext 或 null
     */
    public static HarnessContext fromReactorContext(ContextView ctx) {
        return ctx.getOrDefault(CTX_KEY, null);
    }

    // ==================== Getters ====================

    public String getSessionId() { return sessionId; }
    public String getRequestId() { return requestId; }
    public Map<String, String> getBaggage() { return Collections.unmodifiableMap(baggage); }
    public AgentHarness getHarness() { return harness; }

    public String getBaggage(String key) {
        return baggage.get(key);
    }

    public void setBaggage(String key, String value) {
        baggage.put(key, value);
        rootSpan.setBaggage(key, value);
    }
}
