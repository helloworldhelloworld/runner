package com.lightweightai.openclaw;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Faithful fake of {@link OpenClawClient}（捕获式，仿 agent-kernel 的 CapturingLLMProvider）。
 *
 * <p>{@link #chat} 返回一个手控 Sink 的 Flux，测试在订阅后 {@link #emit}/{@link #complete}/{@link #error}
 * 编排事件；{@link #cancel} 把 runId 记入 {@link #cancelledRunIds} 供断言。<b>不 mock 自己层</b>，
 * 也不连真 OpenClaw —— 守 CLAUDE.md 集成接缝规约的"假对端"。
 */
final class FakeOpenClawClient implements OpenClawClient {

    private final Sinks.Many<OpenClawEvent> sink = Sinks.many().multicast().onBackpressureBuffer();

    final List<String> cancelled = new CopyOnWriteArrayList<>();   // 记录被 cancel 的 sessionId
    volatile OpenClawChatRequest lastRequest;

    @Override
    public Flux<OpenClawEvent> chat(OpenClawChatRequest request) {
        this.lastRequest = request;
        return sink.asFlux();
    }

    @Override
    public void cancel(String sessionId) {
        cancelled.add(sessionId);
    }

    // ── 测试驱动 ──
    void emit(OpenClawEvent ev) { sink.tryEmitNext(ev); }
    void complete() { sink.tryEmitComplete(); }
    void error(Throwable t) { sink.tryEmitError(t); }
}
