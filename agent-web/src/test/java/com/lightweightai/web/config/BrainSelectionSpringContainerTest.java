package com.lightweightai.web.config;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.gateway.ChatHandler;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.gateway.GatewayResponse;
import com.lightweightai.openclaw.OpenClawChatHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.MapPropertySource;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ADR-014 真容器：{@code app.brain.type} 在真 Spring 容器里按 {@code @ConditionalOnProperty} 选脑，
 * 且 native / openclaw 两个 {@code @Primary ChatHandler} <b>同时只存在一个</b>（互斥，不 NoUniqueBean）。
 *
 * <p>native 侧用 {@link NativeBrainStub} 忠实复刻 {@code OrchestratorConfig.orchestratorChatHandler} 的
 * 注解（{@code @Primary} + {@code @ConditionalOnProperty(brain.type=native, matchIfMissing)}），不拖入其重依赖；
 * openclaw 侧装真 {@link BrainConfig}（OpenClawClient 为阶段3占位骨架，构造不连接）。
 */
@DisplayName("ADR-014 真容器: app.brain.type 选 native/openclaw 脑（互斥 @Primary）")
class BrainSelectionSpringContainerTest {

    private AnnotationConfigApplicationContext containerWith(String... extraProps) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        Map<String, Object> props = new HashMap<>();
        for (String kv : extraProps) {
            int eq = kv.indexOf('=');
            props.put(kv.substring(0, eq), kv.substring(eq + 1));
        }
        ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test-props", props));
        ctx.register(NativeBrainStub.class, BrainConfig.class);
        ctx.refresh();
        return ctx;
    }

    @Test
    @DisplayName("缺省（matchIfMissing）→ native 脑为 primary，openclaw beans 不创建")
    void defaultSelectsNative() {
        try (AnnotationConfigApplicationContext ctx = containerWith()) {
            assertEquals(1, ctx.getBeansOfType(ChatHandler.class).size(), "应恰好一个 ChatHandler");
            assertTrue(ctx.getBean(ChatHandler.class) instanceof NativeStubHandler, "缺省应选 native");
            assertTrue(ctx.getBeansOfType(OpenClawChatHandler.class).isEmpty(), "openclaw 脑不应创建");
        }
    }

    @Test
    @DisplayName("app.brain.type=native → 显式 native")
    void explicitNativeSelectsNative() {
        try (AnnotationConfigApplicationContext ctx = containerWith("app.brain.type=native")) {
            assertTrue(ctx.getBean(ChatHandler.class) instanceof NativeStubHandler);
            assertTrue(ctx.getBeansOfType(OpenClawChatHandler.class).isEmpty());
        }
    }

    @Test
    @DisplayName("app.brain.type=openclaw → OpenClawChatHandler 为 primary，native 不创建（无双 @Primary 冲突）")
    void openclawSelectsOpenClaw() {
        try (AnnotationConfigApplicationContext ctx = containerWith("app.brain.type=openclaw")) {
            assertEquals(1, ctx.getBeansOfType(ChatHandler.class).size(), "应恰好一个 ChatHandler（互斥）");
            assertTrue(ctx.getBean(ChatHandler.class) instanceof OpenClawChatHandler, "应选 OpenClaw 脑");
        }
    }

    /** 忠实复刻 OrchestratorConfig.orchestratorChatHandler 的条件 + @Primary（不拖入重依赖）。 */
    @Configuration
    static class NativeBrainStub {
        @Bean
        @Primary
        @ConditionalOnProperty(name = "app.brain.type", havingValue = "native", matchIfMissing = true)
        ChatHandler nativeBrain() {
            return new NativeStubHandler();
        }
    }

    static class NativeStubHandler implements ChatHandler {
        @Override public GatewayResponse chat(GatewayRequest r) { throw new UnsupportedOperationException(); }
        @Override public CompletableFuture<GatewayResponse> chatStream(GatewayRequest r, StreamCallback cb) {
            throw new UnsupportedOperationException();
        }
        @Override public Flux<StreamEvent> chatStreamReactive(GatewayRequest r) { return Flux.empty(); }
    }
}
