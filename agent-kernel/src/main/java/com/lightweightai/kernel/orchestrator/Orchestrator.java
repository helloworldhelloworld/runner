package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentLoop;
import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.agent.AgentResponse;
import com.lightweightai.kernel.gateway.ChatHandler;
import com.lightweightai.kernel.gateway.SessionKeys;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.gateway.GatewayResponse;
import com.lightweightai.kernel.memory.MemoryProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 多 Agent Orchestrator — ChatHandler 实现
 *
 * 对标 OpenClaw Agent Core 的 Orchestrator 组件：
 * - 按 AgentRouter 策略将请求路由到正确的 Agent
 * - 维护 per-session 的 InterruptibleRun 实现全双工打断接续
 * - 发出 AGENT_ROUTE 一等公民事件
 *
 * 插入方式：Gateway.builder().chatHandler(orchestrator) — 替代直连单 Agent
 */
public class Orchestrator implements ChatHandler {

    private static final Logger logger = LoggerFactory.getLogger(Orchestrator.class);

    private final AgentRegistry agentRegistry;
    private final AgentFactory agentFactory;
    private final AgentRouter router;
    private final SubagentRuntime subagentRuntime;

    // per-session 的活跃执行
    private final ActiveRunCoordinator activeRuns = new ActiveRunCoordinator();

    // 已订阅的主动触发源
    private final Map<String, Disposable> activeTriggers = new ConcurrentHashMap<>();

    // TTL 清理：超过此时间的非 RUNNING run 会被自动移除
    private static final long RUN_TTL_MS = 60 * 60 * 1000; // 1 hour
    private static final long CLEANUP_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes
    private final java.util.concurrent.ScheduledExecutorService cleanupExecutor;

    public Orchestrator(AgentRegistry agentRegistry, AgentFactory agentFactory, AgentRouter router) {
        this(agentRegistry, agentFactory, router, null);
    }

    public Orchestrator(AgentRegistry agentRegistry, AgentFactory agentFactory,
                        AgentRouter router, SubagentRuntime subagentRuntime) {
        this.agentRegistry = agentRegistry;
        this.agentFactory = agentFactory;
        this.router = router;
        this.subagentRuntime = subagentRuntime;

        // 启动定期清理任务
        this.cleanupExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
                r -> { Thread t = new Thread(r, "orchestrator-cleanup"); t.setDaemon(true); return t; });
        this.cleanupExecutor.scheduleAtFixedRate(this::cleanupStaleRuns,
                CLEANUP_INTERVAL_MS, CLEANUP_INTERVAL_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * 关闭清理线程 — 防止线程池泄漏。同时取消所有已订阅的 TriggerSource。
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        activeTriggers.values().forEach(Disposable::dispose);
        activeTriggers.clear();
        activeRuns.cancelAll(CancellationCause.SYSTEM_SHUTDOWN);
        logger.info("Orchestrator cleanup executor shut down");
    }

    /**
     * 订阅一个 {@link TriggerSource},把每条 request 喂给 {@link #chatStreamReactive}。
     *
     * 同名 source 重复订阅会取消旧的。事件流上的错误不会中断订阅 — 仅记录日志。
     */
    public void subscribeTriggerSource(TriggerSource source) {
        Disposable previous = activeTriggers.remove(source.name());
        if (previous != null) {
            previous.dispose();
            logger.info("Replacing existing TriggerSource: {}", source.name());
        }
        Disposable d = source.requests()
                .flatMap(req -> chatStreamReactive(req)
                        .onErrorResume(e -> {
                            logger.warn("Trigger '{}' run failed: {}", source.name(), e.getMessage());
                            return Flux.empty();
                        }))
                .subscribe(
                        ev -> { /* 主动循环触发的事件不回传给原始调用者 */ },
                        err -> logger.warn("TriggerSource '{}' subscription error: {}", source.name(), err.getMessage())
                );
        activeTriggers.put(source.name(), d);
        logger.info("Subscribed TriggerSource: {}", source.name());
    }

