package com.lightweightai.openclaw;

import reactor.core.publisher.Flux;

/**
 * OpenClaw runtime 的客户端 SPI（ADR-014）。讲 OpenClaw Gateway/ACP 协议。
 *
 * <p>藏在接口后，便于用 faithful fake 端到端测 {@link OpenClawChatHandler}，无需连真 OpenClaw
 * （守 CLAUDE.md 集成接缝规约：用假对端，不 mock 自己层）。真实现见
 * {@code ws.WebSocketOpenClawClient}（阶段 3）。
 */
public interface OpenClawClient {

    /**
     * 起一轮 OpenClaw run，流式回 {@link OpenClawEvent}。
     *
     * <p>约定：流的首个事件应为 {@link OpenClawEvent.RunStarted}（携带 runId），随后零个或多个
     * {@link OpenClawEvent.Token}（穿插 {@code ToolUse/ToolResult}），以 {@link OpenClawEvent.RunEnd}
     * 收尾后 {@code onComplete}；出错走 {@code onError} 或 {@link OpenClawEvent.ErrorEvent}。
     */
    Flux<OpenClawEvent> chat(OpenClawChatRequest request);

    /**
     * 取消在途 run（ACP cancel）。幂等；未知 / 已结束的 runId 应静默无副作用。
     *
     * @param openClawRunId {@link OpenClawEvent.RunStarted} 携带的 runId
     */
    void cancel(String openClawRunId);
}
