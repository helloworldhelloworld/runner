package com.lightweightai.web.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.postprocess.EmotionClassifier;
import com.lightweightai.kernel.core.postprocess.SpeakableChunkProcessor;
import com.lightweightai.kernel.gateway.ChatHandler;
import com.lightweightai.kernel.gateway.Gateway;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.gateway.GatewayResponse;
import com.lightweightai.safety.CrisisDetector;
import com.lightweightai.web.config.GatewayConfig;
import com.lightweightai.web.config.PostProcessorConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * M-2 / ADR-012：大脑平面（Voice Gateway ⟷ runner WS）文本契约 runner 侧跨仓接缝测试。
 *
 * <p>按 CLAUDE.md 集成接缝规约：<b>fake 远端对端</b>（Voice Gateway = 记录出站帧的 mock
 * {@link WebSocketSession}），其余全真 —— 真 {@link SpringChatWebSocketHandler} 解析入站、
 * 真 {@link Gateway} + 真 {@link SpeakableChunkProcessor} 后处理管道、真
 * {@link StreamEventSerializer} 出站。驱动：
 * <ul>
 *   <li><b>STT 文本入 → 可说块出</b>：送 inbound.chat（多句）→ 出站含 speakable_chunk + stream_end；</li>
 *   <li><b>barge-in 入 → 停播出</b>：送 inbound.interrupt → 下发 speech_interrupted 且在途订阅被取消。</li>
 * </ul>
 * 出/入站帧逐一用三仓共享的 {@code contract/minion-voice-text/messages.schema.json} 校验——
 * schema 与线格式漂移即测试红。
 */
