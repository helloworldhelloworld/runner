package com.lightweightai.kernel.prompt;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Skill 单元测试 — 独立能力单元
 *
 * 覆盖：Builder、matchesTrigger、fromTool/fromTools 工厂方法、ToolDefinition
 */
@DisplayName("Skill - 能力单元")
class SkillTest {

    @Test
    @DisplayName("name 为 null 抛异常")
    void nameRequired() {
        assertThrows(NullPointerException.class, () ->
                Skill.builder().build());
    }

    @Test
    @DisplayName("Builder 默认值")
    void builderDefaults() {
        Skill skill = Skill.builder().name("test").build();
        assertEquals("test", skill.getName());
        assertEquals("", skill.getDescription());
        assertEquals("", skill.getSystemPrompt());
        assertTrue(skill.getTools().isEmpty());
        assertTrue(skill.getTriggers().isEmpty());
        assertEquals(100, skill.getPriority());
    }

    @Test
    @DisplayName("matchesTrigger 无触发词时返回 false")
    void matchesTriggerNoTriggers() {
        Skill skill = Skill.builder().name("test").build();
        assertFalse(skill.matchesTrigger("anything"));
    }

    @Test
    @DisplayName("matchesTrigger 精确匹配（大小写不敏感）")
    void matchesTriggerCaseInsensitive() {
        Skill skill = Skill.builder()
                .name("weather")
                .addTrigger("天气")
                .addTrigger("weather")
                .build();

        assertTrue(skill.matchesTrigger("今天天气怎么样？"));
        assertTrue(skill.matchesTrigger("What is the WEATHER today?"));
        assertFalse(skill.matchesTrigger("Hello there"));
    }

    @Test
    @DisplayName("matchesTrigger 子串匹配")
    void matchesTriggerSubstring() {
        Skill skill = Skill.builder()
                .name("calc")
                .addTrigger("计算")
                .build();

        assertTrue(skill.matchesTrigger("帮我计算一下"));
    }

    @Test
    @DisplayName("fromTool 从 Tool 创建 Skill")
    void fromTool() {
        Tool mockTool = createMockTool("calculator", "Performs math", Map.of("expr", Map.of("type", "string")));
        Skill skill = Skill.fromTool(mockTool);

        assertEquals("calculator", skill.getName());
        assertEquals("Performs math", skill.getDescription());
        assertEquals(1, skill.getTools().size());
        assertEquals("calculator", skill.getTools().get(0).getName());
        assertEquals(1, skill.getToolInstances().size());
    }

    @Test
    @DisplayName("fromTools 从多个 Tool 创建 Skill")
    void fromTools() {
        Tool tool1 = createMockTool("add", "Add numbers", Map.of());
        Tool tool2 = createMockTool("sub", "Subtract numbers", Map.of());

        Skill skill = Skill.fromTools("math", "Math operations", List.of(tool1, tool2));

        assertEquals("math", skill.getName());
        assertEquals(2, skill.getTools().size());
        assertEquals(2, skill.getToolInstances().size());
    }

    @Test
    @DisplayName("ToolDefinition toSchema 格式正确")
    void toolDefinitionToSchema() {
        Skill.ToolDefinition td = new Skill.ToolDefinition(
                "get_time", "Returns current time", Map.of("tz", Map.of("type", "string")));

        Map<String, Object> schema = td.toSchema();
        assertEquals("get_time", schema.get("name"));
        assertEquals("Returns current time", schema.get("description"));
        assertNotNull(schema.get("input_schema"));
    }

    @Test
    @DisplayName("getter 返回防御性拷贝")
    void defensiveCopy() {
        Skill skill = Skill.builder()
                .name("test")
                .addTrigger("hello")
                .build();

        List<String> triggers = skill.getTriggers();
        triggers.add("injected");
        assertEquals(1, skill.getTriggers().size());
    }

    @Test
    @DisplayName("toString 包含关键信息")
    void toStringContainsInfo() {
        Skill skill = Skill.builder()
                .name("weather")
                .priority(10)
                .build();
        String str = skill.toString();
        assertTrue(str.contains("weather"));
        assertTrue(str.contains("10"));
    }

    private Tool createMockTool(String name, String description, Map<String, Object> properties) {
        return new Tool() {
            @Override
            public String getName() { return name; }
            @Override
            public String getDescription() { return description; }
            @Override
            public ToolSchema getSchema() { return ToolSchema.withProperties(properties); }
            @Override
            public ToolResult execute(Map<String, Object> args) { return ToolResult.success("ok"); }
        };
    }
}
