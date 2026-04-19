package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentLoop;
import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.agent.AgentResponse;
import com.lightweightai.kernel.core.StreamEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        // 2. 深度检查
        int currentDepth = request.currentDepth();
        if (profile.getMaxSpawnDepth() <= 0) {
            throw new IllegalStateException(
                    "Agent '" + agentId + "' cannot spawn subagents (maxSpawnDepth=0)");
        }
        if (currentDepth >= profile.getMaxSpawnDepth()) {
            throw new IllegalStateException(
                    "Spawn depth limit reached: current=" + currentDepth
                    + ", max=" + profile.getMaxSpawnDepth());
        }

        // 3. 并发检查 + 注册（原子操作，防止竞态）
        String runId = UUID.randomUUID().toString().substring(0, 8);
        String sessionKey = request.getParentSessionKey() + ":subagent:" + runId;
        SubagentRun run = new SubagentRun(runId, sessionKey, request);

        synchronized (activeRuns) {
            if (activeRuns.size() >= maxConcurrent) {
                throw new IllegalStateException(
                        "Max concurrent subagents reached: " + maxConcurrent);
            }
            activeRuns.put(runId, run);
        }

        logger.info("Spawning subagent: runId={}, agent={}, depth={}, task={}",
                runId, agentId, currentDepth + 1, request.getTask());

        // 4. 发出 SUBAGENT_SPAWN 事件
        announcer.accept(StreamEvent.subagentSpawn(runId, agentId, request.getTask()));

        // 5. 构建子 AgentLoop
        AgentLoop childAgent = agentFactory.create(profile);

        // 7. 异步执行
        Future<?> future = executor.submit(() -> {
            try {
                AgentResponse result = childAgent.run(request.getTask(), sessionKey);
                run.complete(result);

                String resultText = result != null ? result.getText() : null;
                logger.info("Subagent completed: runId={}, duration={}ms, result={}",
                        runId, run.getDurationMs(),
                        com.lightweightai.kernel.util.TextTruncator.truncate(resultText, 500));
                announcer.accept(StreamEvent.subagentComplete(
                        runId,
                        result != null ? result.getText() : "",
                        run.getDurationMs(),
                        0  // tokenUsage — 后续接入 CostTracker
                ));
            } catch (Exception e) {
                run.fail(e);
                logger.error("Subagent failed: runId={}", runId, e);
                announcer.accept(StreamEvent.subagentError(runId, e.getMessage()));
            } finally {
                activeRuns.remove(runId);
                cacheCompleted(runId, run);
            }
        });

        run.setFuture(future);
        return runId;
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
        SubagentRun run = activeRuns.get(runId);
        if (run == null) {
            logger.debug("Run {} not found, skip stop", runId);
            return;
        }

        logger.info("Stopping subagent: runId={}", runId);

        // 先停子 agent 的子 agent（级联：key 以当前 sessionKey 为前缀的都停）
        String prefix = run.getSessionKey();
        activeRuns.values().stream()
                .filter(r -> r.getSessionKey().startsWith(prefix) && !r.getRunId().equals(runId))
                .forEach(child -> {
                    child.cancel("Cascade stop from parent " + runId);
                    announcer.accept(StreamEvent.subagentCancelled(child.getRunId(),
                            "Cascade stop from parent " + runId));
                    activeRuns.remove(child.getRunId());
                });

        // 停当前
        run.cancel("Stopped by caller");
        announcer.accept(StreamEvent.subagentCancelled(runId, "Stopped by caller"));
        activeRuns.remove(runId);
    }

    /**
     * 停止所有活跃 subagent
     */
    public void stopAll() {
        activeRuns.values().forEach(run -> run.cancel("Runtime shutdown"));
        activeRuns.clear();
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
     * 等待指定 run 完成（测试用）
     */
    public boolean waitForCompletion(String runId, long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (System.currentTimeMillis() < deadline) {
            SubagentRun run = activeRuns.get(runId);
            if (run == null) return true;  // 已从 activeRuns 移除 = 完成
            if (!run.isRunning()) return true;
            Thread.sleep(50);
        }
        return false;
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
     * 已完成的 run 结果缓存（带 TTL 自动清理，防止内存泄漏）
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
