package com.lightweightai.openclaw;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.gateway.ChatHandler;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.gateway.GatewayResponse;
import com.lightweightai.kernel.llm.LLMResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 把外部 OpenClaw runtime 作为"脑"接入大脑平面的 {@link ChatHandler} 实现（ADR-014，完整大脑模式）。
 *
 * <p>OpenClaw 自跑 agent loop + 工具 + compaction；本类只负责：把 OpenClaw 的 {@link OpenClawEvent}
 * 流映射成 runner 的 {@code Flux<StreamEvent>}（{@code TEXT_DELTA}/{@code LLM_COMPLETE}），并实现
 * barge-in 的 {@link #interrupt(String)} 契约。下游语音平面（可说块/emotion/序列化/WS）对本实现泛型无关、零改动。
 *
 * <p><b>不</b>新增 {@code StreamEvent.EventType}（守 ADR-005）：OpenClaw 工具事件默认隐藏，仅
 * {@code forwardToolTrace} 时转既有 {@code TRACE} 供观测。
 */
public final class OpenClawChatHandler implements ChatHandler {

    private static final Logger log = LoggerFactory.getLogger(OpenClawChatHandler.class);
    private static final String DEFAULT_AGENT = "minion";

    private final OpenClawClient client;
    private final boolean forwardToolTrace;
    private final Map<String, OpenClawRun> activeRuns = new ConcurrentHashMap<>();

    public OpenClawChatHandler(OpenClawClient client) {
        this(client, false);
    }

    public OpenClawChatHandler(OpenClawClient client, boolean forwardToolTrace) {
        this.client = client;
        this.forwardToolTrace = forwardToolTrace;
    }

    @Override
    public Flux<StreamEvent> chatStreamReactive(GatewayRequest request) {
        String sessionId = request.getSessionId();
        OpenClawRun run = new OpenClawRun(sessionId);
        // 同 session 已有在途 run → 隐式打断（对齐 Orchestrator "新消息打断在途"行为）
        OpenClawRun prev = activeRuns.put(sessionId, run);
        if (prev != null && prev.isRunning()) {
            prev.interrupt(client);
        }
        OpenClawChatRequest req = toRequest(request);

        return Flux.create(sink -> {
            // 实订阅上游、捕获 Disposable 存入 run（仿 InterruptibleRun.activeSubscription），供 interrupt 停转发
            Disposable d = client.chat(req).subscribe(
                    ev -> mapInto(ev, run, sink),
                    err -> { activeRuns.remove(sessionId, run); sink.error(err); },
                    () -> { run.markDone(); activeRuns.remove(sessionId, run); sink.complete(); });
            run.setSubscription(d);
            // 下游取消（WS 断 / barge-in dispose）→ 真停 OpenClaw run，省 token
            sink.onCancel(() -> run.cancelUpstream(client));
            sink.onDispose(() -> activeRuns.remove(sessionId, run));
        });
    }

    @Override
    public StreamEvent interrupt(String sessionId) {
        OpenClawRun run = activeRuns.get(sessionId);
        if (run == null || !run.isRunning()) {
            return null;
        }
        activeRuns.remove(sessionId, run);
        return run.interrupt(client);
    }

    /** {@link OpenClawEvent} → {@code StreamEvent}，注入 sink。 */
    private void mapInto(OpenClawEvent ev, OpenClawRun run, FluxSink<StreamEvent> sink) {
        switch (ev) {
            case OpenClawEvent.RunStarted s -> {
                run.setOpenClawRunId(s.runId());
                if (forwardToolTrace) {
                    sink.next(StreamEvent.trace("openclaw.run", "started runId=" + s.runId()));
                }
            }
            case OpenClawEvent.Token t -> sink.next(StreamEvent.textDelta(t.text()));
            // RunEnd → LLM_COMPLETE：触发下游末块 flush + stream_end；流的真正完成由上游 onComplete 收尾
            case OpenClawEvent.RunEnd e -> sink.next(StreamEvent.llmComplete(LLMResponse.builder().build()));
            case OpenClawEvent.ToolUse u -> {
                if (forwardToolTrace) sink.next(StreamEvent.trace("openclaw.tool", "use " + u.name()));
            }
            case OpenClawEvent.ToolResult r -> {
                if (forwardToolTrace) sink.next(StreamEvent.trace("openclaw.tool", "result " + r.name()));
            }
            case OpenClawEvent.ErrorEvent err -> sink.error(err.cause());
        }
    }

    private OpenClawChatRequest toRequest(GatewayRequest request) {
        Object agentId = request.getMetadata().get("agentId");
        String agent = (agentId instanceof String s && !s.isBlank()) ? s : DEFAULT_AGENT;
        return new OpenClawChatRequest(request.getSessionId(), agent, request.getMessage());
    }

    // ── 非流式路径：OpenClaw 是流式 runtime，语音平面只走 chatStreamReactive；同步/回调路径不支持 ──

    @Override
    public GatewayResponse chat(GatewayRequest request) {
        throw new UnsupportedOperationException("OpenClawChatHandler 仅支持 reactive 流式（chatStreamReactive）");
    }

    @Override
    public CompletableFuture<GatewayResponse> chatStream(GatewayRequest request, StreamCallback callback) {
        throw new UnsupportedOperationException("OpenClawChatHandler 仅支持 reactive 流式（chatStreamReactive）");
    }
}
