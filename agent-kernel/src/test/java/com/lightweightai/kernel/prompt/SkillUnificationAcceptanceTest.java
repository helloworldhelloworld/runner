package com.lightweightai.kernel.prompt;

import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Acceptance Test: Skill 统一后的 PromptEngine 行为
 *
 * 验证：PromptEngine 是 Skill 的唯一注册点，同时管理 system prompt + tool definitions
 */
@DisplayName("Acceptance: Skill 统一 — PromptEngine 是唯一入口")
class SkillUnificationAcceptanceTest {

    @Test
    @DisplayName("注册 Skill 后，build() 产生的 PromptContext 包含 Skill 的 systemPrompt")
    void skillSystemPromptIncludedInBuild() {
        PromptEngine engine = PromptEngine.builder()
                .memoryProvider(new StubMemory())
                .baseSystemPrompt("你是助手")
                .build();

        Skill weatherSkill = Skill.builder()
                .name("weather")
                .description("查天气")
                .systemPrompt("你可以查询天气信息。")
                .addTool("checkWeather", "查天气", Map.of())
                .build();

        engine.registerSkill(weatherSkill);

        // 使用显式激活 skill
        PromptRequest request = PromptRequest.builder()
                .userMessage("今天天气怎么样")
                .sessionId("test")
                .addActiveSkill("weather")
                .build();

        PromptContext ctx = engine.build(request);

        assertTrue(ctx.getSystemPrompt().contains("你是助手"), "Should contain base prompt");
        assertTrue(ctx.getSystemPrompt().contains("查询天气"),
                "Should contain skill system prompt: " + ctx.getSystemPrompt());
        assertFalse(ctx.getTools().isEmpty(), "Should have tool definitions from skill");
    }

    @Test
    @DisplayName("多个 Skill 注册后互不干扰")
    void multipleSkillsIndependent() {
        PromptEngine engine = PromptEngine.builder()
                .memoryProvider(new StubMemory())
                .baseSystemPrompt("base")
                .build();

        engine.registerSkill(Skill.builder().name("a").description("A").systemPrompt("Skill A prompt").build());
        engine.registerSkill(Skill.builder().name("b").description("B").systemPrompt("Skill B prompt").build());

        PromptRequest request = PromptRequest.builder()
                .userMessage("test")
                .sessionId("s1")
                .addActiveSkill("a")
                .addActiveSkill("b")
                .build();

        PromptContext ctx = engine.build(request);

        assertTrue(ctx.getSystemPrompt().contains("Skill A"), "Should contain Skill A");
        assertTrue(ctx.getSystemPrompt().contains("Skill B"), "Should contain Skill B");
    }

    @Test
    @DisplayName("getSkillObjects 返回所有已注册的 Skill 对象")
    void getSkillObjects() {
        PromptEngine engine = PromptEngine.builder()
                .memoryProvider(new StubMemory())
                .baseSystemPrompt("base")
                .build();

        engine.registerSkill(Skill.builder().name("x").description("X").build());
        engine.registerSkill(Skill.builder().name("y").description("Y").build());

        List<Skill> skills = engine.getSkillObjects();
        assertEquals(2, skills.size());
        assertTrue(skills.stream().anyMatch(s -> s.getName().equals("x")));
        assertTrue(skills.stream().anyMatch(s -> s.getName().equals("y")));
    }

    @Test
    @DisplayName("PromptSection 已废弃 — sections 列表不影响核心功能")
    void promptSectionNotRequired() {
        // PromptSection 机制仍在代码中但不再是必需的
        // Skill 的 systemPrompt 是注入 prompt 的唯一方式
        PromptEngine engine = PromptEngine.builder()
                .memoryProvider(new StubMemory())
                .baseSystemPrompt("base only")
                .build();

        PromptRequest request = PromptRequest.builder()
                .userMessage("hello")
                .sessionId("s1")
                .build();

        PromptContext ctx = engine.build(request);
        assertTrue(ctx.getSystemPrompt().contains("base only"));
        assertTrue(ctx.getTools().isEmpty(), "No skills registered → no tools");
    }

    private static class StubMemory implements MemoryProvider {
        @Override public void addMessage(String s, Message m) {}
        @Override public List<Message> getHistory(String s, int l) { return List.of(); }
        @Override public void clearSession(String s) {}
        @Override public void writeEphemeral(String c) {}
        @Override public void writeDurable(String s, String c) {}
        @Override public List<MemorySearchResult> search(String q) { return List.of(); }
    }
}
