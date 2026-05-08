package com.lightweightai.tools;

import com.lightweightai.kernel.agent.Tool;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AgentToolsSource} — SPI entry point for tool discovery.
 *
 * Covers: tool discovery yields expected tools from all registered instances.
 */
@DisplayName("AgentToolsSource - 工具发现 SPI")
class AgentToolsSourceTest {

    @Test
    @DisplayName("discoverTools — 发现所有注册的工具")
    void discoverTools_findsAllTools() {
        AgentToolsSource source = new AgentToolsSource();
        List<Tool> tools = source.discoverTools();

        assertFalse(tools.isEmpty(), "Should discover at least some tools");

        Set<String> names = tools.stream()
            .map(Tool::getName)
            .collect(Collectors.toSet());

        // MathTools provides: add, multiply, divide
        assertTrue(names.contains("add"), "Should discover 'add' from MathTools");
        assertTrue(names.contains("multiply"), "Should discover 'multiply' from MathTools");

        // WebTools provides: web_search
        assertTrue(names.contains("web_search"), "Should discover 'web_search' from WebTools");

        // CheckWeatherTool provides: check_weather
        assertTrue(names.contains("check_weather"), "Should discover 'check_weather' from CheckWeatherTool");
    }

    @Test
    @DisplayName("discoverTools — 每个工具有名称和描述")
    void discoverTools_eachToolHasNameAndDescription() {
        AgentToolsSource source = new AgentToolsSource();
        List<Tool> tools = source.discoverTools();

        for (Tool tool : tools) {
            assertNotNull(tool.getName(), "Tool name should not be null");
            assertFalse(tool.getName().isBlank(), "Tool name should not be blank");
            assertNotNull(tool.getDescription(), "Tool description should not be null");
        }
    }
}
