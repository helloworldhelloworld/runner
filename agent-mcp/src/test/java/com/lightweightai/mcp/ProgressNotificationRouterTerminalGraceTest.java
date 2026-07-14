package com.lightweightai.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * issue #197：进度「终帧」(progress 达到 total) 晚于 tool result 到达时不能被丢弃。
 *
 * <p>原有 {@link ProgressNotificationRouter#complete(String)} 在 result 一返回就关闭 sink，
 * 若终帧此刻仍在途中，其 {@code route()} 落在已关闭的 sink 上被静默丢弃 → 「反馈通知类型的最后一帧
 * 没收到 → 失败上下文没更新」。
 *
 * <p>本用例锁定 {@link ProgressNotificationRouter#completeAfterTerminalOrGrace(String, Duration)}
 * 的契约：等终帧（progress>=total）或有界 grace 兜底，二者取先，既不丢终帧也不挂死。
 */
@DisplayName("ProgressNotificationRouter - 终帧 grace 收口（issue #197）")
class ProgressNotificationRouterTerminalGraceTest {

    private ProgressNotificationRouter router;
    private static final String TOKEN = "tok-197";

    @BeforeEach
    void setUp() {
        router = new ProgressNotificationRouter();
    }

    private static McpSchema.ProgressNotification pn(double progress, double total, String msg) {
        return new McpSchema.ProgressNotification(TOKEN, progress, total, msg);
    }

    private static void awaitTrue(BooleanSupplier cond, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (cond.getAsBoolean()) return;
            try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
    }

    /** #197 核心：终帧在收口请求「之后」到达（但在 grace 窗口内）→ 不丢、随后正常完成。 */
    @Test
    @DisplayName("终帧晚于 result 到达（grace 窗口内）→ 终帧不丢")
    void lateTerminalWithinGrace_notDropped() {
        CopyOnWriteArrayList<McpSchema.ProgressNotification> got = new CopyOnWriteArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        router.register(TOKEN).subscribe(got::add, e -> {}, () -> completed.set(true));

        router.route(pn(0.5, 1.0, "half"));               // 中间帧先到
        // 模拟：tool result 已返回 → 请求收口（带 grace），但终帧还没到
        router.completeAfterTerminalOrGrace(TOKEN, Duration.ofMillis(1000));
        // 终帧稍后（grace 内）才到
        try { Thread.sleep(60); } catch (InterruptedException ignored) {}
        router.route(pn(1.0, 1.0, "done"));

        awaitTrue(completed::get, 2000);
        assertTrue(completed.get(), "grace 后应正常完成，不挂死");
        boolean hasTerminal = got.stream().anyMatch(
            p -> p.progress() != null && p.total() != null && p.progress() >= p.total());
        assertTrue(hasTerminal, "晚到的终帧(progress>=total)不应被丢弃，实际收到: " + got.size() + " 帧");
    }

    /** 终帧在收口请求「之前」已到 → 立即完成（不等 grace）。 */
    @Test
    @DisplayName("终帧先于 result 到达 → 立即完成，不等 grace")
    void terminalBeforeComplete_completesImmediately() {
        CopyOnWriteArrayList<McpSchema.ProgressNotification> got = new CopyOnWriteArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        router.register(TOKEN).subscribe(got::add, e -> {}, () -> completed.set(true));

        router.route(pn(1.0, 1.0, "done"));               // 终帧已到
        router.completeAfterTerminalOrGrace(TOKEN, Duration.ofSeconds(10)); // 即便 grace 很大

        awaitTrue(completed::get, 500);                   // 应远早于 10s
        assertTrue(completed.get(), "已见终帧应立即完成，不受 grace 影响");
    }

    /** 无任何进度活动（工具不发 progress）→ 立即完成，不引入 grace 延迟。 */
    @Test
    @DisplayName("无 progress 活动 → 立即完成，无 grace 延迟")
    void noProgressActivity_completesImmediately() {
        AtomicBoolean completed = new AtomicBoolean(false);
        router.register(TOKEN).subscribe(x -> {}, e -> {}, () -> completed.set(true));

        router.completeAfterTerminalOrGrace(TOKEN, Duration.ofSeconds(10));

        awaitTrue(completed::get, 400);                   // 必须远早于 grace，证明没走 grace
        assertTrue(completed.get(), "无 progress 的工具不应被 grace 拖延");
    }

    /** 有 progress 但始终未到终帧（不确定总量）→ grace 兜底完成，绝不挂死。 */
    @Test
    @DisplayName("有 progress 但无终帧 → grace 兜底完成，不挂死")
    void progressButNoTerminal_completesViaGraceFallback() {
        CopyOnWriteArrayList<McpSchema.ProgressNotification> got = new CopyOnWriteArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        router.register(TOKEN).subscribe(got::add, e -> {}, () -> completed.set(true));

        router.route(pn(0.3, 1.0, "a"));
        router.route(pn(0.6, 1.0, "b"));                  // 从未到达 total
        router.completeAfterTerminalOrGrace(TOKEN, Duration.ofMillis(300));

        awaitTrue(completed::get, 2000);
        assertTrue(completed.get(), "无终帧也必须由 grace 兜底完成，不能挂死");
        assertFalse(got.isEmpty(), "已到的中间帧应保留");
    }
}
