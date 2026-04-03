package com.lightweightai.kernel.logging;

import com.lightweightai.kernel.trace.SpanContext;
import com.lightweightai.kernel.trace.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Operators;
import reactor.util.context.ContextView;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reactor ↔ SLF4J MDC 桥接 (Harness Engineering - Context Engineering)。
 *
 * Reactor 在不同线程上调度操作符，导致 MDC（ThreadLocal）丢失 traceId。
 * 此 Hook 在每个操作符执行前，从 Reactor Context 同步 trace 信息到 MDC。
 *
 * 同步字段：
 * - traceId: 追踪 ID
 * - spanId: 当前 span ID
 * - sessionId: 会话 ID（从 baggage）
 * - requestId: 请求 ID（从 baggage）
 *
 * 使用方式：在 Spring Boot 启动时调用一次 {@code MdcTraceHook.install()}
 */
public final class MdcTraceHook {

    private static final Logger logger = LoggerFactory.getLogger(MdcTraceHook.class);
    private static final String HOOK_KEY = "kernel.mdc.trace";
    private static final AtomicBoolean installed = new AtomicBoolean(false);

    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_SPAN_ID = "spanId";
    public static final String MDC_SESSION_ID = "sessionId";
    public static final String MDC_REQUEST_ID = "requestId";

    private MdcTraceHook() {}

    /**
     * 全局安装 MDC Hook（只需调用一次）
     */
    public static void install() {
        if (installed.compareAndSet(false, true)) {
            Hooks.onEachOperator(HOOK_KEY,
                Operators.lift((scannable, subscriber) -> new MdcContextSubscriber<>(subscriber)));
            logger.info("MdcTraceHook installed: Reactor Context → SLF4J MDC bridging active");
        }
    }

    /**
     * 卸载 MDC Hook
     */
    public static void uninstall() {
        if (installed.compareAndSet(true, false)) {
            Hooks.resetOnEachOperator(HOOK_KEY);
            logger.info("MdcTraceHook uninstalled");
        }
    }

    /**
     * 手动将 ContextView 中的 trace 信息同步到 MDC（用于非 Reactor 场景）
     */
    public static void syncToMdc(ContextView ctx) {
        SpanContext span = TraceContext.current(ctx);
        if (span != null) {
            MDC.put(MDC_TRACE_ID, span.getTraceId());
            MDC.put(MDC_SPAN_ID, span.getSpanId());
            Map<String, String> baggage = span.getAllBaggage();
            String sessionId = baggage.get("sessionId");
            String requestId = baggage.get("requestId");
            if (sessionId != null) MDC.put(MDC_SESSION_ID, sessionId);
            if (requestId != null) MDC.put(MDC_REQUEST_ID, requestId);
        }
    }

    /**
     * 清理 MDC 中的 trace 信息
     */
    public static void clearMdc() {
        MDC.remove(MDC_TRACE_ID);
        MDC.remove(MDC_SPAN_ID);
        MDC.remove(MDC_SESSION_ID);
        MDC.remove(MDC_REQUEST_ID);
    }

    /**
     * 包装 Subscriber，在 onNext/onComplete/onError 前同步 MDC
     */
    private static class MdcContextSubscriber<T> implements CoreSubscriber<T> {
        private final CoreSubscriber<? super T> actual;

        MdcContextSubscriber(CoreSubscriber<? super T> actual) {
            this.actual = actual;
        }

        @Override
        public reactor.util.context.Context currentContext() {
            return actual.currentContext();
        }

        @Override
        public void onSubscribe(org.reactivestreams.Subscription s) {
            actual.onSubscribe(s);
        }

        @Override
        public void onNext(T t) {
            syncToMdc(actual.currentContext());
            actual.onNext(t);
        }

        @Override
        public void onError(Throwable throwable) {
            syncToMdc(actual.currentContext());
            actual.onError(throwable);
        }

        @Override
        public void onComplete() {
            syncToMdc(actual.currentContext());
            actual.onComplete();
        }
    }
}
