package com.lightweightai.web.config;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.StreamEvent.EventType;
import com.lightweightai.kernel.core.postprocess.EmotionClassifier;
import com.lightweightai.kernel.core.postprocess.SpeakableChunkProcessor;
import com.lightweightai.kernel.core.postprocess.StreamPostProcessor;
import com.lightweightai.kernel.gateway.ChatHandler;
import com.lightweightai.kernel.gateway.Gateway;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.gateway.GatewayResponse;
import com.lightweightai.kernel.gateway.SessionManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 🔴-1 修复的 outside-in 验收测试（transmission-chain / 集成接线）。
 *
 * <p>背景：R2/R3 早先只在 agent-kernel 落了 {@link SpeakableChunkProcessor} 类与单测，却从未注册成
 * agent-web 的 {@code StreamPostProcessor} Bean —— 真 Gateway 后处理管道里根本没有它，于是生产环境
 * 永不发出 {@code SPEAKABLE_CHUNK}（M0 验收测试在测试内手工 {@code .transform()} 绕开了真管道，
 * 掩盖了这个 wiring 断点）。
 *
 * <p>本测试按 CLAUDE.md "UT rules" 第 3/5 条：对 producer→consumer 对
 * （Gateway 后处理管道 → SpeakableChunkProcessor）补一个端到端 payload-capture 测试——
 * 驱动 <b>真</b> {@link GatewayConfig#gateway} + {@link PostProcessorConfig#speakableChunkProcessor}
 * 装配，断言多句流式响应经真管道在 {@code LLM_COMPLETE} 前发出带 emotion 的 {@code SPEAKABLE_CHUNK}。
 * 断言的是载荷（chunk 文本 + emotion + 顺序），不是 ceremony。
 */
@DisplayName("Acceptance: 可说块/emotion 经真 Gateway 管道接线 (R2/R3 生产 wiring)")
class SpeakableChunkWiringAcceptanceTest {

    /** PostProcessorConfig 的 @Bean 工厂确实产出一个可用的 speakable-chunk 处理器（注册路径存在）。*/
    @Test
    @DisplayName("PostProcessorConfig.speakableChunkProcessor 产出 speakable-chunk 处理器")
    void beanFactoryProducesProcessor() {
        SpeakableChunkProcessor p = new PostProcessorConfig().speakableChunkProcessor(null);
        assertNotNull(p, "工厂应产出处理器（null EmotionClassifier 时构造器兜底 NEUTRAL）");
        assertEquals("speakable-chunk", p.getName(), "处理器名称就位，供 Gateway 收集/日志");
    }

    /**
     * 端到端：真 GatewayConfig + 真 PostProcessorConfig.speakableChunkProcessor 组装的 Gateway，
     * 对多句流式响应在 LLM_COMPLETE 前发出带 emotion 的 SPEAKABLE_CHUNK。
     */
    @Test
    @DisplayName("真 Gateway 管道：多句响应 → LLM_COMPLETE 前 N×带 emotion 的 SPEAKABLE_CHUNK")
    void realGatewayPipelineEmitsSpeakableChunksWithEmotion() {
        // 自定义 EmotionClassifier，证明 emotion 经 Bean 注入一路透传（不只是默认 NEUTRAL）。
        EmotionClassifier classifier = text -> "cheerful";
        StreamPostProcessor speakable = new PostProcessorConfig().speakableChunkProcessor(classifier);

        // 假 ChatHandler：分片吐出两句话 + LLM_COMPLETE。
        ChatHandler handler = reactiveHandler(List.of(
                StreamEvent.textDelta("你好，"),
                StreamEvent.textDelta("世界。"),   // 句末 。→ 切第一块「你好，世界。」
                StreamEvent.textDelta("再见！"),   // 句末 ！→ 切第二块「再见！」
                StreamEvent.llmComplete(null)
        ));

        // 走真生产装配路径：GatewayConfig 自动把 speakable 收进真后处理管道。
        Gateway gw = new GatewayConfig().gateway(handler, noopSessionManager(), null, List.of(speakable));

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("s1").message("hi").build();

        List<StreamEvent> events = gw.handleStreamReactive(request).collectList().block();
        assertNotNull(events, "流应产出事件");

        List<StreamEvent> chunks = events.stream()
                .filter(e -> e.getType() == EventType.SPEAKABLE_CHUNK).toList();

        // 载荷断言：两个可说块、文本正确、emotion 来自注入的 classifier。
        assertEquals(2, chunks.size(),
                "真管道应发出 2 个 SPEAKABLE_CHUNK（断点修复前这里为 0）。实际事件类型: "
                        + events.stream().map(e -> e.getType().name()).toList());
        assertEquals("你好，世界。", chunks.get(0).getTextDelta());
        assertEquals("再见！", chunks.get(1).getTextDelta());
        assertEquals("cheerful", chunks.get(0).getData().get("emotion"),
                "逐块 emotion 必须来自注入的 EmotionClassifier，证明 R3 经 Bean 接线");
        assertEquals(0, chunks.get(0).getData().get("index"));
        assertEquals(1, chunks.get(1).getData().get("index"));

        // 顺序断言：所有 SPEAKABLE_CHUNK 都在 LLM_COMPLETE 之前。
        int lastChunkIdx = events.lastIndexOf(chunks.get(1));
        int completeIdx = indexOfType(events, EventType.LLM_COMPLETE);
        assertTrue(completeIdx >= 0, "应有 LLM_COMPLETE");
        assertTrue(lastChunkIdx < completeIdx,
                "所有可说块必须先于 LLM_COMPLETE（边想边说，下游 TTS 可在整段完成前开播）");

        // 原始 TEXT_DELTA 仍原样透传（不破坏既有逐字渲染）。
        assertTrue(events.stream().anyMatch(e -> e.getType() == EventType.TEXT_DELTA),
                "原始 TEXT_DELTA 不应被吞掉");
    }

    // ---- helpers ----

    private static int indexOfType(List<StreamEvent> events, EventType type) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).getType() == type) return i;
        }
        return -1;
    }

    private static ChatHandler reactiveHandler(List<StreamEvent> events) {
        return new ChatHandler() {
            @Override
            public GatewayResponse chat(GatewayRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<GatewayResponse> chatStream(GatewayRequest request, StreamCallback callback) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Flux<StreamEvent> chatStreamReactive(GatewayRequest request) {
                return Flux.fromIterable(events);
            }
        };
    }

    private static SessionManager noopSessionManager() {
        return new SessionManager() {
            @Override public List<Map<String, String>> getSessionHistory(String sessionId) { return List.of(); }
            @Override public Map<String, Object> getSessionSummary(String sessionId) { return Map.of(); }
            @Override public void clearSession(String sessionId) { }
        };
    }
}
