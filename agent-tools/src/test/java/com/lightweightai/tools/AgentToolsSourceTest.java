package com.lightweightai.tools;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentToolsSource 工具来源")
class AgentToolsSourceTest {

    private AgentToolsSource source;
    private List<Tool> tools;

    @BeforeEach
    void setUp() {
        source = new AgentToolsSource();
        tools = source.discoverTools();
    }

    @Test
    @DisplayName("discoverTools 应返回非空工具列表")
    void shouldReturnNonEmptyToolList() {
        assertNotNull(tools);
        assertFalse(tools.isEmpty(), "discoverTools() should return at least one tool");
    }

    @Test
    @DisplayName("应发现数学工具 (add, multiply, divide)")
    void shouldDiscoverMathTools() {
        Set<String> names = tools.stream().map(Tool::getName).collect(Collectors.toSet());

        assertTrue(names.contains("add"), "Should discover 'add' tool from MathTools");
        assertTrue(names.contains("multiply"), "Should discover 'multiply' tool from MathTools");
        assertTrue(names.contains("divide"), "Should discover 'divide' tool from MathTools");
    }

    @Test
    @DisplayName("应发现时间工具 (get_time)")
    void shouldDiscoverTimeTools() {
        Set<String> names = tools.stream().map(Tool::getName).collect(Collectors.toSet());

        assertTrue(names.contains("get_time"), "Should discover 'get_time' tool from TimeTools");
    }

    @Test
    @DisplayName("应发现天气工具 (checkWeather)")
    void shouldDiscoverWeatherTool() {
        Set<String> names = tools.stream().map(Tool::getName).collect(Collectors.toSet());

        assertTrue(names.contains("checkWeather"), "Should discover 'checkWeather' tool from CheckWeatherTool");
    }

    @Test
    @DisplayName("应发现网络搜索工具 (web_search)")
    void shouldDiscoverWebSearchTool() {
        Set<String> names = tools.stream().map(Tool::getName).collect(Collectors.toSet());

        assertTrue(names.contains("web_search"), "Should discover 'web_search' tool from WebTools");
    }

    @Test
    @DisplayName("每个工具应有非空的名称和描述")
    void eachToolShouldHaveNonNullNameAndDescription() {
        for (Tool tool : tools) {
            assertNotNull(tool.getName(), "Tool name should not be null");
            assertFalse(tool.getName().isBlank(), "Tool name should not be blank");

            assertNotNull(tool.getDescription(), "Tool description should not be null for: " + tool.getName());
            assertFalse(tool.getDescription().isBlank(),
                "Tool description should not be blank for: " + tool.getName());
        }
    }

    @Test
    @DisplayName("每个工具应有有效的 Schema")
    void eachToolShouldHaveValidSchema() {
        for (Tool tool : tools) {
            ToolSchema schema = tool.getSchema();
            assertNotNull(schema, "Schema should not be null for tool: " + tool.getName());
            assertNotNull(schema.toMap(), "Schema map should not be null for tool: " + tool.getName());
            assertEquals("object", schema.getType(),
                "Schema type should be 'object' for tool: " + tool.getName());
        }
    }

    @Test
    @DisplayName("传输链测试：发现工具 -> 查找 add -> 执行并验证结果载荷")
    void transmissionTest_discoverFindExecuteAdd() {
        Tool addTool = tools.stream()
            .filter(t -> "add".equals(t.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("add tool not found in discovered tools"));

        ToolResult result = addTool.execute(Map.of("a", 10, "b", 25));

        assertFalse(result.isError(), "add(10, 25) should succeed: " + result.getContent());
        assertEquals("35", result.getContent(),
            "add(10, 25) should return '35' but got: " + result.getContent());
    }

    @Test
    @DisplayName("传输链测试：发现工具 -> 查找 checkWeather -> 执行并验证结果载荷")
    void transmissionTest_discoverFindExecuteCheckWeather() {
        Tool weatherTool = tools.stream()
            .filter(t -> "checkWeather".equals(t.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("checkWeather tool not found in discovered tools"));

        ToolResult result = weatherTool.execute(Map.of("city", "上海"));

        assertFalse(result.isError(), "checkWeather(上海) should succeed: " + result.getContent());
        assertTrue(result.getContent().contains("上海"),
            "Result should contain city name '上海' but got: " + result.getContent());
        assertTrue(result.getContent().contains("多云"),
            "Result should contain weather condition '多云' but got: " + result.getContent());
    }

    @Test
    @DisplayName("传输链测试：发现工具 -> 查找 get_time -> 执行并验证结果载荷")
    void transmissionTest_discoverFindExecuteGetTime() {
        Tool timeTool = tools.stream()
            .filter(t -> "get_time".equals(t.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("get_time tool not found in discovered tools"));

        ToolResult result = timeTool.execute(Map.of());

        assertFalse(result.isError(), "get_time() should succeed: " + result.getContent());
        assertNotNull(result.getContent(), "get_time() result content should not be null");
        assertFalse(result.getContent().isEmpty(), "get_time() result content should not be empty");
        // ISO date-time format contains 'T' separator (e.g. 2026-05-16T14:30:00)
        assertTrue(result.getContent().contains("T"),
            "get_time() should return ISO date-time format but got: " + result.getContent());
    }
}
