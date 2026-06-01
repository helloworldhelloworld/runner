package com.lightweightai.kernel.core.postprocess;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.StreamEvent.EventType;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * R2 — 流式分句后处理器。
 *
 * 监听 {@link EventType#TEXT_DELTA} 流，把零碎的文本片段累积成完整小句，在句子边界处
 * 注入 {@link EventType#SPEAKABLE_CHUNK} 事件，让下游 Voice Gateway 能"第一句还没说完就
 * 开始合成下一句"（首响低延迟，对话自然流畅的关键）。
 *
 * 行为：
 * - 原始 TEXT_DELTA 原样透传（不破坏既有文本流 / UI 逐字渲染）。
 * - 命中句子结束符（。！？!?；;…\n 及英文 . 后接空白）即切出一个可说块。
 * - {@link EventType#LLM_COMPLETE} 前 flush 剩余未成句文本为最后一个可说块，
 *   保证所有 SPEAKABLE_CHUNK 都在 LLM_COMPLETE 之前。
 *
 * 状态（缓冲 + 序号）在 {@link Flux#defer} 内按订阅分配，故同一实例可安全复用 / 共享。
 */
public final class SpeakableChunkProcessor implements StreamPostProcessor {

    /** 句子结束符（中英混排）。*/
    private static final String SENTENCE_ENDERS = "。！？!?；;…\n";

    @Override
    public String getName() {
        return "speakable-chunk";
    }

    @Override
    public Flux<StreamEvent> apply(Flux<StreamEvent> upstream) {
        return Flux.defer(() -> {
            StringBuilder buffer = new StringBuilder();
            AtomicInteger index = new AtomicInteger();

            return upstream.concatMap(event -> {
                if (event.getType() == EventType.TEXT_DELTA) {
                    List<StreamEvent> out = new ArrayList<>();
                    out.add(event); // 透传原始片段
                    if (event.getTextDelta() != null) {
                        buffer.append(event.getTextDelta());
                        drainSentences(buffer, index, out);
                    }
                    return Flux.fromIterable(out);
                }

                if (event.getType() == EventType.LLM_COMPLETE) {
                    List<StreamEvent> out = new ArrayList<>();
                    String remaining = buffer.toString().trim();
                    if (!remaining.isEmpty()) {
                        out.add(StreamEvent.speakableChunk(remaining, index.getAndIncrement()));
                    }
                    buffer.setLength(0);
                    out.add(event); // chunk 先于 LLM_COMPLETE
                    return Flux.fromIterable(out);
                }

                return Flux.just(event);
            });
        });
    }

    /** 把 buffer 中已成句的部分切出为 SPEAKABLE_CHUNK，残留未成句文本留在 buffer。*/
    private void drainSentences(StringBuilder buffer, AtomicInteger index, List<StreamEvent> out) {
        int cut;
        while ((cut = nextBoundary(buffer)) >= 0) {
            String sentence = buffer.substring(0, cut + 1).trim();
            buffer.delete(0, cut + 1);
            if (!sentence.isEmpty()) {
                out.add(StreamEvent.speakableChunk(sentence, index.getAndIncrement()));
            }
        }
    }

    /** 返回 buffer 中第一个句子边界字符的下标（含），无则 -1。*/
    private int nextBoundary(CharSequence buffer) {
        for (int i = 0; i < buffer.length(); i++) {
            char c = buffer.charAt(i);
            if (SENTENCE_ENDERS.indexOf(c) >= 0) {
                return i;
            }
            // 英文句点：仅当其后是空白或字符串结尾时才视为句末，避免切断 3.14 / U.S.
            if (c == '.' && (i + 1 >= buffer.length() || Character.isWhitespace(buffer.charAt(i + 1)))) {
                return i;
            }
        }
        return -1;
    }
}
