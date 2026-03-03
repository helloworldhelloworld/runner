package com.lightweightai.kernel.prompt;

import com.lightweightai.kernel.memory.InMemoryProvider;
import com.lightweightai.kernel.memory.MemoryProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD: PromptEngine 测试 (OpenClaw 风格)
 *
 * PromptEngine 负责：
 * 1. 基于 Skill 规范组装上下文
 * 2. 渐进式加载 Skill（按需）
 * 3. 输出可观测的完整 Prompt
 */
@DisplayName("PromptEngine - 上下文构建引擎")
class PromptEngineTest {

    private PromptEngine engine;
    private MemoryProvider memory;

    @BeforeEach
    void setUp() {
        memory = new InMemoryProvider();
        engine = PromptEngine.builder()
            .memoryProvider(memory)
            .build();
    }

    // ==================== Skill 规范测试 ====================

    @Test
    @DisplayName("注册并加载 Skill")
    void shouldRegisterAndLoadSkill() {
        // Given - 定义一个 Skill
        Skill weatherSkill = Skill.builder()
            .name("weather")
            .description("获取天气信息")
            .systemPrompt("你可以查询天气信息。")
            .addTool("get_weather", "获取指定城市天气", new HashMap<String, Object>() {{
                put("city", new HashMap<String, Object>() {{ put("type", "string"); put("description", "城市名称"); }});
            }})
            .build();

        // When
        engine.registerSkill(weatherSkill);

        // Then
        assertTrue(engine.hasSkill("weather"));
        assertEquals(1, engine.getRegisteredSkills().size());
    }

    @Test
    @DisplayName("Skill 的 system prompt 注入到上下文")
    void shouldInjectSkillSystemPrompt() {
        // Given
        Skill skill = Skill.builder()
            .name("helper")
            .systemPrompt("你是一个乐于助人的助手。")
            .build();
        engine.registerSkill(skill);

        // When - 构建 prompt 并激活 skill
        PromptContext context = engine.build(PromptRequest.builder()
            .userMessage("你好")
            .sessionId("session-1")
            .activeSkills(Collections.singletonList("helper"))
            .build());

        // Then
        String systemPrompt = context.getSystemPrompt();
        assertTrue(systemPrompt.contains("乐于助人"));
    }

    @Test
    @DisplayName("Skill 的工具自动注册")
    void shouldRegisterSkillTools() {
        // Given
        Skill skill = Skill.builder()
            .name("calculator")
            .addTool("calculate", "执行数学计算", new HashMap<String, Object>() {{
                put("expression", Collections.singletonMap("type", "string"));
            }})
            .build();
        engine.registerSkill(skill);

        // When
        PromptContext context = engine.build(PromptRequest.builder()
            .userMessage("1+1=?")
            .activeSkills(Collections.singletonList("calculator"))
            .build());

        // Then
        assertTrue(context.hasTools());
        assertEquals(1, context.getTools().size());
        assertEquals("calculate", context.getTools().get(0).getName());
    }

    // ==================== 渐进式加载测试 ====================

    @Test
    @DisplayName("渐进式加载 - 只加载激活的 Skill")
    void shouldOnlyLoadActiveSkills() {
        // Given - 注册多个 skill
        engine.registerSkill(Skill.builder()
            .name("weather")
            .systemPrompt("天气 skill")
            .addTool("get_weather", "获取天气", Collections.emptyMap())
            .build());

        engine.registerSkill(Skill.builder()
            .name("calculator")
            .systemPrompt("计算器 skill")
            .addTool("calculate", "计算", Collections.emptyMap())
            .build());

        engine.registerSkill(Skill.builder()
            .name("translator")
            .systemPrompt("翻译 skill")
            .addTool("translate", "翻译", Collections.emptyMap())
            .build());

        // When - 只激活 weather skill
        PromptContext context = engine.build(PromptRequest.builder()
            .userMessage("北京天气")
            .activeSkills(Collections.singletonList("weather"))
            .build());

        // Then - 只有 weather 的 prompt 和 tool
        assertTrue(context.getSystemPrompt().contains("天气"));
        assertFalse(context.getSystemPrompt().contains("计算器"));
        assertFalse(context.getSystemPrompt().contains("翻译"));
        assertEquals(1, context.getTools().size());
    }