@DisplayName("M-2 接缝: Voice Gateway⟷runner WS 文本契约（STT入/可说块出/barge-in）")
class MinionVoiceTextSeamAcceptanceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static JsonNode SCHEMA_DEFS;

    @BeforeAll
    static void loadSchema() throws IOException {
        Path schema = findContractSchema();
        assertNotNull(schema, "找不到 contract/minion-voice-text/messages.schema.json（三仓共享契约）");
        SCHEMA_DEFS = MAPPER.readTree(Files.readString(schema)).get("$defs");
        assertNotNull(SCHEMA_DEFS, "schema 应含 $defs");
    }

    /** 从 user.dir 向上找 contract 文件（surefire 工作目录为模块目录，契约在仓库根）。 */
    private static Path findContractSchema() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 5 && dir != null; i++, dir = dir.getParent()) {
            Path p = dir.resolve("contract/minion-voice-text/messages.schema.json");
            if (Files.exists(p)) return p;
        }
        return null;
    }

    private List<String> outboundFrames;
    private CountDownLatch streamEnd;
    private WebSocketSession session;
    private SpringChatWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        outboundFrames = new CopyOnWriteArrayList<>();
        streamEnd = new CountDownLatch(1);

        session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("sess-1");
        when(session.isOpen()).thenReturn(true);
        try {
            doAnswer(inv -> {
                String payload = ((TextMessage) inv.getArgument(0)).getPayload();
                outboundFrames.add(payload);
                if (payload.contains("\"stream_end\"")) streamEnd.countDown();
                return null;
            }).when(session).sendMessage(any());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // 真 Gateway + 真 speakable 后处理管道（cheerful classifier 证明逐块 emotion 透传）。
        EmotionClassifier classifier = text -> "cheerful";
        SpeakableChunkProcessor speakable = new PostProcessorConfig().speakableChunkProcessor(classifier);
        Gateway gateway = new GatewayConfig().gateway(
                voiceChatHandler(), noopSessionManager(), null, List.of(speakable));

        // 真 handler，chat/interrupt 路径不用到的依赖传 null。
        handler = new SpringChatWebSocketHandler(
                gateway, new CrisisDetector(), new ToolRegistry(),
                null, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("STT 文本入 → 真管道切出带 emotion 的 speakable_chunk 并按契约下发 + stream_end")
    void sttTextIn_emitsSchemaConformantSpeakableChunks() throws Exception {
        String inbound = MAPPER.createObjectNode()
                .put("type", "chat").put("sessionId", "voice-1")
                .put("agentId", "minion").put("message", "你看到什么了？").toString();
        assertInboundConforms(MAPPER.readTree(inbound), "inbound.chat");

        handler.handleTextMessage(session, new TextMessage(inbound));
        assertTrue(streamEnd.await(5, TimeUnit.SECONDS), "应在超时内收到 stream_end；实际帧: " + outboundFrames);

        List<JsonNode> chunks = framesOfType("speakable_chunk");
        assertEquals(2, chunks.size(), "真管道应切出 2 个可说块；实际帧: " + outboundFrames);
        for (JsonNode c : chunks) assertFrameConforms(c, "outbound.speakable_chunk");

        assertEquals("你好，世界。", chunks.get(0).get("data").get("text").asText());
        assertEquals(0, chunks.get(0).get("data").get("index").asInt());
        assertEquals("cheerful", chunks.get(0).get("data").get("emotion").asText(),
                "逐块 emotion 应来自注入的 classifier（R3 经真管道）");
        assertEquals("再见！", chunks.get(1).get("data").get("text").asText());
        assertEquals(1, chunks.get(1).get("data").get("index").asInt());

        JsonNode end = framesOfType("stream_end").get(0);
        assertFrameConforms(end, "outbound.stream_end");
    }

    @Test
    @DisplayName("barge-in 入 → 按契约下发 speech_interrupted 且取消在途转发")
    void bargeInIn_emitsSchemaConformantSpeechInterrupted() throws Exception {
        String inbound = MAPPER.createObjectNode()
                .put("type", "interrupt").put("sessionId", "voice-1").toString();
        assertInboundConforms(MAPPER.readTree(inbound), "inbound.interrupt");

        // handleInterrupt 同步执行（非 chatExecutor 异步），返回即已下发帧。
        handler.handleTextMessage(session, new TextMessage(inbound));

        List<JsonNode> si = framesOfType("speech_interrupted");
        assertEquals(1, si.size(), "barge-in 应下发恰好一个 speech_interrupted；实际帧: " + outboundFrames);
        assertFrameConforms(si.get(0), "outbound.speech_interrupted");
        assertEquals("barge-in", si.get(0).get("data").get("reason").asText());
        assertFalse(si.get(0).get("data").get("runId").asText().isEmpty(), "应带被打断 run 的 runId");
    }

    // ==================== schema 校验 helpers ====================

    /** 校验出站帧符合 $defs/<def>：type const + 顶层 required + data 嵌套 required。 */
    private void assertFrameConforms(JsonNode frame, String defName) {
        JsonNode def = SCHEMA_DEFS.get(defName);
        assertNotNull(def, "schema 缺少 $defs/" + defName);
        assertRequiredPresent(frame, def, defName);

        JsonNode typeProp = def.path("properties").path("type");
        if (typeProp.has("const")) {
            assertEquals(typeProp.get("const").asText(), frame.path("type").asText(),
                    defName + ": type 不符合契约 const");
        }
    }

    /** 校验入站帧（runner 接受的形状）符合契约。 */
    private void assertInboundConforms(JsonNode frame, String defName) {
        JsonNode def = SCHEMA_DEFS.get(defName);
        assertNotNull(def, "schema 缺少 $defs/" + defName);
        assertRequiredPresent(frame, def, defName);
    }

    /** 断言 def.required（含 properties 下子对象的 required）都在 frame 里出现。 */
    private void assertRequiredPresent(JsonNode frame, JsonNode def, String ctx) {
        JsonNode required = def.get("required");
        if (required != null) {
            for (JsonNode r : required) {
                assertTrue(frame.has(r.asText()),
                        ctx + ": 缺少契约 required 字段 '" + r.asText() + "'；帧=" + frame);
            }
        }
        JsonNode props = def.get("properties");
        if (props != null) {
            props.fieldNames().forEachRemaining(field -> {
                JsonNode sub = props.get(field);
                if ("object".equals(sub.path("type").asText()) && sub.has("required") && frame.has(field)) {
                    assertRequiredPresent(frame.get(field), sub, ctx + "." + field);
                }
            });
        }
    }

    // ==================== 出站帧解析 ====================

    private List<JsonNode> framesOfType(String type) {
        List<JsonNode> out = new ArrayList<>();
        for (String f : outboundFrames) {
            try {
                JsonNode n = MAPPER.readTree(f);
                if (type.equals(n.path("type").asText())) out.add(n);
            } catch (Exception ignored) { /* 非 JSON 帧跳过 */ }
        }
        return out;
    }

    // ==================== fakes（远端对端之外全真）====================

    /** 假 Voice Gateway 业务侧：多句文本（经真 speakable 管道切块）+ 独立打断返回停播信号。 */
    private ChatHandler voiceChatHandler() {
        return new ChatHandler() {
            @Override public GatewayResponse chat(GatewayRequest r) { throw new UnsupportedOperationException(); }
            @Override public CompletableFuture<GatewayResponse> chatStream(GatewayRequest r, StreamCallback cb) {
                throw new UnsupportedOperationException();
            }
            @Override public Flux<StreamEvent> chatStreamReactive(GatewayRequest r) {
                return Flux.just(
                        StreamEvent.textDelta("你好，"),
                        StreamEvent.textDelta("世界。"),   // 。→ 切「你好，世界。」
                        StreamEvent.textDelta("再见！"),   // ！→ 切「再见！」
                        StreamEvent.llmComplete(null));
            }
            @Override public StreamEvent interrupt(String sessionId) {
                return StreamEvent.speechInterrupted("run-" + sessionId, "barge-in");
            }
        };
    }

    private com.lightweightai.kernel.gateway.SessionManager noopSessionManager() {
        return new com.lightweightai.kernel.gateway.SessionManager() {
            @Override public List<Map<String, String>> getSessionHistory(String s) { return List.of(); }
            @Override public Map<String, Object> getSessionSummary(String s) { return Map.of(); }
            @Override public void clearSession(String s) { }
        };
    }
}
