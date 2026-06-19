package com.lightweightai.kernel.core.postprocess;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.StreamEvent.EventType;
import com.lightweightai.kernel.llm.LLMResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R2（可说块边界事件）acceptance test —— SpeakableChunkProcessor 监听 TEXT_DELTA 流，
 * 按句子边界注入 {@link EventType#SPEAKABLE_CHUNK} 事件，使 Voice Gateway 能"边想边说"。
 *
 * 规则：
 * - 原始 TEXT_DELTA 原样透传（不破坏既有文本流）。
 * - 句子结束符（。！？!?\n 等）处切出可说块，注入 SPEAKABLE_CHUNK。
 * - LLM_COMPLETE 前 flush 剩余未成句文本为最后一个 SPEAKABLE_CHUNK。
 * - 所有 SPEAKABLE_CHUNK 必须出现在 LLM_COMPLETE 之前。
 */
@DisplayName("SpeakableChunkProcessor - 流式分句")
class SpeakableChunkProcessorTest {

    private final SpeakableChunkProcessor processor = new SpeakableChunkProcessor();

    private List<StreamEvent> run(Flux<StreamEvent> input) {
        return input.transform(processor).collectList().block();
    }

    private List<String> speakableTexts(List<StreamEvent> events) {
        return events.stream()
                .filter(e -> e.getType() == EventType.SPEAKABLE_CHUNK)
                .map(e -> (String) e.getData().get("text"))
                .toList();
    }

    @Test
    @DisplayName("多句流 → 按句子边界发出多个 SPEAKABLE_CHUNK")
    void emitsChunkPerSentence() {
        List<StreamEvent> out = run(Flux.just(
                StreamEvent.textDelta("你好，"),
                StreamEvent.textDelta("我是小黄人。"),
                StreamEvent.textDelta("今天"),
                StreamEvent.textDelta("天气不错！"),
                StreamEvent.llmComplete(LLMResponse.builder().build())
        ));

        assertEquals(List.of("你好，我是小黄人。", "今天天气不错！"), speakableTexts(out));
    }

    @Test
    @DisplayName("原始 TEXT_DELTA 原样透传")
    void passesThroughTextDeltas() {
        List<StreamEvent> out = run(Flux.just(
                StreamEvent.textDelta("a。"),
                StreamEvent.textDelta("b。"),
                StreamEvent.llmComplete(LLMResponse.builder().build())
        ));

        List<String> deltas = out.stream()
                .filter(e -> e.getType() == EventType.TEXT_DELTA)
                .map(StreamEvent::getTextDelta)
                .toList();
        assertEquals(List.of("a。", "b。"), deltas);
    }

    @Test
    @DisplayName("结尾无句号的残余文本在 LLM_COMPLETE 前 flush 成最后一个 chunk")
    void flushesTrailingTextBeforeComplete() {
        List<StreamEvent> out = run(Flux.just(
                StreamEvent.textDelta("一句话。"),
                StreamEvent.textDelta("没有句号结尾"),
                StreamEvent.llmComplete(LLMResponse.builder().build())
        ));

        assertEquals(List.of("一句话。", "没有句号结尾"), speakableTexts(out));

        int lastChunkIdx = -1, completeIdx = -1;
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i).getType() == EventType.SPEAKABLE_CHUNK) lastChunkIdx = i;
            if (out.get(i).getType() == EventType.LLM_COMPLETE) completeIdx = i;
        }
        assertTrue(lastChunkIdx >= 0 && completeIdx >= 0);
        assertTrue(lastChunkIdx < completeIdx, "所有 SPEAKABLE_CHUNK 必须在 LLM_COMPLETE 之前");
    }

    @Test
    @DisplayName("SPEAKABLE_CHUNK 带递增 index")
    void chunksCarryIncrementingIndex() {
        List<StreamEvent> out = run(Flux.just(
                StreamEvent.textDelta("第一句。第二句。"),
                StreamEvent.llmComplete(LLMResponse.builder().build())
        ));

        List<Integer> indices = out.stream()
                .filter(e -> e.getType() == EventType.SPEAKABLE_CHUNK)
                .map(e -> (Integer) e.getData().get("index"))
                .toList();
        assertEquals(List.of(0, 1), indices);
    }

    // ==================== 首块早发策略（首响延迟杠杆）====================

    private List<String> speakableTexts(Flux<StreamEvent> input, SpeakableChunkProcessor p) {
        return input.transform(p).collectList().block().stream()
                .filter(e -> e.getType() == EventType.SPEAKABLE_CHUNK)
                .map(e -> (String) e.getData().get("text"))
                .toList();
    }

    @Test
    @DisplayName("eager：首块在首个子句边界(逗号)提前切，其余仍按句末；默认则整句")
    void eagerFirstChunkCutsAtClause() {
        Flux<StreamEvent> in = Flux.just(
                StreamEvent.textDelta("你好，我是小黄人。"),
                StreamEvent.textDelta("今天天气不错！"),
                StreamEvent.llmComplete(LLMResponse.builder().build()));

        // 默认（句末）：首块要等整句
        assertEquals(List.of("你好，我是小黄人。", "今天天气不错！"),
                speakableTexts(Flux.just(
                        StreamEvent.textDelta("你好，我是小黄人。"),
                        StreamEvent.textDelta("今天天气不错！"),
                        StreamEvent.llmComplete(LLMResponse.builder().build())),
                        new SpeakableChunkProcessor()));

        // eager：首块在第一个逗号就切，后续回到句末
        SpeakableChunkProcessor eager = new SpeakableChunkProcessor(
                EmotionClassifier.NEUTRAL,
                SpeakableChunkProcessor.FirstChunkPolicy.eager("，", 0));
        assertEquals(List.of("你好，", "我是小黄人。", "今天天气不错！"), speakableTexts(in, eager));
    }

    @Test
    @DisplayName("eager：无句末/子句边界时，达 maxChars 字符硬切首块")
    void eagerFirstChunkCutsAtMaxChars() {
        SpeakableChunkProcessor eager = new SpeakableChunkProcessor(
                EmotionClassifier.NEUTRAL,
                SpeakableChunkProcessor.FirstChunkPolicy.eager("", 5));
        // "abcdefg" 无任何边界：首块按 5 字符硬切，余下随 complete flush
        assertEquals(List.of("abcde", "fg"), speakableTexts(Flux.just(
                StreamEvent.textDelta("abcdefg"),
                StreamEvent.llmComplete(LLMResponse.builder().build())), eager));
    }

    // ==================== 首块 max-wait 时间维度兜底（ADR-013）====================

    @Test
    @DisplayName("eager+maxWait：慢 token 无边界，到 maxWait 墙钟即整段 flush 首块（虚拟时钟）")
    void eagerMaxWaitFlushesFirstChunkAfterTimeout() {
        SpeakableChunkProcessor p = new SpeakableChunkProcessor(
                EmotionClassifier.NEUTRAL,
                // 子句符/字符上限都不命中，只剩时间兜底（maxWait=250ms）
                SpeakableChunkProcessor.FirstChunkPolicy.eager("", 0, 250));

        StepVerifier.withVirtualTime(() ->
                        Flux.concat(
                                Flux.just(StreamEvent.textDelta("我觉得")), // 无边界、未到字符上限
                                Flux.<StreamEvent>never())                 // 静默：token 没续上
                                .transform(p))
                .expectNextMatches(e -> e.getType() == EventType.TEXT_DELTA)  // 原样透传
                .expectNoEvent(Duration.ofMillis(249))                       // 还没到 maxWait，不发块
                .thenAwait(Duration.ofMillis(1))                             // 跨过 250ms
                .expectNextMatches(e -> e.getType() == EventType.SPEAKABLE_CHUNK
                        && "我觉得".equals(e.getData().get("text"))
                        && Integer.valueOf(0).equals(e.getData().get("index")))
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("eager+maxWait：首块在 maxWait 前由子句边界切出 → 定时器取消、不重复发（虚拟时钟）")
    void eagerMaxWaitCancelledWhenClauseHitsFirst() {
        SpeakableChunkProcessor p = new SpeakableChunkProcessor(
                EmotionClassifier.NEUTRAL,
                SpeakableChunkProcessor.FirstChunkPolicy.eager("，", 0, 250));

        StepVerifier.withVirtualTime(() ->
                        Flux.concat(
                                Flux.just(StreamEvent.textDelta("你好，")), // 子句边界即切首块
                                Flux.<StreamEvent>never())
                                .transform(p))
                .expectNextMatches(e -> e.getType() == EventType.TEXT_DELTA)
                .expectNextMatches(e -> e.getType() == EventType.SPEAKABLE_CHUNK
                        && "你好，".equals(e.getData().get("text")))
                .expectNoEvent(Duration.ofMillis(500)) // 跨过 maxWait，无重复 timeout 块
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("maxWait 设了但边界都先到：结果与不设一致，时间兜底不误触、不挂尾延迟")
    void maxWaitNoOpWhenBoundariesArriveFirst() {
        SpeakableChunkProcessor p = new SpeakableChunkProcessor(
                EmotionClassifier.NEUTRAL,
                SpeakableChunkProcessor.FirstChunkPolicy.eager("，", 0, 250));
        // 同步流、立即 complete：每个边界在 t≈0 命中，定时器（250ms）发火前即被取消
        assertEquals(List.of("你好，", "我是小黄人。", "今天天气不错！"),
                speakableTexts(Flux.just(
                        StreamEvent.textDelta("你好，我是小黄人。"),
                        StreamEvent.textDelta("今天天气不错！"),
                        StreamEvent.llmComplete(LLMResponse.builder().build())), p));
    }
}
