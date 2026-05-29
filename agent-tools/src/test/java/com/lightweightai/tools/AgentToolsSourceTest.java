package com.lightweightai.tools;

import com.lightweightai.kernel.agent.Tool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentToolsSource - SPI entry point for agent-tools module")
class AgentToolsSourceTest {

    @Test
    @DisplayName("discoverTools returns all expected tools")
    void shouldDiscoverAllTools() {
        AgentToolsSource source = new AgentToolsSource();
        List<Tool> tools = source.discoverTools();

        assertFalse(tools.isEmpty(), "Should discover at least one tool");

        Set<String> toolNames = tools.stream()
            .map(Tool::getName)
            .collect(Collectors.toSet());

        assertTrue(toolNames.contains("web_search"), "Should discover web_search");
        assertTrue(toolNames.contains("check_weather"), "Should discover check_weather");
    }

    @Test
    @DisplayName("All discovered tools have non-null schema")
    void allToolsShouldHaveSchema() {
        AgentToolsSource source = new AgentToolsSource();
        List<Tool> tools = source.discoverTools();

        for (Tool tool : tools) {
            assertNotNull(tool.getSchema(),
                "Tool '" + tool.getName() + "' should have non-null schema");
            assertNotNull(tool.getDescription(),
                "Tool '" + tool.getName() + "' should have non-null description");
        }
    }

    @Test
    @DisplayName("All discovered tools have unique names")
    void allToolsShouldHaveUniqueNames() {
        AgentToolsSource source = new AgentToolsSource();
        List<Tool> tools = source.discoverTools();

        Set<String> names = tools.stream().map(Tool::getName).collect(Collectors.toSet());
        assertEquals(tools.size(), names.size(), "All tool names should be unique");
    }
}
