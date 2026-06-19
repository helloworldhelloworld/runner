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
 * <p><b>边界（重要）</b>：切块只作用于 LLM 的<b>输出</b>文本流，产物 {@code SPEAKABLE_CHUNK} 发给
 * 下游 <b>TTS 播放</b>；<b>不</b>回灌给 LLM、<b>不</b>进对话历史 / 下一轮 prompt（那里始终是
 * {@code LLM_COMPLETE} 携带的完整响应）。因此即使 eager 把首块在子句处切碎，模型侧与记忆侧
 * 的语义始终完整——切碎只可能影响 TTS 首句韵律与该块的 emotion 判断，不影响"模型怎么理解"。
 *
 * 行为：
 * - 原始 TEXT_DELTA 原样透传（不破坏既有文本流 / UI 逐字渲染）。
 * - 命中句子结束符（。！？!?；;…\n 及英文 . 后接空白）即切出一个可说块。
 * - {@link EventType#LLM_COMPLETE} 前 flush 剩余未成句文本为最后一个可说块，
 *   保证所有 SPEAKABLE_CHUNK 都在 LLM_COMPLETE 之前。
 *
 * <p><b>首块早发（{@link FirstChunkPolicy}，默认关闭）</b>：首响延迟的主导杠杆之一是"首个可说块要
 * 等够一整句"。开启 eager 策略后，<b>仅首个</b>可说块可在更早的边界切出——子句末（逗号/顿号/冒号）、
 * 或累计达 {@code maxChars} 字符硬切——句末/子句末/字符上限三者先到先发；首块之后回到句末切分。
 * 默认 {@link FirstChunkPolicy#sentenceOnly()} 保持原有行为，向后兼容。
 *
 * 状态（缓冲 + 序号）在 {@link Flux#defer} 内按订阅分配，故同一实例可安全复用 / 共享。
 */
public final class SpeakableChunkProcessor implements StreamPostProcessor {

    /** 句子结束符（中英混排）。*/
    private static final String SENTENCE_ENDERS = "。！？!?；;…\n";

    /** 逐块情绪来源（R3），默认中性。*/
    private final EmotionClassifier emotionClassifier;
    /** 首块早发策略（默认句末切，向后兼容）。*/
    private final FirstChunkPolicy firstChunkPolicy;

    public SpeakableChunkProcessor() {
        this(EmotionClassifier.NEUTRAL);
    }

    public SpeakableChunkProcessor(EmotionClassifier emotionClassifier) {
        this(emotionClassifier, FirstChunkPolicy.sentenceOnly());
    }

    public SpeakableChunkProcessor(EmotionClassifier emotionClassifier, FirstChunkPolicy firstChunkPolicy) {
        this.emotionClassifier = emotionClassifier != null ? emotionClassifier : EmotionClassifier.NEUTRAL;
        this.firstChunkPolicy = firstChunkPolicy != null ? firstChunkPolicy : FirstChunkPolicy.sentenceOnly();
    }

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
                        drainChunks(buffer, index, out);
                    }
                    return Flux.fromIterable(out);
                }

                if (event.getType() == EventType.LLM_COMPLETE) {
                    List<StreamEvent> out = new ArrayList<>();
                    String remaining = buffer.toString().trim();
                    if (!remaining.isEmpty()) {
                        out.add(emit(remaining, index));
                    }
                    buffer.setLength(0);
                    out.add(event); // chunk 先于 LLM_COMPLETE
                    return Flux.fromIterable(out);
                }

                return Flux.just(event);
            });
        });
    }

    /** 把 buffer 中可切出的部分切为 SPEAKABLE_CHUNK，残留留在 buffer。首块可按 eager 策略提前切。*/
    private void drainChunks(StringBuilder buffer, AtomicInteger index, List<StreamEvent> out) {
        int cut;
        while ((cut = boundaryFor(buffer, index.get())) >= 0) {
            String sentence = buffer.substring(0, cut + 1).trim();
            buffer.delete(0, cut + 1);
            if (!sentence.isEmpty()) {
                out.add(emit(sentence, index));
            }
        }
    }

    /**
     * 返回当前应切分的边界下标（含），无则 -1。
     * 首块且 eager 开启时：句末 / 子句末 / maxChars 三者最早；之后（及默认）只按句末。
     */
    private int boundaryFor(StringBuilder buffer, int emittedCount) {
        if (firstChunkPolicy.eager && emittedCount == 0) {
            int sentence = nextBoundary(buffer, SENTENCE_ENDERS, true);
            int clause = firstChunkPolicy.clauseEnders.isEmpty()
                    ? -1 : nextBoundary(buffer, firstChunkPolicy.clauseEnders, false);
            int earliest = minNonNeg(sentence, clause);
            if (earliest >= 0) {
                return earliest;
            }
            // 既无句末也无子句末：达到字符上限则硬切首块（首响优先，宁可切碎）。
            if (firstChunkPolicy.maxChars > 0 && buffer.length() >= firstChunkPolicy.maxChars) {
                return firstChunkPolicy.maxChars - 1;
            }
            return -1;
        }
        return nextBoundary(buffer, SENTENCE_ENDERS, true);
    }

    /** 构造一个带逐块情绪的 SPEAKABLE_CHUNK 并自增序号。*/
    private StreamEvent emit(String sentence, AtomicInteger index) {
        return StreamEvent.speakableChunk(sentence, index.getAndIncrement(),
                emotionClassifier.classify(sentence));
    }

    /**
     * 返回 buffer 中第一个边界字符（来自 {@code enders}）的下标（含），无则 -1。
     * {@code englishPeriod=true} 时把"英文句点后接空白/结尾"也当边界（仅句末用，避免切断 3.14 / U.S.）。
     */
    private int nextBoundary(CharSequence buffer, String enders, boolean englishPeriod) {
        for (int i = 0; i < buffer.length(); i++) {
            char c = buffer.charAt(i);
            if (enders.indexOf(c) >= 0) {
                return i;
            }
            if (englishPeriod && c == '.'
                    && (i + 1 >= buffer.length() || Character.isWhitespace(buffer.charAt(i + 1)))) {
                return i;
            }
        }
        return -1;
    }

    /** 两个下标里更早的非负值；都为负返回 -1。*/
    private static int minNonNeg(int a, int b) {
        if (a < 0) return b;
        if (b < 0) return a;
        return Math.min(a, b);
    }

    /**
     * 首块早发策略（首响延迟杠杆）。默认 {@link #sentenceOnly()} 关闭，保持按句末切。
     * eager 开启后仅影响<b>首个</b>可说块：在子句末（{@code clauseEnders}）或累计 {@code maxChars}
     * 字符处提前切，与句末取最早者。
     */
    public static final class FirstChunkPolicy {
        /** 默认子句结束符：逗号 / 顿号 / 冒号（中英）。*/
        public static final String DEFAULT_CLAUSE_ENDERS = "，,、：:";

        final boolean eager;
        final String clauseEnders;
        final int maxChars;

        private FirstChunkPolicy(boolean eager, String clauseEnders, int maxChars) {
            this.eager = eager;
            this.clauseEnders = clauseEnders != null ? clauseEnders : "";
            this.maxChars = maxChars;
        }

        /** 默认：仅按句末切首块（向后兼容）。*/
        public static FirstChunkPolicy sentenceOnly() {
            return new FirstChunkPolicy(false, "", 0);
        }

        /** 首块早发：子句末（{@code clauseEnders}）或达 {@code maxChars} 字符提前切。{@code maxChars<=0} 表示不按字符上限切。*/
        public static FirstChunkPolicy eager(String clauseEnders, int maxChars) {
            return new FirstChunkPolicy(true, clauseEnders, maxChars);
        }

        /** 常用 eager：默认子句符 + 24 字符上限。*/
        public static FirstChunkPolicy eagerDefault() {
            return eager(DEFAULT_CLAUSE_ENDERS, 24);
        }
    }
}
