package com.lightweightai.openclaw.ws;

import com.lightweightai.openclaw.OpenClawChatRequest;
import com.lightweightai.openclaw.OpenClawClient;
import com.lightweightai.openclaw.OpenClawEvent;
import reactor.core.publisher.Flux;

/**
 * 真实 OpenClaw 客户端：WS 连 OpenClaw Gateway，讲 ACP/Gateway 协议（ADR-014 阶段 3）。
 *
 * <p><b>骨架占位</b>：协议已查证（docs.openclaw.ai），仅剩一个残留挡住落地。本类<b>构造时不连接</b>（只存 url），
 * {@link #chat}/{@link #cancel} 在真实现前抛 {@link UnsupportedOperationException}，以便 {@code BrainConfig}
 * 在 {@code app.brain.type=openclaw} 时能装配 + 容器测试选脑，而不误连。
 *
 * <p><b>已查证的协议</b>（JSON-RPC over WS，帧 {@code req}/{@code res}/{@code event}，{@code wss://host:18789}）：
 * <ul>
 *   <li>连接：{@code connect}（protocol v3/4 + {@code auth.token} + scopes {@code operator.read/write} + 设备签名）。</li>
 *   <li>起 run：{@code chat.send {sessionKey, text, agentId}}。</li>
 *   <li>token 流：{@code event} 帧 {@code event:"chat"}，payload {@code {deltaText（增量）, message（累计）}}（v4）→ 映射 TEXT_DELTA。</li>
 *   <li>打断（barge-in）：{@code chat.abort {sessionKey}} / {@code sessions.abort {key, runId?}}，协作式、不拆 session。</li>
 * </ul>
 * <p><b>残留（阻塞落地）</b>：run 开始/结束/取消、tool_use/tool_result 的确切事件名不在顶层 {@code frames.ts}，
 * 在 OpenClaw 的 feature-specific（chat）schema 模块；需读该模块把 {@link OpenClawEvent} 映射坐实，才能可靠发 {@code RunEnd→LLM_COMPLETE}。
 *
 * <p>实现要点（ADR-014 / docs/modules/agent-openclaw.md）：复用 agent-mcp WS transport + ADR-011 连接韧性
 * （后台重连、send 前就绪信号、不静默吞超时）；用 faithful fake 对端做往返 + 失败时序接缝测试。
 */
public final class WebSocketOpenClawClient implements OpenClawClient {

    private final String url;

    public WebSocketOpenClawClient(String url) {
        this.url = url;   // 不在构造时连接
    }

    public String url() {
        return url;
    }

    @Override
    public Flux<OpenClawEvent> chat(OpenClawChatRequest request) {
        return Flux.error(new UnsupportedOperationException(
                "WebSocketOpenClawClient 真协议未实现（ADR-014 阶段 3，待 OpenClaw 侧开放问题解掉）"));
    }

    @Override
    public void cancel(String openClawRunId) {
        throw new UnsupportedOperationException(
                "WebSocketOpenClawClient 真协议未实现（ADR-014 阶段 3）");
    }
}
