package com.lightweightai.kernel.prompt;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Skill - 能力单元")
class SkillTest {

    // ==================== Builder ====================

    @Test
    @DisplayName("builder 构建完整 Skill")
    void shouldBuildCompleteSkill() {
        Skill skill = Skill.builder()
            .name("weather")
            .description("Weather lookup skill")
            .systemPrompt("You can check the weather.")
            .addTrigger("weather")
            .addTrigger("forecast")
            .priority(50)
            .metadata("source", "builtin")
            .build();

        assertEquals("weather", skill.getName());
        assertEquals("Weather lookup skill", skill.getDescription());
        assertEquals("You can check the weather.", skill.getSystemPrompt());
        assertEquals(List.of("weather", "forecast"), skill.getTriggers());
        assertEquals(50, skill.getPriority());
        assertEquals("builtin", skill.getMetadata().get("source"));
    }

    @Test
    @DisplayName("builder name 必填")
    void shouldRequireName() {
        assertThrows(NullPointerException.class,
            () -> Skill.builder().build());
    }

    @Test
    @DisplayName("builder 默认值")
    void shouldHaveDefaults() {
        Skill skill = Skill.builder().name("test").build();

        assertEquals("", skill.getDescription());
        assertEquals("", skill.getSystemPrompt());
        assertTrue(skill.getTools().isEmpty());
        assertTrue(skill.getToolInstances().isEmpty());
        assertTrue(skill.getTriggers().isEmpty());
        assertEquals(100, skill.getPriority());
        assertTrue(skill.getMetadata().isEmpty());
    }

    @Test
    @DisplayName("triggers 批量设置")
    void shouldSetTriggersInBulk() {
        Skill skill = Skill.builder()
            .name("math")
            .triggers(List.of("calculate", "compute", "math"))
            .build();

        assertEquals(3, skill.getTriggers().size());
    }

    // ==================== Trigger Matching ====================

    @Test
    @DisplayName("matchesTrigger 匹配包含触发词的消息")
    void shouldMatchTriggerInMessage() {
        Skill skill = Skill.builder()
            .name("weather")
            .addTrigger("weather")
            .addTrigger("forecast")
            .build();

        assertTrue(skill.matchesTrigger("What's the weather today?"));
        assertTrue(skill.matchesTrigger("Give me the forecast"));
    }

    @Test
    @DisplayName("matchesTrigger 大小写不敏感")
    void shouldMatchCaseInsensitive() {
        Skill skill = Skill.builder()
            .name("weather")
            .addTrigger("weather")
            .build();

        assertTrue(skill.matchesTrigger("WEATHER report"));
        assertTrue(skill.matchesTrigger("Weather Report"));
    }

    @Test
    @DisplayName("matchesTrigger 不匹配时返回 false")
    void shouldReturnFalseWhenNoMatch() {
        Skill skill = Skill.builder()
            .name("weather")
            .addTrigger("weather")
            .build();

        assertFalse(skill.matchesTrigger("Hello world"));
    }

    @Test
    @DisplayName("matchesTrigger 无触发词返回 false")
    void shouldReturnFalseWhenNoTriggers() {
        Skill skill = Skill.builder().name("empty").build();

        assertFalse(skill.matchesTrigger("anything"));
    }

    // ==================== Factory Methods ====================

    @Test
    @DisplayName("fromTool 从单个 Tool 创建 Skill")
    void shouldCreateSkillFromSingleTool() {
        Tool tool = createSimpleTool("calculator", "Basic math");

        Skill skill = Skill.fromTool(tool);

        assertEquals("calculator", skill.getName());
        assertEquals("Basic math", skill.getDescription());
        assertEquals(1, skill.getTools().size());
        assertEquals(1, skill.getToolInstances().size());
        assertEquals("calculator", skill.getTools().get(0).getName());
    }