    @Test
    @DisplayName("渐进式加载 - 自动检测需要的 Skill")
    void shouldAutoDetectRequiredSkills() {
        // Given
        engine.registerSkill(Skill.builder()
            .name("weather")
            .description("天气查询")
            .triggers(Arrays.asList("天气", "气温", "下雨"))
            .systemPrompt("天气查询 skill")
            .build());

        engine.registerSkill(Skill.builder()
            .name("calculator")
            .description("数学计算")
            .triggers(Arrays.asList("计算", "加", "减", "乘", "除"))
            .systemPrompt("计算 skill")
            .build());

        // When - 不指定 activeSkills，让引擎自动检测
        PromptContext context = engine.build(PromptRequest.builder()
            .userMessage("北京今天天气怎么样？")
            .autoDetectSkills(true)
            .build());

        // Then - 自动激活 weather skill
        assertTrue(context.getActiveSkillNames().contains("weather"));
        assertFalse(context.getActiveSkillNames().contains("calculator"));
    }

    @Test
    @DisplayName("渐进式加载 - 组合多个 Skill")
    void shouldCombineMultipleSkills() {
        // Given
        engine.registerSkill(Skill.builder()
            .name("base")
            .systemPrompt("基础能力：你是一个助手。")
            .priority(0) // 高优先级，先加载
            .build());

        engine.registerSkill(Skill.builder()
            .name("weather")
            .systemPrompt("天气能力：你可以查询天气。")
            .priority(10)
            .build());

        // When
        PromptContext context = engine.build(PromptRequest.builder()
            .userMessage("天气")
            .activeSkills(Arrays.asList("base", "weather"))
            .build());

        // Then - 按优先级组合
        String systemPrompt = context.getSystemPrompt();
        int baseIndex = systemPrompt.indexOf("基础能力");
        int weatherIndex = systemPrompt.indexOf("天气能力");
        assertTrue(baseIndex < weatherIndex, "base skill should come before weather skill");
    }

    // ==================== 记忆集成测试 ====================

    @Test
    @DisplayName("记忆搜索结果注入到上下文")
    void shouldInjectMemoryContext() {
        // Given
        memory.writeEphemeral("用户喜欢喝咖啡");
        memory.writeDurable("用户信息", "用户名: 张三");

        // When - 使用包含关键词的查询
        PromptContext context = engine.build(PromptRequest.builder()
            .userMessage("咖啡")  // 直接用关键词搜索
            .sessionId("session-1")
            .build());

        // Then
        assertTrue(context.hasMemoryContext());
        String memorySection = context.getMemoryContext();
        assertTrue(memorySection.contains("咖啡"));
    }

    @Test
    @DisplayName("对话历史注入到上下文")
    void shouldInjectConversationHistory() {
        // Given
        memory.addMessage("session-1",
            com.lightweightai.kernel.memory.Message.user("你好"));
        memory.addMessage("session-1",
            com.lightweightai.kernel.memory.Message.assistant("你好！"));

        // When
        PromptContext context = engine.build(PromptRequest.builder()
            .userMessage("我之前说了什么？")
            .sessionId("session-1")
            .build());

        // Then
        assertEquals(2, context.getHistoryMessages().size());
    }

    // ==================== 可观测性测试 ====================

    @Test
    @DisplayName("输出完整可观测的 Prompt 结构")
    void shouldOutputObservablePromptContext() {
        // Given
        engine.registerSkill(Skill.builder()
            .name("test")
            .systemPrompt("测试 skill")
            .addTool("test_tool", "测试工具", Collections.emptyMap())
            .build());

        memory.writeEphemeral("测试记忆");

        // When
        PromptContext context = engine.build(PromptRequest.builder()
            .userMessage("测试消息")
            .sessionId("session-1")
            .activeSkills(Collections.singletonList("test"))
            .build());

        // Then - 可观测的结构
        assertNotNull(context.getSystemPrompt());
        assertNotNull(context.getTools());
        assertNotNull(context.getUserMessage());
        assertNotNull(context.toDebugString()); // 调试输出

        // 验证调试输出包含关键信息
        String debug = context.toDebugString();
        assertTrue(debug.contains("System Prompt"));
        assertTrue(debug.contains("Active Skills"));
        assertTrue(debug.contains("Tools"));
    }

    @Test
    @DisplayName("记录 Prompt 构建过程")
    void shouldRecordBuildProcess() {
        // Given
        engine.registerSkill(Skill.builder().name("s1").build());
        engine.registerSkill(Skill.builder().name("s2").build());

        // When
        PromptContext context = engine.build(PromptRequest.builder()
            .userMessage("test")
            .activeSkills(Arrays.asList("s1", "s2"))
            .build());

        // Then - 构建日志
        List<String> buildLog = context.getBuildLog();
        assertFalse(buildLog.isEmpty());
        assertTrue(buildLog.stream().anyMatch(log -> log.contains("s1")));
    }
}
