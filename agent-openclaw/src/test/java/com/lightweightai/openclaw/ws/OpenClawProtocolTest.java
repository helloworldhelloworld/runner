package com.lightweightai.openclaw.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.openclaw.OpenClawChatRequest;
import com.lightweightai.openclaw.OpenClawEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** OpenClaw 线协议编解码单测（ADR-014 阶段3）：出站帧结构 + 入站 chat.state 机映射。 */
@DisplayName("OpenClawProtocol - 线协议编解码")
class OpenClawProtocolTest {

    private static final ObjectMapper M = new ObjectMapper();

    private static JsonNode parse(String json) {
        try {
            return M.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── 出站帧 ──

    @Test
    @DisplayName("chat.send：method + sessionKey/agentId/message")
    void chatSendFrame() {
        JsonNode n = parse(OpenClawProtocol.chatSend("1",
                new OpenClawChatRequest("s1", "minion", "你好")));
        assertEquals("req", n.get("type").asText());
        assertEquals("1", n.get("id").asText());
        assertEquals("chat.send", n.get("method").asText());
        JsonNode p = n.get("params");
        assertEquals("s1", p.get("sessionKey").asText());
        assertEquals("minion", p.get("agentId").asText());
        assertEquals("你好", p.get("message").asText());
    }

    @Test
    @DisplayName("chat.send：agentId 空则省略")
    void chatSendOmitsBlankAgent() {
        JsonNode p = parse(OpenClawProtocol.chatSend("1",
                new OpenClawChatRequest("s1", "", "hi"))).get("params");
        assertFalse(p.has("agentId"), "空 agentId 不应出现在帧里");
    }

    @Test
    @DisplayName("chat.abort：按 sessionKey")
    void chatAbortFrame() {
        JsonNode n = parse(OpenClawProtocol.chatAbort("9", "s1"));
        assertEquals("chat.abort", n.get("method").asText());
        assertEquals("s1", n.get("params").get("sessionKey").asText());
    }

    @Test
    @DisplayName("connect：协议协商 + operator scopes + token")
    void connectFrame() {
        JsonNode p = parse(OpenClawProtocol.connect("0", "tok", 3, 4)).get("params");
        assertEquals(3, p.get("minProtocol").asInt());
        assertEquals(4, p.get("maxProtocol").asInt());
        assertEquals("operator", p.get("role").asText());
        assertEquals("tok", p.get("auth").get("token").asText());
    }

    // ── 入站 chat.state 机 ──

    private static String chatEvent(String statePayloadJson) {
        return "{\"type\":\"event\",\"event\":\"chat\",\"payload\":" + statePayloadJson + "}";
    }

    @Test
    @DisplayName("state=delta → Token(deltaText)")
    void deltaToToken() {
        Optional<OpenClawEvent> ev = OpenClawProtocol.parseEvent(
                chatEvent("{\"state\":\"delta\",\"deltaText\":\"你好，\"}"));
        assertTrue(ev.orElseThrow() instanceof OpenClawEvent.Token t && t.text().equals("你好，"));
    }

    @Test
    @DisplayName("state=final → RunEnd(stopReason)")
    void finalToRunEnd() {
        Optional<OpenClawEvent> ev = OpenClawProtocol.parseEvent(
                chatEvent("{\"state\":\"final\",\"stopReason\":\"end_turn\"}"));
        assertTrue(ev.orElseThrow() instanceof OpenClawEvent.RunEnd r && "end_turn".equals(r.stopReason()));
    }

    @Test
    @DisplayName("state=error → ErrorEvent(含 errorKind)")
    void errorToErrorEvent() {
        OpenClawEvent ev = OpenClawProtocol.parseEvent(
                chatEvent("{\"state\":\"error\",\"errorKind\":\"timeout\",\"errorMessage\":\"slow\"}")).orElseThrow();
        assertTrue(ev instanceof OpenClawEvent.ErrorEvent e
                && e.cause().getMessage().contains("timeout") && e.cause().getMessage().contains("slow"));
    }

    @Test
    @DisplayName("state=aborted → RunEnd(aborted)")
    void abortedToRunEnd() {
        OpenClawEvent ev = OpenClawProtocol.parseEvent(chatEvent("{\"state\":\"aborted\"}")).orElseThrow();
        assertTrue(ev instanceof OpenClawEvent.RunEnd r && "aborted".equals(r.stopReason()));
    }

    @Test
    @DisplayName("非 chat event / res / tick / 畸形 → empty")
    void nonChatOrMalformedIgnored() {
        assertTrue(OpenClawProtocol.parseEvent("{\"type\":\"event\",\"event\":\"tick\",\"payload\":{}}").isEmpty());
        assertTrue(OpenClawProtocol.parseEvent("{\"type\":\"res\",\"id\":\"1\",\"ok\":true}").isEmpty());
        assertTrue(OpenClawProtocol.parseEvent(chatEvent("{\"state\":\"unknown\"}")).isEmpty());
        assertTrue(OpenClawProtocol.parseEvent("not json").isEmpty());
    }
}
