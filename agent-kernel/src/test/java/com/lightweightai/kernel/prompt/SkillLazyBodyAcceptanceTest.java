package com.lightweightai.kernel.prompt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Outside-in Acceptance Test: Skill body 懒加载
 *
 * 目的:大型 Skill(如 OpenClaw 风格的 Markdown 文档)不应在注册时就被读到内存;
 * 只在 PromptEngine 实际激活该 Skill 时才调用 supplier。
 */
@DisplayName("Acceptance: Skill body 懒加载")
class SkillLazyBodyAcceptanceTest {

    @Test
    @DisplayName("Skill.lazyBody:未激活时 supplier 不被调用")
    void supplierNotInvokedWhenSkillInactive() {
        AtomicInteger loadCount = new AtomicInteger(0);
        Skill lazy = Skill.builder()
                .name("docs")
                .description("documentation")
                .lazyBody(() -> {
                    loadCount.incrementAndGet();
                    return "the very long body";
                })
                .build();

        PromptEngine engine = PromptEngine.builder().build();
        engine.registerSkill(lazy);

        // 用户消息既无显式激活 docs,也无触发词命中
        engine.build(PromptRequest.builder()
                .userMessage("nothing relevant")
                .autoDetectSkills(true)
                .build());

        assertEquals(0, loadCount.get(), "未激活的 skill 不应触发 supplier 加载");
    }

    @Test
    @DisplayName("Skill.lazyBody:显式激活时 supplier 调用且内容进入 system prompt")
    void supplierInvokedOnceWhenSkillActivated() {
        AtomicInteger loadCount = new AtomicInteger(0);
        Skill lazy = Skill.builder()
                .name("docs")
                .lazyBody(() -> {
                    loadCount.incrementAndGet();
                    return "## DOCS-BODY-MARKER\nfull docs here";
                })
                .build();

        PromptEngine engine = PromptEngine.builder().build();
        engine.registerSkill(lazy);

        PromptContext ctx = engine.build(PromptRequest.builder()
                .userMessage("hi")
                .activeSkills(List.of("docs"))
                .build());

        assertTrue(loadCount.get() >= 1, "激活后 supplier 至少调一次");
        assertTrue(ctx.getSystemPrompt().contains("DOCS-BODY-MARKER"),
                "system prompt 应含 supplier 返回的内容: " + ctx.getSystemPrompt());
    }

    @Test
    @DisplayName("Skill.lazyBody:触发词激活同样会加载 supplier")
    void triggerActivationAlsoLoadsSupplier() {
        AtomicInteger loadCount = new AtomicInteger(0);
        Skill lazy = Skill.builder()
                .name("weather")
                .triggers(List.of("forecast"))
                .lazyBody(() -> {
                    loadCount.incrementAndGet();
                    return "WEATHER-BODY";
                })
                .build();

        PromptEngine engine = PromptEngine.builder().build();
        engine.registerSkill(lazy);

        PromptContext ctx = engine.build(PromptRequest.builder()
                .userMessage("show me the forecast for tokyo")
                .autoDetectSkills(true)
                .build());

        assertTrue(loadCount.get() >= 1, "触发词命中后 supplier 应被调用");
        assertTrue(ctx.getSystemPrompt().contains("WEATHER-BODY"));
    }

    @Test
    @DisplayName("旧式 systemPrompt(String):零行为变化(向后兼容)")
    void eagerStringRemainsBackwardCompatible() {
        Skill eager = Skill.builder()
                .name("eager")
                .systemPrompt("EAGER-BODY")
                .build();

        PromptEngine engine = PromptEngine.builder().build();
        engine.registerSkill(eager);

        PromptContext ctx = engine.build(PromptRequest.builder()
                .userMessage("hi")
                .activeSkills(List.of("eager"))
                .build());

        assertTrue(ctx.getSystemPrompt().contains("EAGER-BODY"));
    }
}
