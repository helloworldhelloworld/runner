package com.lightweightai.kernel.harness;

import com.lightweightai.kernel.agent.AgentObserver;
import com.lightweightai.kernel.trace.SpanContext;
import com.lightweightai.kernel.trace.SpanKind;
import com.lightweightai.kernel.trace.TracePropagator;
import com.lightweightai.kernel.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent Harness — 统一运行时容器 (Harness Engineering)。
 *
 * 三大支柱的协调者：
 * 1. Context Engineering: Tracer + TracePropagator + BaggageContext
 * 2. Architectural Constraints: ConstraintRegistry + AgentObserver
 * 3. Entropy Management: TaskScheduler (通过外部注入)
 *
 * AgentHarness 不替代 Gateway 或 AgentLoop，而是作为它们的基础设施提供者。
 * Gateway 和 AgentLoop 持有 harness 引用，从中获取 tracer、constraints、observers。
 *
 * 生命周期：
 * - start(): 启动所有子系统
 * - close(): 优雅关闭所有子系统
 */
public class AgentHarness implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(AgentHarness.class);

    private final Tracer tracer;
    private final ConstraintRegistry constraintRegistry;
    private final List<AgentObserver> observers;
    private volatile boolean started = false;

    private AgentHarness(Builder builder) {
        this.tracer = builder.tracer != null ? builder.tracer : Tracer.NOOP;
        this.constraintRegistry = builder.constraintRegistry != null
            ? builder.constraintRegistry : new ConstraintRegistry();
        this.observers = Collections.unmodifiableList(new ArrayList<>(builder.observers));
    }

    // ==================== 生命周期 ====================

    /**
     * 启动 Harness 所有子系统
     */
    public synchronized void start() {
        if (started) return;
        started = true;
        logger.info("AgentHarness started: tracer={}, constraints={}, observers={}",
            tracer.isEnabled() ? "enabled" : "noop",
            constraintRegistry.size(),
            observers.size());
    }

    /**
     * 关闭 Harness 所有子系统
     */
    @Override
    public synchronized void close() {
        if (!started) return;
        started = false;
        logger.info("AgentHarness closed");
    }

    // ==================== 请求级上下文工厂 ====================

    /**
     * 创建请求级 HarnessContext（无外部 trace）
     */
    public HarnessContext createContext(String sessionId, String requestId) {
        return createContext(sessionId, requestId, null);
    }

    /**
     * 创建请求级 HarnessContext
     *
     * 如果 incomingHeaders 包含 W3C traceparent，则接续外部 trace；
     * 否则创建新的 root trace。
     *
     * @param sessionId  会话 ID
     * @param requestId  请求 ID
     * @param incomingHeaders HTTP headers（可选，用于 trace 传播）
     */
    public HarnessContext createContext(String sessionId, String requestId,
                                        Map<String, String> incomingHeaders) {
        // 1. 从 headers extract 或创建 root span
        SpanContext rootSpan;
        if (incomingHeaders != null && !incomingHeaders.isEmpty()) {
            rootSpan = TracePropagator.extract(incomingHeaders, "gateway.handle");
        } else {
            rootSpan = tracer.startTrace("gateway.handle", SpanKind.SERVER);
        }

        // 2. 设置基础属性
        rootSpan.setAttribute("sessionId", sessionId);
        rootSpan.setAttribute("requestId", requestId);

        // 3. 初始化 baggage
        Map<String, String> baggage = new HashMap<>();
        baggage.put("sessionId", sessionId);
        baggage.put("requestId", requestId);
        rootSpan.setBaggage("sessionId", sessionId);
        rootSpan.setBaggage("requestId", requestId);

        return new HarnessContext(sessionId, requestId, rootSpan, baggage, this);
    }

    // ==================== 子系统访问 ====================

    public Tracer getTracer() { return tracer; }
    public ConstraintRegistry getConstraints() { return constraintRegistry; }
    public List<AgentObserver> getObservers() { return observers; }
    public boolean isStarted() { return started; }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Tracer tracer;
        private ConstraintRegistry constraintRegistry;
        private final List<AgentObserver> observers = new ArrayList<>();

        public Builder tracer(Tracer tracer) {
            this.tracer = tracer;
            return this;
        }

        public Builder constraintRegistry(ConstraintRegistry registry) {
            this.constraintRegistry = registry;
            return this;
        }

        public Builder addObserver(AgentObserver observer) {
            this.observers.add(observer);
            return this;
        }

        public Builder observers(List<AgentObserver> observers) {
            this.observers.addAll(observers);
            return this;
        }

        public AgentHarness build() {
            return new AgentHarness(this);
        }
    }
}
