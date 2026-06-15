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
}
