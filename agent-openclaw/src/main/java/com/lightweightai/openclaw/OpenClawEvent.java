package com.lightweightai.openclaw;

import java.util.Map;

/**
 * OpenClaw runtime 的内部事件（ADR-014）。
 *
 * <p><b>边界</b>：此类型<b>仅 {@code agent-openclaw} 模块内部可见</b>，由 {@link OpenClawChatHandler}
 * 映射为既有 {@code StreamEvent}（{@code TEXT_DELTA}/{@code LLM_COMPLETE}/…）。<b>绝不外泄为新的
 * {@code StreamEvent.EventType}</b>，以守 ADR-005 的闭合枚举协议。
 *
 * <p>用 sealed + record 表达带标签的不可变联合体。
 */
public sealed interface OpenClawEvent
        permits OpenClawEvent.RunStarted, OpenClawEvent.Token, OpenClawEvent.ToolUse,
                OpenClawEvent.ToolResult, OpenClawEvent.RunEnd, OpenClawEvent.ErrorEvent {

    /** OpenClaw 起了一轮 run；携带 runId，供 barge-in 取消用。应为流首个事件。 */
    record RunStarted(String runId) implements OpenClawEvent {}

    /** 文本增量（逐字）。映射为 {@code TEXT_DELTA}。 */
    record Token(String text) implements OpenClawEvent {}

    /** OpenClaw 内部发起一次工具调用（OpenClaw 自跑工具）。默认隐藏，仅可选 trace。 */
    record ToolUse(String name, Map<String, Object> input) implements OpenClawEvent {}

    /** OpenClaw 内部工具返回结果。默认隐藏，仅可选 trace。 */
    record ToolResult(String name, String result) implements OpenClawEvent {}

    /** 本轮 run 结束。映射为 {@code LLM_COMPLETE}（触发末块 flush + stream_end）。 */
    record RunEnd(String stopReason) implements OpenClawEvent {}

    /** OpenClaw 侧错误。映射为 {@code Flux.error}。 */
    record ErrorEvent(Throwable cause) implements OpenClawEvent {}
}