    @Test
    @DisplayName("fromTools 从多个 Tool 创建 Skill")
    void shouldCreateSkillFromMultipleTools() {
        Tool tool1 = createSimpleTool("add", "Addition");
        Tool tool2 = createSimpleTool("subtract", "Subtraction");

        Skill skill = Skill.fromTools("math", "Math operations", List.of(tool1, tool2));

        assertEquals("math", skill.getName());
        assertEquals("Math operations", skill.getDescription());
        assertEquals(2, skill.getTools().size());
        assertEquals(2, skill.getToolInstances().size());
    }

    // ==================== ToolDefinition ====================

    @Test
    @DisplayName("ToolDefinition 基本创建和获取")
    void shouldCreateToolDefinition() {
        Map<String, Object> params = Map.of("city", Map.of("type", "string"));
        Skill.ToolDefinition def = new Skill.ToolDefinition("getWeather", "Get weather info", params);

        assertEquals("getWeather", def.getName());
        assertEquals("Get weather info", def.getDescription());
        assertEquals(1, def.getParameters().size());
    }

    @Test
    @DisplayName("ToolDefinition null 参数变成空 map")
    void shouldHandleNullParameters() {
        Skill.ToolDefinition def = new Skill.ToolDefinition("noArgs", "No args tool", null);

        assertNotNull(def.getParameters());
        assertTrue(def.getParameters().isEmpty());
    }

    @Test
    @DisplayName("ToolDefinition toSchema 生成 JSON Schema 格式")
    void shouldConvertToSchema() {
        Map<String, Object> params = Map.of("x", Map.of("type", "number"));
        Skill.ToolDefinition def = new Skill.ToolDefinition("calc", "Calculator", params);

        Map<String, Object> schema = def.toSchema();
        assertEquals("calc", schema.get("name"));
        assertEquals("Calculator", schema.get("description"));
        assertNotNull(schema.get("input_schema"));
        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");
        assertEquals("object", inputSchema.get("type"));
    }

    @Test
    @DisplayName("ToolDefinition getParameters 返回防御性副本")
    void shouldReturnDefensiveCopyOfParameters() {
        Map<String, Object> params = Map.of("a", "b");
        Skill.ToolDefinition def = new Skill.ToolDefinition("t", "d", params);

        Map<String, Object> p = def.getParameters();
        p.put("new", "val");
        // Original should be unchanged
        assertFalse(def.getParameters().containsKey("new"));
    }

    // ==================== Defensive Copies ====================

    @Test
    @DisplayName("getTools 返回防御性副本")
    void shouldReturnDefensiveCopyOfTools() {
        Tool tool = createSimpleTool("t", "d");
        Skill skill = Skill.fromTool(tool);

        List<Skill.ToolDefinition> tools = skill.getTools();
        tools.clear();
        assertEquals(1, skill.getTools().size());
    }

    @Test
    @DisplayName("getTriggers 返回防御性副本")
    void shouldReturnDefensiveCopyOfTriggers() {
        Skill skill = Skill.builder()
            .name("test")
            .addTrigger("trigger1")
            .build();

        List<String> triggers = skill.getTriggers();
        triggers.clear();
        assertEquals(1, skill.getTriggers().size());
    }

    @Test
    @DisplayName("getMetadata 返回防御性副本")
    void shouldReturnDefensiveCopyOfMetadata() {
        Skill skill = Skill.builder()
            .name("test")
            .metadata("k", "v")
            .build();

        Map<String, Object> meta = skill.getMetadata();
        meta.clear();
        assertEquals("v", skill.getMetadata().get("k"));
    }

    // ==================== toString ====================

    @Test
    @DisplayName("toString 包含关键信息")
    void shouldProduceReadableToString() {
        Skill skill = Skill.builder()
            .name("weather")
            .priority(50)
            .addTool("getWeather", "get weather", Map.of())
            .build();

        String str = skill.toString();
        assertTrue(str.contains("weather"));
        assertTrue(str.contains("1"));
        assertTrue(str.contains("50"));
    }

    // ==================== Helper ====================

    private Tool createSimpleTool(String name, String description) {
        return new Tool() {
            @Override
            public String getName() { return name; }

            @Override
            public String getDescription() { return description; }

            @Override
            public ToolSchema getSchema() { return ToolSchema.empty(); }

            @Override
            public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("ok");
            }
        };
    }
}
