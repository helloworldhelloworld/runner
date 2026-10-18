package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentLoop;
import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.agent.AgentResponse;
import com.lightweightai.kernel.agent.ToolPolicy;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.CancellationToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Subagent 运行时 — 对标 OpenClaw subagent 机制
 *
 * 核心能力：
 * - 非阻塞 spawn — 立即返回 runId
 * - 深度控制 — maxSpawnDepth 限制嵌套层级
 * - 并发限制 — maxConcurrent 全局上限
 * - push-based announce — 通过 Consumer<StreamEvent> 回调通知
 * - 级联停止 — stop(runId) 取消该 run 及其所有子 run
 *
 * 事件（一等公民）：
 * - SUBAGENT_SPAWN: spawn 时立即发出
 * - SUBAGENT_COMPLETE: 子 agent 完成
 * - SUBAGENT_ERROR: 子 agent 失败
 * - SUBAGENT_CANCELLED: 被级联停止
 */
public class SubagentRuntime {

    private static final Logger logger = LoggerFactory.getLogger(SubagentRuntime.class);

    private final AgentFactory agentFactory;
    private final AgentRegistry agentRegistry;
    private final int maxConcurrent;
    private final Map<String, SubagentRun> activeRuns = new ConcurrentHashMap<>();
    private final ExecutorService executor;

    public SubagentRuntime(AgentFactory agentFactory, AgentRegistry agentRegistry, int maxConcurrent) {
        this.agentFactory = agentFactory;
        this.agentRegistry = agentRegistry;
        this.maxConcurrent = maxConcurrent;
        this.executor = Executors.newFixedThreadPool(maxConcurrent,
                r -> { Thread t = new Thread(r, "subagent-worker"); t.setDaemon(true); return t; });
    }

