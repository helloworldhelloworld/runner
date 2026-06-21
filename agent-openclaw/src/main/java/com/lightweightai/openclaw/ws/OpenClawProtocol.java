package com.lightweightai.openclaw.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lightweightai.openclaw.OpenClawChatRequest;
import com.lightweightai.openclaw.OpenClawEvent;

import java.util.Optional;

/**
 * OpenClaw Gateway 线协议编解码（ADR-014 阶段3）—— 纯函数、无 socket、可单测。
 *
 * <p>依据 docs.openclaw.ai + {@code gateway-protocol/src/schema}（已查证 2026-06-21）：
 * JSON-RPC over WS，帧 {@code req}/{@code res}/{@code event}。
 * <ul>
 *   <li>起 run：{@code chat.send {sessionKey, text, agentId}}。</li>
 *   <li>token / run 生命周期：{@code event:"chat"} payload 带 {@code state}：
 *       {@code delta}{@code {deltaText}}、{@code final}{@code {stopReason?}}、
 *       {@code error}{@code {errorMessage?, errorKind}}、{@code aborted}。</li>
 *   <li>打断：{@code chat.abort {sessionKey}}（runId 可选）。</li>
 * </ul>
 *
 * <p><b>简化点（TODO 真网关）</b>：{@code connect} 省略 challenge-response 与设备签名（真 gateway 先发
 * {@code connect.challenge}、客户端用设备私钥签 nonce）。当前版对忠实假对端打通；真网关 auth 待补（ADR-014）。
 */
final class OpenClawProtocol {

    private static final ObjectMapper M = new ObjectMapper();

    private OpenClawProtocol() {
    }

    /** {@code connect} 请求帧（简化：协议协商 + token，省设备签名）。 */
    static String connect(String id, String token, int minProtocol, int maxProtocol) {
        ObjectNode root = req(id, "connect");
        ObjectNode p = root.putObject("params");
        p.put("minProtocol", minProtocol).put("maxProtocol", maxProtocol);
        p.put("role", "operator");
        p.putArray("scopes").add("operator.read").add("operator.write");
        if (token != null) {
            p.putObject("auth").put("token", token);
        }
        return write(root);
    }

    /** {@code chat.send}：起一轮 run。 */
    static String chatSend(String id, OpenClawChatRequest r) {
        ObjectNode root = req(id, "chat.send");
        ObjectNode p = root.putObject("params");
        p.put("sessionKey", r.sessionId());
        if (r.agentId() != null && !r.agentId().isBlank()) {
            p.put("agentId", r.agentId());
        }
        p.put("message", r.message());
        return write(root);
    }

    /** {@code chat.abort}：按 sessionKey 打断在途 run（barge-in）。 */
    static String chatAbort(String id, String sessionKey) {
        ObjectNode root = req(id, "chat.abort");
        root.putObject("params").put("sessionKey", sessionKey);
        return write(root);
    }

    /**
     * 解析服务端帧 → {@link OpenClawEvent}。只关心 {@code event:"chat"} 的 {@code state} 机；
     * 其它帧（{@code res}/{@code tick}/非 chat event/畸形）返回 {@link Optional#empty()}。
     */
    static Optional<OpenClawEvent> parseEvent(String json) {
        JsonNode n;
        try {
            n = M.readTree(json);
        } catch (Exception e) {
            return Optional.empty();
        }
        if (!"event".equals(n.path("type").asText()) || !"chat".equals(n.path("event").asText())) {
            return Optional.empty();
        }
        JsonNode pl = n.path("payload");
        return switch (pl.path("state").asText("")) {
            case "delta" -> Optional.of(new OpenClawEvent.Token(pl.path("deltaText").asText("")));
            case "final" -> Optional.of(new OpenClawEvent.RunEnd(textOrNull(pl, "stopReason")));
            case "error" -> Optional.of(new OpenClawEvent.ErrorEvent(
                    new RuntimeException("openclaw " + pl.path("errorKind").asText("error")
                            + ": " + pl.path("errorMessage").asText(""))));
            case "aborted" -> Optional.of(new OpenClawEvent.RunEnd("aborted"));
            default -> Optional.empty();
        };
    }

    private static ObjectNode req(String id, String method) {
        ObjectNode root = M.createObjectNode();
        root.put("type", "req").put("id", id).put("method", method);
        return root;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isTextual() ? v.asText() : null;
    }

    private static String write(ObjectNode node) {
        try {
            return M.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("OpenClaw 帧序列化失败", e);
        }
    }
}
