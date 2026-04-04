package com.lightweightai.tools;

import com.lightweightai.kernel.agent.Tool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentToolsSource")
class AgentToolsSourceTest {

    @Test
    void shouldDiscoverAllTools() {
        AgentToolsSource source = new AgentToolsSource();
        List<Tool> tools = source.discoverTools();

        assertNotNull(tools);
        assertFalse(tools.isEmpty());
    }

    @Test
    void shouldDiscoverMathTools() {
        AgentToolsSource source = new AgentToolsSource();
        List<Tool> tools = source.discoverTools();

        Set<String> names = tools.stream()
                .map(Tool::getName)
                .collect(Collectors.toSet());

        assertTrue(names.contains("add") || names.contains("math_add") || names.stream().anyMatch(n -> n.contains("add")),
                "Should discover math tools, found: " + names);
    }

    @Test
    void shouldDiscoverWebSearchTool() {
        AgentToolsSource source = new AgentToolsSource();
        List<Tool> tools = source.discoverTools();

        Set<String> names = tools.stream()
                .map(Tool::getName)
                .collect(Collectors.toSet());

        assertTrue(names.contains("web_search"),
                "Should discover web_search tool, found: " + names);
    }

    @Test
    void shouldHaveUniqueToolNames() {
        AgentToolsSource source = new AgentToolsSource();
        List<Tool> tools = source.discoverTools();

        Set<String> names = tools.stream()
                .map(Tool::getName)
                .collect(Collectors.toSet());

        assertEquals(tools.size(), names.size(), "All tool names should be unique");
    }
}
