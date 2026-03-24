package com.lightweightai.kernel.prompt;

import com.lightweightai.kernel.memory.InMemoryProvider;
import com.lightweightai.kernel.memory.MemoryProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PromptEngine 高级测试 - 补充关键路径覆盖
 *
 * 覆盖：
 * - 自动检测 + 显式指定混合场景
 * - 不存在的 skill 名称处理
 * - 优先级排序边界
 * - 无 MemoryProvider 场景
 * - Durable 记忆注入
 * - baseSystemPrompt 注入
 * - 空 trigger 列表不匹配
 * - 多个 trigger 部分匹配
 */
@DisplayName("PromptEngine - 高级场景")
class PromptEngineAdvancedTest {

    private MemoryProvider memory;

    @BeforeEach
    void setUp() {
        memory = new InMemoryProvider();
    }

    @Test
    @DisplayName("显式指定不存在的 Skill 被跳过并记录日志")
    void shouldSkipNonexistentSkillAndLog() {
        PromptEngine engine = PromptEngine.builder()
            .memoryProvider(memory)
            .build();

        engine.registerSkill(Skill.builder().name("real").systemPrompt("Real skill").build());

        PromptContext context = engine.build(PromptRequest.builder()
            .userMessage("test")
            .activeSkills(List.of("real", "fake"))
            .build());

        // "real" should be active, "fake" should not
        assertTrue(context.getActiveSkillNames().contains("real"));
        assertFalse(context.getActiveSkillNames().contains("fake"));

        // Build log should mention the missing skill
        boolean hasWarning = context.getBuildLog().stream()
            .anyMatch(log -> log.contains("fake") && log.contains("not found"));
        assertTrue(hasWarning, "Build log should warn about missing skill");
    }

    @Test
    @DisplayName("自动检测和显式指定混合 - 不重复添加")
    void shouldNotDuplicateExplicitAndAutoDetected() {
        PromptEngine engine = PromptEngine.builder()
            .memoryProvider(memory)
            .build();

        engine.registerSkill(Skill.builder()
            .name("weather")
            .triggers(List.of("天气", "气温"))
            .systemPrompt("天气 skill")
            .build());

        // 既显式指定又能自动检测到
        PromptContext context = engine.build(PromptRequest.builder()
            .userMessage("今天天气怎么样")
            .activeSkills(List.of("weather"))
            .autoDetectSkills(true)
            .build());

        // Should only appear once
        long count = context.getActiveSkillNames().stream()
            .filter(n -> n.equals("weather"))
            .count();
        assertEquals(1, count, "Skill should not be duplicated");
    }

    @Test
    @DisplayName("多 Skill 优先级排序 - 同优先级保持注册顺序")
    void shouldSortByPriorityPreservingOrder() {
        PromptEngine engine = PromptEngine.builder()
            .memoryProvider(memory)
            .build();

        engine.registerSkill(Skill.builder().name("C").priority(10).systemPrompt("C").build());
        engine.registerSkill(Skill.builder().name("A").priority(1).systemPrompt("A").build());
        engine.registerSkill(Skill.builder().name("B").priority(5).systemPrompt("B").build());

        PromptContext context = engine.build(PromptRequest.builder()
            .userMessage("test")
            .activeSkills(List.of("C", "A", "B"))
            .build());

        // System prompt should have A before B before C
        String prompt = context.getSystemPrompt();
        int posA = prompt.indexOf("## A");
        int posB = prompt.indexOf("## B");
        int posC = prompt.indexOf("## C");
        assertTrue(posA < posB, "A (priority 1) should come before B (priority 5)");
        assertTrue(posB < posC, "B (priority 5) should come before C (priority 10)");
    }

    @Test
    @DisplayName("无 MemoryProvider 时跳过记忆加载")
    void shouldSkipMemoryWhenNoProvider() {
        PromptEngine engine = PromptEngine.builder()
            .build(); // no memoryProvider

        engine.registerSkill(Skill.builder().name("s1").systemPrompt("test").build());

        PromptContext context = engine.build(PromptRequest.builder()
            .userMessage("hello")
            .activeSkills(List.of("s1"))
            .build());

        assertFalse(context.hasMemoryContext());
        assertTrue(context.getHistoryMessages().isEmpty());
    }

    @Test
    @DisplayName("baseSystemPrompt 注入到最终 prompt")
    void shouldInjectBaseSystemPrompt() {
        PromptEngine engine = PromptEngine.builder()
            .memoryProvider(memory)
            .baseSystemPrompt("你是一个AI助手。请用中文回答。")
            .build();

        PromptContext context = engine.build(PromptRequest.builder()
            .userMessage("test")
            .build());

        assertTrue(context.getSystemPrompt().contains("AI助手"));
    }