    /**
     * 非阻塞 spawn — 立即返回 runId
     *
     * @param request  spawn 请求
     * @param announcer 事件通知回调（push-based）
     * @return runId
     * @throws IllegalStateException 深度超限或并发超限
     */
    public String spawn(SpawnRequest request, Consumer<StreamEvent> announcer) {
        // 1. 解析目标 agent profile
        String agentId = request.getAgentId() != null
                ? request.getAgentId()
                : agentRegistry.getDefault().getAgentId();
        AgentProfile profile = agentRegistry.get(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent '" + agentId + "' not found"));

        int currentDepth = request.currentDepth();
        if (request.inheritedPolicy() == null) {
            // Preserve the legacy direct API's target-profile depth rules.
            if (profile.getMaxSpawnDepth() <= 0) {
                throw new IllegalStateException("Agent '" + agentId + "' cannot spawn subagents (maxSpawnDepth=0)");
            }
            if (currentDepth >= profile.getMaxSpawnDepth()) {
                throw new IllegalStateException("Spawn depth limit reached: current=" + currentDepth
                        + ", max=" + profile.getMaxSpawnDepth());
            }
        } else if (request.remainingSpawnDepth() == null || request.remainingSpawnDepth() < 0) {
            throw new IllegalStateException("Spawn depth limit reached");
        }

        // 3. 并发检查 + 注册（原子操作，防止竞态）
        String runId = UUID.randomUUID().toString().substring(0, 8);
        String sessionKey = request.getParentSessionKey() + ":subagent:" + runId;
        SubagentRun run = new SubagentRun(runId, sessionKey, request);
        CancellationToken foreground = request.executionContext() != null
                ? request.executionContext().getCancellationToken() : null;

        synchronized (activeRuns) {
            if (foreground != null && foreground.isCancelled()) {
                throw new IllegalStateException("Foreground execution is cancelled");
            }
            if (request.parentRun() != null && !request.parentRun().isRunning()) {
                throw new IllegalStateException("Parent task is no longer running");
            }
            if (activeRuns.size() >= maxConcurrent) {
                throw new IllegalStateException(
                        "Max concurrent subagents reached: " + maxConcurrent);
            }
            activeRuns.put(runId, run);
        }

        logger.info("Spawning subagent: runId={}, agent={}, depth={}, task={}",
                runId, agentId, currentDepth + 1, request.getTask());

        announce(announcer, StreamEvent.subagentSpawn(runId, agentId, request.getTask()));
        try {
            if (foreground != null) {
                run.setCancellationRegistration(foreground.onCancel(() -> stop(runId, announcer)));
            }
            if (!run.isRunning()) return runId;
            AgentLoop childAgent = request.inheritedPolicy() == null
                    ? agentFactory.create(profile)
                    : createAgent(agentFactory, profile, sessionKey, request.inheritedPolicy(),
                            Math.min(request.remainingSpawnDepth(), Math.max(0, profile.getMaxSpawnDepth())), run);
            Future<?> future = executor.submit(() -> {
                try {
                    if (!run.isRunning()) return;
                    AgentResponse result = request.executionContext() == null
                            ? childAgent.run(request.getTask(), sessionKey)
                            : childAgent.run(request.getTask(), sessionKey, request.executionContext());
                    if (result != null && "budget_exhausted".equals(result.getStopReason())) {
                        throw new IllegalStateException("budget_exhausted: shared root budget cannot admit another model call");
                    }
                    if (run.tryComplete(result)) {
                        announce(announcer, StreamEvent.subagentComplete(runId,
                                result != null ? result.getText() : "", run.getDurationMs(), 0));
                    }
                } catch (Exception e) {
                    if (run.tryFail(e)) announce(announcer, StreamEvent.subagentError(runId, run.getErrorMessage()));
                } finally {
                    finish(run);
                }
            });
            run.setFuture(future);
            return runId;
        } catch (RuntimeException e) {
            if (run.tryFail(e)) announce(announcer, StreamEvent.subagentError(runId, run.getErrorMessage()));
            finish(run);
            throw e;
        }
    }

    AgentLoop createRootAgent(AgentFactory factory, AgentProfile profile, String sessionKey) {
        return createAgent(factory, profile, sessionKey, ToolPolicy.allowAll(), Math.max(0, profile.getMaxSpawnDepth()), null);
    }

    private AgentLoop createAgent(AgentFactory factory, AgentProfile profile, String sessionKey,
                                  ToolPolicy inherited, int remainingDepth, SubagentRun run) {
        ToolPolicy ownPolicy = request -> profile.getToolPolicy().evaluate(new ToolPolicy.ToolPolicyRequest(
                request.toolName(), request.tool(), request.args(), profile, request.executionContext()));
        ToolPolicy effective = ToolPolicy.intersection(inherited, ownPolicy,
                remainingDepth > 0 ? ToolPolicy.allowAll() : ToolPolicy.denyList(Set.of("spawn_subagent")));
        // Current task liveness must not be inherited: an independent descendant
        // may legitimately outlive the parent that spawned it.
        ToolPolicy executionPolicy = ToolPolicy.intersection(effective,
                request -> run == null || run.isRunning() ? ToolPolicy.Decision.ABSTAIN : ToolPolicy.Decision.DENY);
        TaskScope scope = run == null ? TaskScope.root(sessionKey)
                : run.getRequest().ownerScope().child(run.getRunId());
        return factory.create(profile, List.of(
                new SpawnSubagentTool(this, sessionKey, event -> logger.debug("Subagent event: {}", event.getType()),
                        effective, remainingDepth, run, scope),
                new WaitSubagentTool(this, scope),
                new ListSubagentsTool(this, scope),
                new StopSubagentTool(this, scope)), executionPolicy);
    }

    private void announce(Consumer<StreamEvent> announcer, StreamEvent event) {
        try {
            if (announcer != null) announcer.accept(event);
        } catch (RuntimeException e) {
            logger.warn("Subagent event consumer failed for {}", event.getType(), e);
        }
    }

    private void finish(SubagentRun run) {
        run.releaseCancellationRegistration();
        synchronized (activeRuns) {
            cacheCompleted(run.getRunId(), run);
            activeRuns.remove(run.getRunId(), run);
        }
    }

    /**
     * 级联停止
     */
    public void stop(String runId) {
        stop(runId, e -> {});
    }

    /**
     * 级联停止（带事件回调）
     */
    public void stop(String runId, Consumer<StreamEvent> announcer) {
        List<SubagentRun> cancelled = new ArrayList<>();
        synchronized (activeRuns) {
            SubagentRun root = getRunOrCompleted(runId);
            if (root == null) return;
            List<SubagentRun> descendants = new ArrayList<>();
            descendants.add(root);
            for (SubagentRun candidate : activeRuns.values()) {
                if (candidate != root && isDescendantOf(candidate, root)) descendants.add(candidate);
            }
            for (SubagentRun run : descendants) {
                if (run.tryCancel("Stopped by caller; cascade root " + runId)) cancelled.add(run);
                finish(run);
            }
        }
        for (SubagentRun run : cancelled) {
            announce(announcer, StreamEvent.subagentCancelled(run.getRunId(), run.getErrorMessage()));
        }
    }

    private boolean isDescendantOf(SubagentRun candidate, SubagentRun root) {
        if (candidate.getRequest().ownerScope() == null) {
            // Legacy administrative requests encode ancestry in the session key.
            return candidate.getSessionKey().startsWith(root.getSessionKey() + ":subagent:");
        }
        // Keep following actual parents after they have left the active/cache tables.
        for (SubagentRun parent = candidate.getRequest().parentRun(); parent != null;
             parent = parent.getRequest().parentRun()) {
            if (parent == root) return true;
        }
        return false;
    }

    SubagentRun getOwnedRun(String runId, TaskScope ownerScope) {
        SubagentRun run = getRunOrCompleted(runId);
        return run != null && ownerScope.equals(run.getRequest().ownerScope()) ? run : null;
    }

    /** Session clearing also stops independent descendants after their foreground has completed. */
    void stopForRootSession(String rootSessionKey) {
        synchronized (activeRuns) {
            for (SubagentRun run : List.copyOf(activeRuns.values())) {
                TaskScope owner = run.getRequest().ownerScope();
                if (owner != null && owner.rootSessionKey().equals(rootSessionKey)) {
                    run.tryCancel("Root session was cleared");
                    finish(run);
                }
            }
        }
    }

    List<SubagentRun> getOwnedActiveRuns(TaskScope ownerScope) {
        return getActiveRuns().stream().filter(run -> ownerScope.equals(run.getRequest().ownerScope())).toList();
    }

    boolean stopOwned(String runId, TaskScope ownerScope) {
        synchronized (activeRuns) {
            if (getOwnedRun(runId, ownerScope) == null) return false;
            stop(runId);
            return true;
        }
    }

    /**
     * 停止所有活跃 subagent
     */
    public void stopAll() {
        synchronized (activeRuns) {
            for (SubagentRun run : List.copyOf(activeRuns.values())) {
                run.cancel("Runtime shutdown");
                finish(run);
            }
        }
    }

    /**
     * 关闭线程池 — 防止泄漏
     */
    public void shutdown() {
        stopAll();
        executor.shutdown();
        logger.info("SubagentRuntime executor shut down");
    }

    /**
     * 等待指定 run 的终态信号
     */
    public boolean waitForCompletion(String runId, long timeout, TimeUnit unit) throws InterruptedException {
        SubagentRun run = getRunOrCompleted(runId);
        return run == null || run.await(timeout, unit);
    }

    public int getActiveCount() { return activeRuns.size(); }

    /**
     * 获取活跃 run（用于 list/wait）
     */
    public SubagentRun getRun(String runId) {
        return activeRuns.get(runId);
    }

    /**
     * 获取所有活跃 run 的快照
     */
    public List<SubagentRun> getActiveRuns() {
        return List.copyOf(activeRuns.values());
    }

    /**
     * 已完成的 run 结果缓存（按数量设上限，防止无限增长）
     *
     * key=runId, value=SubagentRun
     * 缓存上限 MAX_COMPLETED_CACHE，超过时删除最旧的。
     */
    private static final int MAX_COMPLETED_CACHE = 200;
    private final Map<String, SubagentRun> completedRuns = new ConcurrentHashMap<>();

    private void cacheCompleted(String runId, SubagentRun run) {
        completedRuns.put(runId, run);
        // 超过上限时清理最旧的
        if (completedRuns.size() > MAX_COMPLETED_CACHE) {
            completedRuns.entrySet().stream()
                    .min(java.util.Comparator.comparingLong(e -> e.getValue().getStartTime()))
                    .ifPresent(oldest -> completedRuns.remove(oldest.getKey()));
        }
    }

    /**
     * 获取已完成的 run（包括 active 和 completed 缓存）
     */
    public SubagentRun getRunOrCompleted(String runId) {
        SubagentRun run = activeRuns.get(runId);
        return run != null ? run : completedRuns.get(runId);
    }
}
