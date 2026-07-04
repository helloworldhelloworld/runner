package com.lightweightai.kernel.bench;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.StreamEvent.EventType;
import com.lightweightai.kernel.core.postprocess.EmotionClassifier;
import com.lightweightai.kernel.core.postprocess.SpeakableChunkProcessor;
import com.lightweightai.kernel.core.postprocess.SpeakableChunkProcessor.FirstChunkPolicy;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.openrouter.OpenRouterProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 语音首响延迟 spike — 阶段 1：runner 侧微基准（手动、环境门控、丢弃式）。
 *
 * <p>量延迟链路里最贵且方差主导的两段（业界共识，见 todo/2026-06-17 风险探针 + ADR 依据）：
 * <ul>
 *   <li><b>LLM_TTFT</b>：订阅 → 第一个 {@code TEXT_DELTA}（time-to-first-token）；</li>
 *   <li><b>chunk_form</b>：订阅 → 第一个 {@code SPEAKABLE_CHUNK}（首个可说块成形，喂 TTS 的起点）。</li>
 * </ul>
 * 对比首块策略：默认句末切 vs {@link FirstChunkPolicy#eagerDefault() eager 早发}，量 chunk_form 降幅。
 *
 * <p><b>门控</b>（仿 {@code RealPiConnectionIT} 的 assumeTrue：不静默假绿、不进 CI 关键路径）——
 * 仅当设置 {@code LATENCY_SPIKE=1} 且配齐真 LLM（Qwen/DashScope）env 才跑，否则干净跳过：
 * <pre>
 *   LATENCY_SPIKE=1 \
 *   OPENROUTER_API_KEY=sk-... \
 *   OPENROUTER_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1 \
 *   OPENROUTER_MODEL=qwen-plus \
 *   [LATENCY_SPIKE_REPS=30] \
 *   mvn -pl agent-kernel test -Dtest=LatencySpikeBench
 * </pre>
 * 结果打印到 stdout（分段 P50/P95/P99 表 + 策略对比），手动誊进 latency-spike-results.md。
 */
@DisplayName("Bench: 语音首响延迟（LLM_TTFT + chunk_form，真 Qwen，门控）")
class LatencySpikeBench {

    /** 固定 prompt 集：短答 / 长答（覆盖不同生成长度下的首响）。*/
    private static final List<String> PROMPTS = List.of(
            "用一句话打个招呼。",
            "简单说说今天适合做什么。",
            "讲一段三四句话的自我介绍，你是一个叫小黄人的桌面机器人。",
            "解释一下什么是延迟，用两三句话。"
    );

    @Test
    void measureFirstResponseLatency() {
        assumeTrue("1".equals(System.getenv("LATENCY_SPIKE")),
                "未设 LATENCY_SPIKE=1，跳过延迟 spike（手动 bench）");
        String apiKey = System.getenv("OPENROUTER_API_KEY");
        String baseUrl = System.getenv("OPENROUTER_BASE_URL");
        String model = System.getenv("OPENROUTER_MODEL");
        assumeTrue(notBlank(apiKey) && notBlank(baseUrl) && notBlank(model),
                "未配齐 OPENROUTER_API_KEY / _BASE_URL / _MODEL（真 Qwen），跳过");

        int reps = parseIntOr(System.getenv("LATENCY_SPIKE_REPS"), 30);
        OpenRouterProvider provider = new OpenRouterProvider(apiKey, model, baseUrl);

        System.out.printf("%n=== 延迟 spike（model=%s, reps=%d, prompts=%d）===%n",
                model, reps, PROMPTS.size());

        Sample sentenceOnly = measure(provider, FirstChunkPolicy.sentenceOnly(), reps);
        Sample eager = measure(provider, FirstChunkPolicy.eagerDefault(), reps);

        System.out.println("\n-- 默认（句末切首块）--");
        report("LLM_TTFT", sentenceOnly.ttft);
        report("chunk_form", sentenceOnly.chunkForm);
        System.out.println("\n-- eager（子句/24字符 早发首块）--");
        report("LLM_TTFT", eager.ttft);
        report("chunk_form", eager.chunkForm);

        long base = p50(sentenceOnly.chunkForm);
        long ea = p50(eager.chunkForm);
        System.out.printf("%n>> chunk_form P50: 默认 %dms → eager %dms（降 %dms）%n", base, ea, base - ea);
        System.out.printf(">> 预算对照：LLM_TTFT P50=%dms（业界线 <400ms）%n", p50(sentenceOnly.ttft));
        System.out.println("（TTFA 还含 EOU/STT/TTS_TTFB，见全链 harness 阶段 2-4）");
    }

    /** 跑 reps×prompts，收集每次的 TTFT 与 chunk_form（ms）。*/
    private Sample measure(OpenRouterProvider provider, FirstChunkPolicy policy, int reps) {
        Sample s = new Sample();
        LLMOptions opts = LLMOptions.builder().maxTokens(256).build();
        for (int r = 0; r < reps; r++) {
            for (String prompt : PROMPTS) {
                long[] one = measureOnce(provider, policy, opts, prompt);
                if (one[0] >= 0) s.ttft.add(one[0]);
                if (one[1] >= 0) s.chunkForm.add(one[1]);
            }
        }
        return s;
    }

    /** 单次：返回 {ttftMs, chunkFormMs}，缺失为 -1。用单调时钟，订阅起算。 */
    private long[] measureOnce(OpenRouterProvider provider, FirstChunkPolicy policy,
                               LLMOptions opts, String prompt) {
        List<ConversationMessage> msgs = List.of(ConversationMessage.builder()
                .role(MessageRole.USER).textContent(prompt).build());
        AtomicLong subscribeNanos = new AtomicLong();
        AtomicLong firstToken = new AtomicLong(-1);
        AtomicLong firstChunk = new AtomicLong(-1);
        try {
            provider.completeStreamReactive(msgs, opts)
                    .transform(new SpeakableChunkProcessor(EmotionClassifier.NEUTRAL, policy))
                    .doOnSubscribe(x -> subscribeNanos.set(System.nanoTime()))
                    .doOnNext(e -> {
                        long now = System.nanoTime();
                        if (e.getType() == EventType.TEXT_DELTA) {
                            firstToken.compareAndSet(-1, now);
                        } else if (e.getType() == EventType.SPEAKABLE_CHUNK) {
                            firstChunk.compareAndSet(-1, now);
                        }
                    })
                    .blockLast(Duration.ofSeconds(60));
        } catch (Exception e) {
            System.err.println("bench rep 失败（跳过该次）：" + e.getMessage());
            return new long[]{-1, -1};
        }
        long sub = subscribeNanos.get();
        return new long[]{toMs(firstToken.get(), sub), toMs(firstChunk.get(), sub)};
    }

    private static long toMs(long markNanos, long subscribeNanos) {
        return markNanos < 0 ? -1 : (markNanos - subscribeNanos) / 1_000_000;
    }

    private static void report(String name, List<Long> xs) {
        if (xs.isEmpty()) { System.out.printf("  %-12s 无样本%n", name); return; }
        System.out.printf("  %-12s n=%d  P50=%dms  P95=%dms  P99=%dms  min=%dms  max=%dms%n",
                name, xs.size(), p(xs, 50), p(xs, 95), p(xs, 99),
                Collections.min(xs), Collections.max(xs));
    }

    private static long p50(List<Long> xs) { return xs.isEmpty() ? -1 : p(xs, 50); }

    /** 第 q 百分位（最近秩法）。*/
    private static long p(List<Long> xs, int q) {
        List<Long> s = new ArrayList<>(xs);
        Collections.sort(s);
        int idx = (int) Math.ceil(q / 100.0 * s.size()) - 1;
        return s.get(Math.max(0, Math.min(idx, s.size() - 1)));
    }

    private static boolean notBlank(String v) { return v != null && !v.isBlank(); }

    private static int parseIntOr(String v, int dflt) {
        try { return v == null ? dflt : Integer.parseInt(v.trim()); } catch (Exception e) { return dflt; }
    }

    private static final class Sample {
        final List<Long> ttft = new ArrayList<>();
        final List<Long> chunkForm = new ArrayList<>();
    }
}
