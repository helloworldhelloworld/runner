package com.lightweightai.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 将 McpAsyncClient 全局的 progressConsumer 回调
 * 路由到每次独立工具调用对应的 Flux sink。
 *
 * MCP 的 ProgressNotification 携带 progressToken，
 * 每次 callTool 请求中通过 _meta.progressToken 传入唯一 token，
 * 服务端在执行过程中通过同一个 token 推送进度通知，
 * Router 根据 token 查找对应的 sink 并分发。
 *
 * <h2>终帧不丢（issue #197）</h2>
 * 进度「终帧」（{@code progress >= total}）常常携带终态/失败上下文。若在 tool result 返回后
 * 立即 {@link #complete(String)} 关闭 sink，而终帧此刻仍在途中，其 {@link #route} 会落在已关闭的
 * sink 上被静默丢弃。{@link #completeAfterTerminalOrGrace(String, Duration)} 用「等终帧或有界 grace
 * 兜底」的收口语义避免这一竞态，同时对无进度/无终帧/出错的工具立即或有界完成，绝不挂死。
 */
public class ProgressNotificationRouter {

    private final ConcurrentHashMap<String, Sinks.Many<McpSchema.ProgressNotification>> sinks
            = new ConcurrentHashMap<>();

    /** 该 token 是否出现过任何进度帧（无活动的工具不应被 grace 拖延）。 */
    private final Set<String> activity = ConcurrentHashMap.newKeySet();
    /** 该 token 是否已见终帧（progress>=total）。 */
    private final Set<String> terminalSeen = ConcurrentHashMap.newKeySet();
    /** 正在等待 grace 兜底的收口任务，便于终帧到达时提前收口 / 取消。 */
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingGrace = new ConcurrentHashMap<>();

    private final ScheduledExecutorService graceScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "mcp-progress-grace");
                t.setDaemon(true);
                return t;
            });

    /**
     * 注册一个新的工具调用，返回 Flux 供订阅
     */
    public Flux<McpSchema.ProgressNotification> register(String progressToken) {
        Sinks.Many<McpSchema.ProgressNotification> sink =
                Sinks.many().unicast().onBackpressureBuffer();
        sinks.put(progressToken, sink);
        // 清理同名 token 的历史状态（token 通常为每次调用唯一，但测试会复用固定 token）
        activity.remove(progressToken);
        terminalSeen.remove(progressToken);
        cancelPending(progressToken);
        return sink.asFlux()
                .doFinally(signal -> sinks.remove(progressToken));
    }

    /**
     * 由 progressConsumer 回调调用，分发到对应 sink
     */
    public void route(McpSchema.ProgressNotification notification) {
        Object token = notification.progressToken();
        if (token == null) {
            return;
        }
        String key = token.toString();
        Sinks.Many<McpSchema.ProgressNotification> sink = sinks.get(key);
        if (sink != null) {
            sink.tryEmitNext(notification);
        }
        activity.add(key);
        if (isTerminal(notification)) {
            terminalSeen.add(key);
            // 终帧已在上面 emit（先于任何 complete）；若此刻正等 grace 兜底，立即收口不再等待
            if (pendingGrace.remove(key) != null) {
                complete(key);
            }
        }
    }

    /**
     * 工具调用完成时关闭对应 sink（立即）。用于错误路径 / 不需要等终帧的场景。
     */
    public void complete(String progressToken) {
        cancelPending(progressToken);
        activity.remove(progressToken);
        terminalSeen.remove(progressToken);
        Sinks.Many<McpSchema.ProgressNotification> sink = sinks.remove(progressToken);
        if (sink != null) {
            sink.tryEmitComplete();
        }
    }

    /**
     * 收口 sink，但保证进度「终帧」不被抢跑的 complete 丢弃（issue #197）：
     * <ul>
     *   <li>已见终帧（progress&gt;=total）或该 token 从无进度活动 → 立即 {@link #complete}；</li>
     *   <li>有进度活动但尚未见终帧 → 给晚到的终帧一个有界 {@code grace} 窗口，
     *       窗口内终帧一到即由 {@link #route} 立即收口；到点仍无终帧则兜底 complete（绝不挂死）。</li>
     * </ul>
     *
     * @param progressToken 进度 token
     * @param grace         终帧兜底等待窗口；{@code null}/&le;0 视为不等待（等同 {@link #complete}）
     */
    public void completeAfterTerminalOrGrace(String progressToken, Duration grace) {
        if (progressToken == null) {
            return;
        }
        if (terminalSeen.contains(progressToken)
                || !activity.contains(progressToken)
                || grace == null || grace.isZero() || grace.isNegative()) {
            complete(progressToken);
            return;
        }
        ScheduledFuture<?> future = graceScheduler.schedule(
                () -> { pendingGrace.remove(progressToken); complete(progressToken); },
                grace.toMillis(), TimeUnit.MILLISECONDS);
        ScheduledFuture<?> prev = pendingGrace.put(progressToken, future);
        if (prev != null) {
            prev.cancel(false);
        }
        // 竞态兜底：schedule 与 route(终帧) 交叉时，若终帧已到，立即收口
        if (terminalSeen.contains(progressToken) && pendingGrace.remove(progressToken) != null) {
            future.cancel(false);
            complete(progressToken);
        }
    }

    /** 释放 grace 调度线程（由 {@link McpToolClient#close()} 调用）。 */
    public void shutdown() {
        graceScheduler.shutdownNow();
    }

    private void cancelPending(String token) {
        ScheduledFuture<?> f = pendingGrace.remove(token);
        if (f != null) {
            f.cancel(false);
        }
    }

    private static boolean isTerminal(McpSchema.ProgressNotification pn) {
        return pn != null
                && pn.progress() != null
                && pn.total() != null
                && pn.progress() >= pn.total();
    }
}
