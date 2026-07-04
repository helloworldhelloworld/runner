package com.lightweightai.openclaw;

import com.lightweightai.kernel.core.StreamEvent;
import reactor.core.Disposable;
import reactor.core.publisher.FluxSink;

/**
 * 一轮 OpenClaw run 的运行态 + 取消句柄（ADR-014）。{@code InterruptibleRun} 的 OpenClaw 等价物：
 * 持上游订阅 {@link Disposable}（供停转发）与外层 {@link FluxSink}（供 barge-in 自终止语音流）。
 */
final class OpenClawRun {

    enum Phase { RUNNING, INTERRUPTED }

    private final String sessionId;
    private volatile String openClawRunId;
    private volatile Disposable subscription;
    private volatile FluxSink<StreamEvent> sink;
    private volatile Phase phase = Phase.RUNNING;

    OpenClawRun(String sessionId) {
        this.sessionId = sessionId;
    }

    Phase phase() { return phase; }
    boolean isRunning() { return phase == Phase.RUNNING; }

    void setOpenClawRunId(String id) { this.openClawRunId = id; }

    /** 记外层 sink（订阅时最早设置,供 interrupt 自终止外层流）。 */
    void setSink(FluxSink<StreamEvent> s) { this.sink = s; }

    /**
     * 记上游订阅。若在设置前已被 interrupt（跨线程 barge-in 抢跑）,立即补 dispose,避免孤儿订阅泄漏（review #183-3）。
     */
    void setSubscription(Disposable d) {
        this.subscription = d;
        if (phase == Phase.INTERRUPTED && d != null && !d.isDisposed()) {
            d.dispose();
        }
    }

    /**
     * barge-in：标记 INTERRUPTED → ACP 取消上游 + dispose 订阅 + **自终止外层语音流**,返回
     * {@code SPEECH_INTERRUPTED}（形状与 {@code Orchestrator.interrupt} 一致,供传输层下发停播）。
     *
     * <p>自终止外层流很关键：Reactor 取消上游不会回调 onComplete/onError,若不主动 complete 外层 sink,
     * 语音流会挂尾（仅靠下游 WS handler dispose 兜底）。见 review #183-1。
     */
    StreamEvent interrupt(OpenClawClient client) {
        phase = Phase.INTERRUPTED;
        cancelUpstream(client);
        FluxSink<StreamEvent> s = sink;
        if (s != null) {
            s.complete();
        }
        return StreamEvent.speechInterrupted(openClawRunId, "barge-in");
    }

    /** 取消上游 OpenClaw run + dispose 订阅（幂等）。下游取消（WS 断）时也走此路,省 token。 */
    void cancelUpstream(OpenClawClient client) {
        client.cancel(sessionId);   // OpenClaw 按 sessionKey 打断（chat.abort）
        Disposable d = subscription;
        if (d != null && !d.isDisposed()) {
            d.dispose();
        }
    }
}
