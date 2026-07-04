package com.lightweightai.openclaw;

/**
 * 发给 OpenClaw 的一轮请求（ADR-014）。从 {@code GatewayRequest} 映射而来。
 *
 * @param sessionId 会话标识（runner ⟷ OpenClaw session 映射键）
 * @param agentId   OpenClaw agent 选择键（如 "minion"；可空，由 client 兜默认）
 * @param message   用户文本（STT 转写）
 */
public record OpenClawChatRequest(String sessionId, String agentId, String message) {
}
