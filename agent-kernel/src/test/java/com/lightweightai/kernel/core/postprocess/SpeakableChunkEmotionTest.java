package com.lightweightai.kernel.core.postprocess;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.StreamEvent.EventType;
import com.lightweightai.kernel.llm.LLMResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R3（逐块 emotion）acceptance test —— SPEAKABLE_CHUNK 携带逐块情绪，
 * 供设备 Realizer 驱动眼睛/LED/语气。emotion 来源是可插拔的 {@link EmotionClassifier}。
 */
@DisplayName("SpeakableChunkProcessor - 逐块 emotion")
class SpeakableChunkEmotionTest {

    private List<StreamEvent> chunks(List<StreamEvent> events) {
        return events.stream().filter(e -> e.getType() == EventType.SPEAKABLE_CHUNK).toList();
    }

    @Test
    @DisplayName("默认无分类器 → 每个 chunk 仍带 emotion 字段(neutral)")
    void defaultCarriesNeutralEmotion() {
        List<StreamEvent> out = new SpeakableChunkProcessor()
                .apply(Flux.just(
                        StreamEvent.textDelta("你好。"),
                        StreamEvent.llmComplete(LLMResponse.builder().build())))
                .collectList().block();

        List<StreamEvent> cs = chunks(out);
        assertEquals(1, cs.size());
        assertEquals("neutral", cs.get(0).getData().get("emotion"));
    }

    @Test
    @DisplayName("注入分类器 → 每个 chunk 的 emotion 来自分类器")
    void classifierDrivesPerChunkEmotion() {
        EmotionClassifier classifier = text -> text.contains("！") ? "excited" : "calm";

        List<StreamEvent> out = new SpeakableChunkProcessor(classifier)
                .apply(Flux.just(
                        StreamEvent.textDelta("今天天气不错。"),
                        StreamEvent.textDelta("太棒了！"),
                        StreamEvent.llmComplete(LLMResponse.builder().build())))
                .collectList().block();

        List<StreamEvent> cs = chunks(out);
        assertEquals(2, cs.size());
        assertEquals("calm", cs.get(0).getData().get("emotion"));
        assertEquals("今天天气不错。", cs.get(0).getData().get("text"));
        assertEquals("excited", cs.get(1).getData().get("emotion"));
        assertEquals("太棒了！", cs.get(1).getData().get("text"));
    }

    @Test
    @DisplayName("flush 的残余块也带 emotion")
    void flushedChunkAlsoCarriesEmotion() {
        EmotionClassifier classifier = text -> "happy";

        List<StreamEvent> out = new SpeakableChunkProcessor(classifier)
                .apply(Flux.just(
                        StreamEvent.textDelta("没有句号结尾"),
                        StreamEvent.llmComplete(LLMResponse.builder().build())))
                .collectList().block();

        List<StreamEvent> cs = chunks(out);
        assertEquals(1, cs.size());
        assertEquals("happy", cs.get(0).getData().get("emotion"));
    }
}