    /**
     * 取消订阅指定 source。已发出但还未消费完的 request 不保证被处理。
     */
    public void unsubscribeTriggerSource(String name) {
        Disposable d = activeTriggers.remove(name);
        if (d != null) {
            d.dispose();
            logger.info("Unsubscribed TriggerSource: {}", name);
        }
    }

    private void cleanupStaleRuns() {
        long now = System.currentTimeMillis();
        activeRuns.removeIf(run -> {
            if (run.isRunning()) return false; // 正在运行的不清理
            // INTERRUPTED / COMPLETED 超过 TTL 则清理
            if (now - run.getCreatedAt() > RUN_TTL_MS) {
                logger.debug("Cleaning up stale run: phase={}", run.getPhase());
                return true;
            }
            return false;
        });
    }

    @Override
    public GatewayResponse chat(GatewayRequest request) {
        // 同步路径：直接委托
        String agentId = router.route(request);
        AgentLoop agent = createAgent(agentRegistry.get(agentId).orElseGet(agentRegistry::getDefault),
                SessionKeys.main(request.getPrincipalId(), agentId, request.getSessionId()));

        long start = System.currentTimeMillis();
        var result = agent.run(request.getMessage(), SessionKeys.main(request.getPrincipalId(), agentId, request.getSessionId()), request.getToolExecutionContext());
        return GatewayResponse.fromAgentResponse(result, request.getRequestId(),
                request.getSessionId(), System.currentTimeMillis() - start);
    }

    @Override
    public CompletableFuture<GatewayResponse> chatStream(GatewayRequest request, StreamCallback callback) {
        // 桥接 reactive → callback
        StringBuilder text = new StringBuilder();
        Map<String, Object> completionMetadata = new java.util.LinkedHashMap<>();
        return chatStreamReactive(request)
                .doOnNext(event -> {
                    if (event.getType() == StreamEvent.EventType.TEXT_DELTA) {
                        String delta = event.getTextDelta();
                        text.append(delta != null ? delta : "");
                        callback.onDelta(delta, Map.of());
                    } else if (event.getType() == StreamEvent.EventType.BUDGET_STOPPED) {
                        completionMetadata.put("stopReason", "budget_exhausted");
                        completionMetadata.put("usage", event.getData());
                    } else if (event.getType() == StreamEvent.EventType.LLM_COMPLETE && event.getResponse() != null) {
                        completionMetadata.put("stopReason", event.getResponse().getStopReason());
                    }
                })
                .then(reactor.core.publisher.Mono.fromSupplier(() -> {
                    GatewayResponse resp = GatewayResponse.builder()
                            .requestId(request.getRequestId())
                            .sessionId(request.getSessionId())
                            .text(text.toString())
                            .metadata(completionMetadata)
                            .build();
                    callback.onComplete(resp);
                    return resp;
                }))
                .onErrorResume(e -> {
                    callback.onError(e);
                    return reactor.core.publisher.Mono.just(GatewayResponse.error(request.getRequestId(),
                            request.getSessionId(), e.getMessage()));
                }).toFuture();
    }

    @Override
    public Flux<StreamEvent> chatStreamReactive(GatewayRequest request) {
        // Gateway emits its own prefix first; merely building this Flux must not
        // allocate or interrupt a run that might never receive a subscription.
        return Flux.defer(() -> startStream(request));
    }

