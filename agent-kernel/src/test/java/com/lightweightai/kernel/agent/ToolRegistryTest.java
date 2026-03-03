package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
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
 * TDD: ToolRegistry 测试
 *
 * ToolRegistry 负责：
 * 1. 统一管理所有工具
 * 2. 按分类/标签查询工具
 * 3. 支持工具启用/禁用
 * 4. 提供工具元信息
 */
@DisplayName("ToolRegistry - 统一工具注册表")
class ToolRegistryTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    // ==================== 基础注册测试 ====================

    @Test
    @DisplayName("注册和获取工具")
    void shouldRegisterAndGetTool() {
        // Given
        Tool tool = createSimpleTool("get_weather", "获取天气");

        // When
        registry.register(tool);

        // Then
        assertTrue(registry.has("get_weather"));
        assertEquals(tool, registry.get("get_weather").orElse(null));
    }

    @Test
    @DisplayName("注册多个工具")
    void shouldRegisterMultipleTools() {
        // Given
        Tool weather = createSimpleTool("get_weather", "获取天气");
        Tool time = createSimpleTool("get_time", "获取时间");
        Tool calculator = createSimpleTool("calculate", "计算");

        // When
        registry.register(weather);
        registry.register(time);
        registry.register(calculator);

        // Then
        assertEquals(3, registry.getAll().size());
    }

    @Test
    @DisplayName("获取不存在的工具返回空")
    void shouldReturnEmptyForUnknownTool() {
        // When & Then
        assertTrue(registry.get("nonexistent").isEmpty());
    }

    // ==================== 分类/标签测试 ====================

    @Test
    @DisplayName("按分类查询工具")
    void shouldGetToolsByCategory() {
        // Given
        Tool weather = createToolWithCategory("get_weather", "weather");
        Tool forecast = createToolWithCategory("get_forecast", "weather");
        Tool calculator = createToolWithCategory("calculate", "math");

        registry.register(weather);
        registry.register(forecast);
        registry.register(calculator);

        // When
        List<Tool> weatherTools = registry.getByCategory("weather");

        // Then
        assertEquals(2, weatherTools.size());
        assertTrue(weatherTools.stream().anyMatch(t -> t.getName().equals("get_weather")));
        assertTrue(weatherTools.stream().anyMatch(t -> t.getName().equals("get_forecast")));
    }

    @Test
    @DisplayName("按标签查询工具")
    void shouldGetToolsByTag() {
        // Given
        Tool tool1 = createToolWithTags("tool1", "api", "external");
        Tool tool2 = createToolWithTags("tool2", "api", "internal");
        Tool tool3 = createToolWithTags("tool3", "local");

        registry.register(tool1);
        registry.register(tool2);
        registry.register(tool3);

        // When
        List<Tool> apiTools = registry.getByTag("api");

        // Then
        assertEquals(2, apiTools.size());
    }

    // ==================== 启用/禁用测试 ====================

    @Test
    @DisplayName("禁用工具后不可用")
    void shouldDisableTool() {
        // Given
        Tool tool = createSimpleTool("dangerous_tool", "危险操作");
        registry.register(tool);

        // When
        registry.disable("dangerous_tool");

        // Then
        assertTrue(registry.has("dangerous_tool"));
        assertFalse(registry.isEnabled("dangerous_tool"));
        assertTrue(registry.getEnabled().isEmpty());
    }

    @Test
    @DisplayName("重新启用工具")
    void shouldEnableTool() {
        // Given
        Tool tool = createSimpleTool("tool1", "工具1");
        registry.register(tool);
        registry.disable("tool1");

        // When
        registry.enable("tool1");

        // Then
        assertTrue(registry.isEnabled("tool1"));
        assertEquals(1, registry.getEnabled().size());
    }

    // ==================== 批量操作测试 ====================

    @Test
    @DisplayName("批量注册工具")
    void shouldRegisterAll() {
        // Given
        List<Tool> tools = Arrays.asList(
            createSimpleTool("tool1", "工具1"),
            createSimpleTool("tool2", "工具2"),
            createSimpleTool("tool3", "工具3")
        );

        // When
        registry.registerAll(tools);

        // Then
        assertEquals(3, registry.getAll().size());
    }

    @Test
    @DisplayName("注销工具")
    void shouldUnregisterTool() {
        // Given
        Tool tool = createSimpleTool("tool1", "工具1");
        registry.register(tool);

        // When
        registry.unregister("tool1");

        // Then
        assertFalse(registry.has("tool1"));
    }

    // ==================== 工具元信息测试 ====================

    @Test
    @DisplayName("获取工具定义列表（供 LLM 使用）")
    void shouldGetToolDefinitions() {
        // Given
        Tool tool = createToolWithSchema("get_weather", "获取天气",
            new HashMap<String, Object>() {{ put("city", new HashMap<String, Object>() {{ put("type", "string"); put("description", "城市"); }}); }});
        registry.register(tool);

        // When
        List<Map<String, Object>> definitions = registry.getToolDefinitions();

        // Then
        assertEquals(1, definitions.size());
        Map<String, Object> def = definitions.get(0);
        assertEquals("get_weather", def.get("name"));
        assertEquals("获取天气", def.get("description"));
        assertNotNull(def.get("input_schema"));
    }

    // ==================== 辅助方法 ====================

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
                return ToolResult.success("result");
            }
        };
    }

    private Tool createToolWithCategory(String name, String category) {
        return new CategorizedTool(name, "Description", category, Collections.<String>emptyList());
    }

    private Tool createToolWithTags(String name, String... tags) {
        return new CategorizedTool(name, "Description", "default", Arrays.asList(tags));
    }

    private Tool createToolWithSchema(String name, String description, Map<String, Object> properties) {
        return new Tool() {
            @Override
            public String getName() { return name; }

            @Override
            public String getDescription() { return description; }

            @Override
            public ToolSchema getSchema() {
                return ToolSchema.withProperties(properties);
            }

            @Override
            public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("result");
            }
        };
    }

    /**
     * 带分类和标签的工具实现
     */
    private static class CategorizedTool implements Tool, ToolMetadata {
        private final String name;
        private final String description;
        private final String category;
        private final List<String> tags;

        CategorizedTool(String name, String description, String category, List<String> tags) {
            this.name = name;
            this.description = description;
            this.category = category;
            this.tags = tags;
        }

        @Override
        public String getName() { return name; }

        @Override
        public String getDescription() { return description; }

        @Override
        public ToolSchema getSchema() { return ToolSchema.empty(); }

        @Override
        public ToolResult execute(Map<String, Object> args) {
            return ToolResult.success("result");
        }

        @Override
        public String getCategory() { return category; }

        @Override
        public List<String> getTags() { return tags; }
    }
}
