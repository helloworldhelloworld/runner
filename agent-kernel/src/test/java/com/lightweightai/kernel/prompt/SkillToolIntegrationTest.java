package com.lightweightai.kernel.prompt;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD: Skill 与 Tool 集成测试
 *
 * 测试 Skill 能够：
 * 1. 直接包装 Tool 实例
 * 2. 从 Tool 生成工具定义
 * 3. 支持混合模式（Tool + 手动定义）
 */
@DisplayName("Skill + Tool 集成")
class SkillToolIntegrationTest {

    @Test
    @DisplayName("从 Tool 创建 Skill")
    void shouldCreateSkillFromTool() {
        // Given
        Tool weatherTool = createWeatherTool();

        // When
        Skill skill = Skill.builder()
            .name("weather")
            .description("天气查询 Skill")
            .systemPrompt("你可以查询任何城市的天气。")
            .addTool(weatherTool)
            .build();

        // Then
        assertEquals(1, skill.getTools().size());
        assertEquals("get_weather", skill.getTools().get(0).getName());
        assertEquals("获取指定城市天气", skill.getTools().get(0).getDescription());
    }

    @Test
    @DisplayName("从多个 Tool 创建 Skill")
    void shouldCreateSkillFromMultipleTools() {
        // Given
        Tool weatherTool = createWeatherTool();
        Tool forecastTool = createForecastTool();

        // When
        Skill skill = Skill.builder()
            .name("weather-service")
            .description("完整天气服务")
            .addTool(weatherTool)
            .addTool(forecastTool)
            .build();

        // Then
        assertEquals(2, skill.getTools().size());
    }

    @Test
    @DisplayName("Tool 的 Schema 转换为 Skill 工具定义")
    void shouldConvertToolSchemaToDefinition() {
        // Given
        Tool tool = createWeatherTool();

        // When
        Skill skill = Skill.builder()
            .name("weather")
            .addTool(tool)
            .build();

        // Then
        Skill.ToolDefinition def = skill.getTools().get(0);
        Map<String, Object> schema = def.toSchema();

        assertEquals("get_weather", schema.get("name"));
        assertNotNull(schema.get("input_schema"));

        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");
        assertEquals("object", inputSchema.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
        assertTrue(properties.containsKey("city"));
    }

    @Test
    @DisplayName("混合模式 - Tool 和手动定义共存")
    void shouldSupportMixedMode() {
        // Given
        Tool tool = createWeatherTool();

        // When
        Skill skill = Skill.builder()
            .name("mixed")
            .addTool(tool) // 从 Tool 添加
            .addTool("manual_tool", "手动定义的工具", // 手动添加
                Map.of("param1", Map.of("type", "string")))
            .build();

        // Then
        assertEquals(2, skill.getTools().size());
        assertTrue(skill.getTools().stream()
            .anyMatch(t -> t.getName().equals("get_weather")));
        assertTrue(skill.getTools().stream()
            .anyMatch(t -> t.getName().equals("manual_tool")));
    }

    @Test
    @DisplayName("获取 Skill 关联的原始 Tool 实例")
    void shouldGetOriginalToolInstances() {
        // Given
        Tool weatherTool = createWeatherTool();
        Tool forecastTool = createForecastTool();

        // When
        Skill skill = Skill.builder()
            .name("weather")
            .addTool(weatherTool)
            .addTool(forecastTool)
            .build();

        // Then
        List<Tool> tools = skill.getToolInstances();
        assertEquals(2, tools.size());
        assertTrue(tools.contains(weatherTool));
        assertTrue(tools.contains(forecastTool));
    }

    @Test
    @DisplayName("快捷方法 - 只包含 Tool 的 Skill")
    void shouldCreateToolOnlySkill() {
        // Given
        Tool tool = createWeatherTool();

        // When - 使用快捷方法
        Skill skill = Skill.fromTool(tool);

        // Then
        assertEquals("get_weather", skill.getName());
        assertEquals("获取指定城市天气", skill.getDescription());
        assertEquals(1, skill.getTools().size());
    }

    @Test
    @DisplayName("快捷方法 - 从多个 Tool 创建 Skill")
    void shouldCreateSkillFromToolList() {
        // Given
        List<Tool> tools = List.of(createWeatherTool(), createForecastTool());

        // When
        Skill skill = Skill.fromTools("weather-bundle", "天气工具集", tools);

        // Then
        assertEquals("weather-bundle", skill.getName());
        assertEquals(2, skill.getTools().size());
    }

    // ==================== 辅助方法 ====================

    private Tool createWeatherTool() {
        return new Tool() {
            @Override
            public String getName() { return "get_weather"; }

            @Override
            public String getDescription() { return "获取指定城市天气"; }

            @Override
            public ToolSchema getSchema() {
                return ToolSchema.withRequired(
                    Map.of("city", Map.of("type", "string", "description", "城市名称")),
                    "city"
                );
            }

            @Override
            public ToolResult execute(Map<String, Object> args) {
                String city = (String) args.get("city");
                return ToolResult.success(city + ": 晴天, 25°C");
            }
        };
    }

    private Tool createForecastTool() {
        return new Tool() {
            @Override
            public String getName() { return "get_forecast"; }

            @Override
            public String getDescription() { return "获取未来天气预报"; }

            @Override
            public ToolSchema getSchema() {
                return ToolSchema.withProperties(
                    Map.of(
                        "city", Map.of("type", "string"),
                        "days", Map.of("type", "integer", "default", 7)
                    )
                );
            }

            @Override
            public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("未来天气预报");
            }
        };
    }
}
