package com.lightweightai.openclaw;

import com.lightweightai.kernel.core.StreamEvent;
import reactor.core.Disposable;

/**
 * 一轮 OpenClaw run 的运行态 + 取消句柄（ADR-014）。{@code InterruptibleRun} 的 OpenClaw 等价物：
 * 持 {@code openClawRunId}（供 ACP 取消）与上游订阅 {@link Disposable}（供停转发）。
 */
final class OpenClawRun {

    enum Phase { RUNNING, INTERRUPTED, DONE }

    private final String sessionId;
    private volatile String openClawRunId;
    private volatile Disposable subscription;
    private volatile Phase phase = Phase.RUNNING;

    OpenClawRun(String sessionId) {
        this.sessionId = sessionId;
    }

    String sessionId() { return sessionId; }
    String openClawRunId() { return openClawRunId; }
    Phase phase() { return phase; }
    boolean isRunning() { return phase == Phase.RUNNING; }

    void setOpenClawRunId(String id) { this.openClawRunId = id; }
    void setSubscription(Disposable d) { this.subscription = d; }
    void markDone() { if (phase == Phase.RUNNING) phase = Phase.DONE; }

    /**
     * barge-in：标记 INTERRUPTED → ACP 取消 OpenClaw run + dispose 上游订阅，返回
     * {@code SPEECH_INTERRUPTED}（形状与 {@code Orchestrator.interrupt} 一致，供传输层下发停播）。
     */
    StreamEvent interrupt(OpenClawClient client) {
        phase = Phase.INTERRUPTED;
        cancelUpstream(client);
        return StreamEvent.speechInterrupted(openClawRunId, "barge-in");
    }

    /** 取消上游 OpenClaw run + dispose 订阅（幂等）。下游取消（WS 断）时也走此路，省 token。 */
    void cancelUpstream(OpenClawClient client) {
        client.cancel(sessionId);   // OpenClaw 按 sessionKey 打断（chat.abort），runId 可选
        Disposable d = subscription;
        if (d != null && !d.isDisposed()) {
            d.dispose();
        }
    }
}
