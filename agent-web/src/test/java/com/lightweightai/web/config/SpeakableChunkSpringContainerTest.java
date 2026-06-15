package com.lightweightai.web.config;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.postprocess.SpeakableChunkProcessor;
import com.lightweightai.kernel.core.postprocess.StreamPostProcessor;
import com.lightweightai.kernel.gateway.ChatHandler;
import com.lightweightai.kernel.gateway.Gateway;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.gateway.GatewayResponse;
import com.lightweightai.kernel.gateway.SessionManager;
import com.lightweightai.kernel.trace.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M-4 修复：speakable-chunk Bean 的 Spring <b>真容器</b>激活 + 自动收集进管道测试。
 *
 * <p>背景（巡检 A2/M-4）：{@link SpeakableChunkWiringAcceptanceTest} 用
 * {@code new PostProcessorConfig().speakableChunkProcessor(...)} 并手工把 list 传进
 * {@code new GatewayConfig().gateway(..., List.of(speakable))}，<b>绕开了</b> Spring 的
 * {@code @ConditionalOnProperty} 激活判定 与 {@code List<StreamPostProcessor>} 自动收集——
 * 即「条件 Bean 真能在容器里按开关激活、并经 {@link GatewayConfig} 汇入后处理管道」从未被真容器验证。
 *
 * <p>本测试起一个真 {@link AnnotationConfigApplicationContext}，由容器自己评估
 * {@code @ConditionalOnProperty} 并自动收集 {@code List<StreamPostProcessor>} 注入 {@code Gateway}：
 * <ul>
 *   <li>{@code app.voice.speakable-chunk.enabled=true} → Bean 激活，且真的进了 Gateway 管道；</li>
 *   <li>属性缺省（matchIfMissing=false） / 显式 false → Bean 不创建，管道里没有它。</li>
 * </ul>
 *
 * <p>同类 Bean（risk-control / tracing，matchIfMissing=true）在测试里显式关掉，把被测面收敛到
 * speakable 一个条件上；其余 {@code @ConditionalOnBean} 的处理器因无对应依赖 Bean 自然不激活。
 */
@DisplayName("M-4 真容器: speakable-chunk @ConditionalOnProperty 激活 + 自动汇入 Gateway 管道")
class SpeakableChunkSpringContainerTest {

    /**
     * 起一个真 Spring 容器：注册真 {@link PostProcessorConfig} + {@link GatewayConfig} + 桩依赖，
     * 固定关掉 risk-control / tracing，只留 speakable 一个被测开关；额外属性由参数注入。
     */
    private AnnotationConfigApplicationContext containerWith(String... extraProps) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        Map<String, Object> props = new HashMap<>();
        props.put("app.risk-control.enabled", "false");
        props.put("app.tracing.enabled", "false");
        for (String kv : extraProps) {
            int eq = kv.indexOf('=');
            props.put(kv.substring(0, eq), kv.substring(eq + 1));
        }
        ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test-props", props));
        ctx.register(PostProcessorConfig.class, GatewayConfig.class, StubGatewayDeps.class);
        ctx.refresh();
        return ctx;
    }

    @Test
    @DisplayName("enabled=true → 容器激活 SpeakableChunkProcessor Bean 并自动收进 Gateway 管道")
    void enabledTrue_activatesBeanAndCollectsIntoPipeline() {
        try (AnnotationConfigApplicationContext ctx =
                     containerWith("app.voice.speakable-chunk.enabled=true")) {
            // 1) @ConditionalOnProperty 真在容器里判定为「激活」
            assertEquals(1, ctx.getBeansOfType(SpeakableChunkProcessor.class).size(),
                    "enabled=true 时容器应激活恰好一个 speakable-chunk Bean");

            // 2) 该 Bean 被 GatewayConfig 自动收集（List<StreamPostProcessor> 注入）汇入真管道
            List<StreamPostProcessor> pipeline = pipelineOf(ctx.getBean(Gateway.class));
            assertTrue(pipeline.stream().anyMatch(p -> p instanceof SpeakableChunkProcessor),
                    "speakable-chunk 应经自动收集进入 Gateway 后处理管道；实际管道: "
                            + pipeline.stream().map(StreamPostProcessor::getName).toList());
        }
    }

    @Test
    @DisplayName("属性缺省（matchIfMissing=false）→ 容器不创建 Bean，管道里没有它")
    void propertyMissing_beanNotCreated() {
        try (AnnotationConfigApplicationContext ctx = containerWith()) {
            assertTrue(ctx.getBeansOfType(SpeakableChunkProcessor.class).isEmpty(),
                    "属性缺省时（matchIfMissing=false）容器不应创建 speakable-chunk Bean（生产默认 OFF）");
            assertFalse(pipelineOf(ctx.getBean(Gateway.class)).stream()
                            .anyMatch(p -> p instanceof SpeakableChunkProcessor),
                    "未激活时 Gateway 管道里不应有 speakable-chunk");
        }
    }

    @Test
    @DisplayName("enabled=false → 容器不创建 Bean")
    void enabledFalse_beanNotCreated() {
        try (AnnotationConfigApplicationContext ctx =
                     containerWith("app.voice.speakable-chunk.enabled=false")) {
            assertTrue(ctx.getBeansOfType(SpeakableChunkProcessor.class).isEmpty(),
                    "显式 enabled=false 时容器不应创建 speakable-chunk Bean");
        }
    }

    /** Gateway 管道（可能为空）的处理器列表，永不返回 null。 */
    private static List<StreamPostProcessor> pipelineOf(Gateway gw) {
        return gw.getPostProcessorPipeline() != null
                ? gw.getPostProcessorPipeline().getProcessors()
                : List.of();
    }

    /** GatewayConfig.gateway() 所需的最小依赖 Bean（ChatHandler / SessionManager；Tracer 可选）。 */
    @Configuration
    static class StubGatewayDeps {
        @Bean
        ChatHandler chatHandler() {
            return new ChatHandler() {
                @Override public GatewayResponse chat(GatewayRequest r) { throw new UnsupportedOperationException(); }
                @Override public CompletableFuture<GatewayResponse> chatStream(GatewayRequest r, StreamCallback cb) {
                    throw new UnsupportedOperationException();
                }
                @Override public Flux<StreamEvent> chatStreamReactive(GatewayRequest r) { return Flux.empty(); }
            };
        }

        @Bean
        Tracer tracer() {
            return Tracer.NOOP;
        }

        @Bean
        SessionManager sessionManager() {
            return new SessionManager() {
                @Override public List<Map<String, String>> getSessionHistory(String s) { return List.of(); }
                @Override public Map<String, Object> getSessionSummary(String s) { return Map.of(); }
                @Override public void clearSession(String s) { }
            };
        }
    }
}