    private Flux<StreamEvent> startStream(GatewayRequest request) {
        String sessionId = SessionKeys.slot(request.getPrincipalId(), request.getSessionId());
        String agentId = router.route(request);

        logger.info("Orchestrator routing: session={} → agent={}", sessionId, agentId);

        // AGENT_ROUTE 事件
        StreamEvent routeEvent = StreamEvent.agentRoute(agentId, request.getSessionId());

        // 检查是否有活跃的 run（打断接续）
        InterruptibleRun existing = activeRuns.get(sessionId);
        if (existing != null && existing.isRunning()) {
            // 打断并接续
            logger.info("Interrupting active run for session={}", sessionId);
            StreamEvent interruptEvent = existing.interrupt();

            // barge-in：打断成立时，先让设备停播已下发的音频，再接续（R4）
            StreamEvent speechInterrupted = interruptEvent != null
                    ? StreamEvent.speechInterrupted(existing.getRunId(), "barge-in")
                    : null;

            return Flux.concat(
                    Flux.just(routeEvent),
                    interruptEvent != null ? Flux.just(interruptEvent) : Flux.empty(),
                    speechInterrupted != null ? Flux.just(speechInterrupted) : Flux.empty(),
                    Flux.defer(() -> existing.execute(request.getMessage(), request.getToolExecutionContext()))
            ).doFinally(sig -> releaseFinishedRun(sessionId, existing));
        }

        // 新建或复用已完成的 run
        String runId = UUID.randomUUID().toString();
        String sessionKey = SessionKeys.main(request.getPrincipalId(), agentId, request.getSessionId());
        AgentProfile profile = agentRegistry.get(agentId).orElseGet(agentRegistry::getDefault);
        AgentLoop agent = createAgent(profile, sessionKey);

        InterruptibleRun run = new InterruptibleRun(runId, sessionKey, agent, agent.getExecutionMemory(request.getToolExecutionContext(), sessionKey));
        activeRuns.put(sessionId, run);

        return Flux.concat(
                Flux.just(routeEvent),
                Flux.defer(() -> run.execute(request.getMessage(), request.getToolExecutionContext()))
        ).doFinally(sig -> releaseFinishedRun(sessionId, run));
    }

    private AgentLoop createAgent(AgentProfile profile, String sessionKey) {
        return subagentRuntime == null ? agentFactory.create(profile)
                : subagentRuntime.createRootAgent(agentFactory, profile, sessionKey);
    }

    private void releaseFinishedRun(String sessionId, InterruptibleRun run) {
        // Include cancellation during the route prefix, before execute() starts.
        // Identity guards cleanup against a replacement run for the same session.
        if (!run.isRunning() && run.getPhase() != InterruptibleRun.RunPhase.INTERRUPTED) {
            activeRuns.remove(sessionId, run);
        }
    }

    /**
     * 独立打断会话的在途 run（入站 barge-in，ADR-012）。
     *
     * 与 {@link #chatStreamReactive} 里"新消息到达时隐式打断"语义一致，但不需要新消息——
     * 供 Voice Gateway 在用户一开口时立即停播。打断成立则返回面向设备的
     * {@code SPEECH_INTERRUPTED}；无在途 run 返回 {@code null}。
     *
     * 被打断的 run 留在 {@code activeRuns}（phase=INTERRUPTED），后续新 chat 走新建分支覆盖之，
     * 与既有 interrupt/resume 状态机一致。
     */
    @Override
    public StreamEvent interrupt(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        InterruptibleRun existing = activeRuns.get(sessionId);
        if (existing == null || !existing.isRunning()) {
            return null;
        }
        logger.info("Standalone barge-in interrupting active run for session={}", sessionId);
        StreamEvent interruptEvent = existing.interrupt();
        if (interruptEvent == null) {
            return null;  // 打断未成立（竞态：run 恰好已结束）
        }
        return StreamEvent.speechInterrupted(existing.getRunId(), "barge-in");
    }

    @Override
    public StreamEvent interrupt(String principalId, String sessionId) {
        return interrupt(SessionKeys.slot(principalId, sessionId));
    }

    @Override
    public void cancel(String principalId, String sessionId, String cause) {
        if (sessionId == null) return;
        InterruptibleRun run = activeRuns.get(SessionKeys.slot(principalId, sessionId));
        if (run == null) return;
        CancellationCause cancellationCause;
        try {
            cancellationCause = CancellationCause.valueOf(cause == null ? "CLIENT_DISCONNECTED" : cause);
        } catch (IllegalArgumentException ignored) {
            cancellationCause = CancellationCause.CLIENT_DISCONNECTED;
        }
        run.cancel(cancellationCause);
    }

    void clearExecutionScope(String principalId, String agentId, String sessionId) {
        interrupt(principalId, sessionId);
        if (subagentRuntime != null) {
            subagentRuntime.stopForRootSession(SessionKeys.main(principalId, agentId, sessionId));
        }
    }

}