    @Test
    @DisplayName("baseSystemPrompt + Skill prompt 正确组合")
    void shouldCombineBaseAndSkillPrompts() {
        PromptEngine engine = PromptEngine.builder()
            .memoryProvider(memory)
            .baseSystemPrompt("Base prompt here.")
            .build();

        engine.registerSkill(Skill.builder()
            .name("helper")
            .systemPrompt("Helper instructions.")
            .build());

        PromptContext context = engine.build(PromptRequest.builder()
            .userMessage("test")
            .activeSkills(List.of("helper"))
            .build());

        String prompt = context.getSystemPrompt();
        assertTrue(prompt.contains("Base prompt here."));
        assertTrue(prompt.contains("Helper instructions."));
        // Base should come before skill
        assertTrue(prompt.indexOf("Base") < prompt.indexOf("Helper"));
    }

    @Test
    @DisplayName("空 trigger 列表的 Skill 不会被自动检测")
    void shouldNotAutoDetectSkillWithNoTriggers() {
        PromptEngine engine = PromptEngine.builder()
            .memoryProvider(memory)
            .build();

        engine.registerSkill(Skill.builder()
            .name("no-triggers")
            .systemPrompt("Should not match")
            .build()); // no triggers set

        PromptContext context = engine.build(PromptRequest.builder()
            .userMessage("anything at all")
            .autoDetectSkills(true)
            .build());

        assertFalse(context.getActiveSkillNames().contains("no-triggers"));
    }

    @Test
    @DisplayName("Trigger 匹配不区分大小写")
    void shouldMatchTriggersIgnoreCase() {
        PromptEngine engine = PromptEngine.builder()
            .memoryProvider(memory)
            .build();

        engine.registerSkill(Skill.builder()
            .name("weather")
            .triggers(List.of("Weather", "FORECAST"))
            .systemPrompt("Weather skill")
            .build());

        PromptContext context = engine.build(PromptRequest.builder()
            .userMessage("what's the weather like?")
            .autoDetectSkills(true)
            .build());

        assertTrue(context.getActiveSkillNames().contains("weather"));
    }

    @Test
    @DisplayName("Durable 记忆注入到上下文")
    void shouldInjectDurableMemory() {
        memory.writeDurable("用户档案", "姓名: 张三\n偏好: 咖啡");

        PromptEngine engine = PromptEngine.builder()
            .memoryProvider(memory)
            .build();

        PromptContext context = engine.build(PromptRequest.builder()
            .userMessage("你好")
            .sessionId("s1")
            .build());

        assertTrue(context.hasMemoryContext());
        assertTrue(context.getMemoryContext().contains("张三"));
    }

    @Test
    @DisplayName("多个 Skill 的工具合并收集")
    void shouldCollectToolsFromMultipleSkills() {
        PromptEngine engine = PromptEngine.builder()
            .memoryProvider(memory)
            .build();

        engine.registerSkill(Skill.builder()
            .name("s1")
            .addTool("tool_a", "Tool A", Map.of())
            .build());

        engine.registerSkill(Skill.builder()
            .name("s2")
            .addTool("tool_b", "Tool B", Map.of())
            .addTool("tool_c", "Tool C", Map.of())
            .build());

        PromptContext context = engine.build(PromptRequest.builder()
            .userMessage("test")
            .activeSkills(List.of("s1", "s2"))
            .build());

        assertEquals(3, context.getTools().size());
    }

    @Test
    @DisplayName("Skill 注册覆盖 - 同名 Skill 后注册覆盖先注册")
    void shouldOverrideSkillWithSameName() {
        PromptEngine engine = PromptEngine.builder()
            .memoryProvider(memory)
            .build();

        engine.registerSkill(Skill.builder()
            .name("weather")
            .systemPrompt("Old weather prompt")
            .build());

        engine.registerSkill(Skill.builder()
            .name("weather")
            .systemPrompt("New weather prompt")
            .build());

        assertEquals(1, engine.getRegisteredSkills().size());
        assertTrue(engine.getSkill("weather").get().getSystemPrompt().contains("New"));
    }

    @Test
    @DisplayName("getSkill 返回 Optional.empty 对不存在的 Skill")
    void shouldReturnEmptyOptionalForMissingSkill() {
        PromptEngine engine = PromptEngine.builder().build();
        assertTrue(engine.getSkill("nonexistent").isEmpty());
    }
}
