package com.lightweightai.web.config;

import com.lightweightai.web.postprocess.CardAppendProcessor;
import com.lightweightai.web.postprocess.DeeplinkProcessor;
import com.lightweightai.web.postprocess.RiskControlProcessor;
import com.lightweightai.web.postprocess.SimpleRiskChecker;
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
}
