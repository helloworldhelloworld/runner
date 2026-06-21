package com.lightweightai.openclaw.ws;

import com.lightweightai.openclaw.OpenClawChatRequest;
import com.lightweightai.openclaw.OpenClawClient;
import com.lightweightai.openclaw.OpenClawEvent;
import reactor.core.publisher.Flux;

/**
 * 真实 OpenClaw 客户端：WS 连 OpenClaw Gateway，讲 ACP/Gateway 协议（ADR-014 阶段 3）。
 *
 * <p><b>骨架占位</b>：真协议实现被三个 OpenClaw 侧开放问题阻塞（ACP「无新输入纯取消在途 run」、
 * 事件流 / 线格式、MCP 挂载，见 ADR-014）。本类<b>构造时不连接</b>（只存 url），
 * {@link #chat}/{@link #cancel} 在真实现落地前抛 {@link UnsupportedOperationException}，
 * 以便 {@code BrainConfig} 在 {@code app.brain.type=openclaw} 时能装配 + 容器测试选脑，而不误连。
 *
 * <p>阶段 3 实现要点（见 ADR-014 / docs/modules/agent-openclaw.md）：复用 agent-mcp WS transport 经验 +
 * ADR-011 连接韧性（后台重连、send 前就绪信号、不静默吞超时）；用 faithful fake 对端做往返 + 失败时序接缝测试。
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
