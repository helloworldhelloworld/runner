package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentLoop;
import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.agent.AgentResponse;
import com.lightweightai.kernel.gateway.ChatHandler;
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
    private final Map<String, InterruptibleRun> activeRuns = new ConcurrentHashMap<>();

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
        activeRuns.entrySet().removeIf(entry -> {
            InterruptibleRun run = entry.getValue();
            if (run.isRunning()) return false; // 正在运行的不清理
            // INTERRUPTED / COMPLETED 超过 TTL 则清理
            if (now - run.getCreatedAt() > RUN_TTL_MS) {
                logger.debug("Cleaning up stale run: session={}, phase={}", entry.getKey(), run.getPhase());
                return true;
            }
            return false;
        });
    }

    @Override
    public GatewayResponse chat(GatewayRequest request) {
        // 同步路径：直接委托
        String agentId = router.route(request);
        AgentLoop agent = agentFactory.create(agentRegistry.get(agentId).orElseGet(agentRegistry::getDefault));

        long start = System.currentTimeMillis();
        var result = agent.run(request.getMessage(), resolveSessionKey(agentId, request.getSessionId()));
        return GatewayResponse.fromAgentResponse(result, request.getRequestId(),
                request.getSessionId(), System.currentTimeMillis() - start);
    }

    @Override
    public CompletableFuture<GatewayResponse> chatStream(GatewayRequest request, StreamCallback callback) {
        // 桥接 reactive → callback
        StringBuilder text = new StringBuilder();
        return chatStreamReactive(request)
                .doOnNext(event -> {
                    if (event.getType() == StreamEvent.EventType.TEXT_DELTA) {
                        String delta = event.getTextDelta();
                        text.append(delta != null ? delta : "");
                        callback.onDelta(delta, Map.of());
                    }
                })
                .then()
                .toFuture()
                .thenApply(v -> {
                    GatewayResponse resp = GatewayResponse.builder()
                            .requestId(request.getRequestId())
                            .sessionId(request.getSessionId())
                            .text(text.toString())
                            .build();
                    callback.onComplete(resp);
                    return resp;
                })
                .exceptionally(e -> {
                    callback.onError(e);
                    return GatewayResponse.error(request.getRequestId(),
                            request.getSessionId(), e.getMessage());
                });
    }

    @Override
    public Flux<StreamEvent> chatStreamReactive(GatewayRequest request) {
        String sessionId = request.getSessionId();
        String agentId = router.route(request);

        logger.info("Orchestrator routing: session={} → agent={}", sessionId, agentId);

        // AGENT_ROUTE 事件
        StreamEvent routeEvent = StreamEvent.agentRoute(agentId, sessionId);

        // 检查是否有活跃的 run（打断接续）
        InterruptibleRun existing = activeRuns.get(sessionId);
        if (existing != null && existing.isRunning()) {
            // 打断并接续
            logger.info("Interrupting active run for session={}", sessionId);
            StreamEvent interruptEvent = existing.interrupt();

            return Flux.concat(
                    Flux.just(routeEvent),
                    interruptEvent != null ? Flux.just(interruptEvent) : Flux.empty(),
                    existing.execute(request.getMessage())
            );
        }

        // 新建或复用已完成的 run
        String runId = UUID.randomUUID().toString();
        String sessionKey = resolveSessionKey(agentId, sessionId);
        AgentProfile profile = agentRegistry.get(agentId).orElseGet(agentRegistry::getDefault);
        AgentLoop agent = agentFactory.create(profile);

        // 如果 profile 允许 spawn 且 runtime 存在，动态注册 spawn_subagent 工具
        if (subagentRuntime != null && profile.getMaxSpawnDepth() > 0) {
            // spawn 事件通过 announcer 回调注入到当前 run 的事件流
            // 这里用一个简单的事件收集器，在 Flux 链中注入
            agent.getToolRegistry().register(
                    new SpawnSubagentTool(subagentRuntime, sessionKey, event -> {
                        logger.debug("Subagent event in session {}: {}", sessionId, event.getType());
                    }));
        }

        InterruptibleRun run = new InterruptibleRun(runId, sessionKey, agent, getMemoryProvider(agent));
        activeRuns.put(sessionId, run);

        return Flux.concat(
                Flux.just(routeEvent),
                run.execute(request.getMessage())
                        .doFinally(sig -> {
                            // 完成后从 activeRuns 移除（除非被打断）
                            InterruptibleRun current = activeRuns.get(sessionId);
                            if (current != null && !current.isRunning()
                                    && current.getPhase() != InterruptibleRun.RunPhase.INTERRUPTED) {
                                activeRuns.remove(sessionId);
                            }
                        })
        );
    }

    private String resolveSessionKey(String agentId, String sessionId) {
        // 命名空间化：agent:<agentId>:main:<sessionId>
        return "agent:" + agentId + ":main:" + sessionId;
    }

    /**
     * 从 AgentLoop 获取 MemoryProvider（通过 AgentFactory 注入的共享实例）
     */
    private MemoryProvider getMemoryProvider(AgentLoop agent) {
        // AgentLoop 当前没有暴露 memoryProvider getter
        // 暂时通过 AgentFactory 的共享引用获取
        return agentFactory.getSharedMemory();
    }
}
