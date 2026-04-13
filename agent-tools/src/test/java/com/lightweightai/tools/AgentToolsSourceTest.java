package com.lightweightai.tools;

import com.lightweightai.kernel.agent.Tool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentToolsSource - SPI tool source for agent-tools module")
class AgentToolsSourceTest {

    @Test
    @DisplayName("discoverTools returns tools from all annotated tool classes")
    void discoverToolsReturnsAllTools() {
        AgentToolsSource source = new AgentToolsSource();
        List<Tool> tools = source.discoverTools();

        assertNotNull(tools);
        assertFalse(tools.isEmpty());

        Set<String> toolNames = tools.stream()
            .map(Tool::getName)
            .collect(Collectors.toSet());

        // MathTools: add, subtract, multiply, divide
        assertTrue(toolNames.contains("add"));
        assertTrue(toolNames.contains("subtract"));

        // TimeTools: get_current_time, parse_date
        assertTrue(toolNames.contains("get_current_time"));

        // WebTools: web_search
        assertTrue(toolNames.contains("web_search"));

        // CheckWeatherTool: check_weather
        assertTrue(toolNames.contains("check_weather"));
    }

    @Test
    @DisplayName("all discovered tools have names and descriptions")
    void allToolsHaveMetadata() {
        AgentToolsSource source = new AgentToolsSource();
        for (Tool tool : source.discoverTools()) {
            assertNotNull(tool.getName(), "Tool name should not be null");
            assertFalse(tool.getName().isBlank(), "Tool name should not be blank");
            assertNotNull(tool.getDescription(), "Tool description should not be null for: " + tool.getName());
        }
    }

    @Test
    @DisplayName("discovered tools are executable")
    void toolsAreExecutable() {
        AgentToolsSource source = new AgentToolsSource();
        List<Tool> tools = source.discoverTools();

        Tool addTool = tools.stream()
            .filter(t -> "add".equals(t.getName()))
            .findFirst()
            .orElseThrow();

        var result = addTool.execute(java.util.Map.of("a", 3.0, "b", 5.0));
        assertNotNull(result);
        assertFalse(result.isError());
    }
}
