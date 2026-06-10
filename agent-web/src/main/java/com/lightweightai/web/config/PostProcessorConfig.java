package com.lightweightai.web.config;

import com.lightweightai.kernel.core.postprocess.EmotionClassifier;
import com.lightweightai.kernel.core.postprocess.SpeakableChunkProcessor;
import com.lightweightai.kernel.trace.export.StreamEventSpanExporter;
import com.lightweightai.web.postprocess.CardAppendProcessor;
import com.lightweightai.web.postprocess.DeeplinkProcessor;
import com.lightweightai.web.postprocess.RiskControlProcessor;
import com.lightweightai.web.postprocess.SimpleRiskChecker;
import com.lightweightai.web.postprocess.SpanTracePostProcessor;
import com.lightweightai.web.postprocess.TracingPostProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 流式后处理器 Bean 注册
 *
 * 每个处理器仅在其依赖接口有 Bean 实现时才激活。
 * 业务方只需注册对应的 RiskChecker / DeeplinkResolver / CardProvider Bean，
 * 处理器即自动注入 Gateway 后处理管道。
 */
@Configuration
public class PostProcessorConfig {

    /**
     * 默认风控检查器 — 关键词 + 正则匹配
     * 可通过 app.risk-control.enabled=false 关闭
     */
    @Bean
    @ConditionalOnProperty(name = "app.risk-control.enabled", havingValue = "true", matchIfMissing = true)
    public RiskControlProcessor.RiskChecker simpleRiskChecker() {
        return new SimpleRiskChecker();
    }

    @Bean
    @ConditionalOnBean(RiskControlProcessor.RiskChecker.class)
    public RiskControlProcessor riskControlProcessor(RiskControlProcessor.RiskChecker checker) {
        return new RiskControlProcessor(checker, 50);
    }

    @Bean
    @ConditionalOnBean(DeeplinkProcessor.DeeplinkResolver.class)
    public DeeplinkProcessor deeplinkProcessor(DeeplinkProcessor.DeeplinkResolver resolver) {
        return new DeeplinkProcessor(resolver);
    }

    @Bean
    @ConditionalOnBean(CardAppendProcessor.CardProvider.class)
    public CardAppendProcessor cardAppendProcessor(CardAppendProcessor.CardProvider provider) {
        return new CardAppendProcessor(provider);
    }

    /**
     * 调用链追踪处理器 — 默认启用
     * 可通过 app.tracing.enabled=false 关闭
     */
    @Bean
    @ConditionalOnProperty(name = "app.tracing.enabled", havingValue = "true", matchIfMissing = true)
    public TracingPostProcessor tracingPostProcessor() {
        return new TracingPostProcessor();
    }

    /**
     * Span 追踪后处理器 — 将 span 数据注入流末尾
     */
    @Bean
    @ConditionalOnProperty(name = "app.tracing.enabled", havingValue = "true", matchIfMissing = true)
    public SpanTracePostProcessor spanTracePostProcessor(StreamEventSpanExporter exporter) {
        return new SpanTracePostProcessor(exporter);
    }

    /**
     * Minion R2/R3 — 可说块 + 逐块 emotion 后处理器（见 ADR-006 / architecture.md Embodiment）。
     *
     * 默认 <b>关闭</b>：仅语音/具身部署设 {@code app.voice.speakable-chunk.enabled=true} 才接入，
     * 非语音 persona/部署不受影响、不增加分句开销。打开后经 {@link GatewayConfig} 汇入真 Gateway
     * 后处理管道，在 {@code LLM_COMPLETE} 前切出带 emotion 的 {@code SPEAKABLE_CHUNK} 喂下游 Voice Gateway。
     *
     * <p>{@link EmotionClassifier} 可选：业务注册一个 Bean 即覆盖默认（{@link EmotionClassifier#NEUTRAL}）。
     */
    @Bean
    @ConditionalOnProperty(name = "app.voice.speakable-chunk.enabled", havingValue = "true", matchIfMissing = false)
    public SpeakableChunkProcessor speakableChunkProcessor(
            @Autowired(required = false) EmotionClassifier emotionClassifier) {
        return new SpeakableChunkProcessor(emotionClassifier); // null → NEUTRAL（构造器内兜底）
    }
}
