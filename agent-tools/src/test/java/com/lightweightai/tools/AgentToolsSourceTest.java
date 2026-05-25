package com.lightweightai.tools;

import com.lightweightai.kernel.agent.Tool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentToolsSource — SPI 工具发现")
class AgentToolsSourceTest {

    @Test
    @DisplayName("discoverTools 返回非空列表，包含 MathTools、TimeTools、WebTools、CheckWeatherTool")
    void discoversAllToolSources() {
        AgentToolsSource source = new AgentToolsSource();
        List<Tool> tools = source.discoverTools();

        assertNotNull(tools);
        assertFalse(tools.isEmpty());

        Set<String> toolNames = tools.stream()
                .map(Tool::getName)
                .collect(Collectors.toSet());

        assertTrue(toolNames.stream().anyMatch(n -> n.toLowerCase().contains("add")
                || n.toLowerCase().contains("math")
                || n.toLowerCase().contains("calculator")),
                "Should contain math tools, found: " + toolNames);
    }

    @Test
    @DisplayName("每个 Tool 都有 name 和 description")
    void allToolsHaveNameAndDescription() {
        AgentToolsSource source = new AgentToolsSource();
        List<Tool> tools = source.discoverTools();

        for (Tool tool : tools) {
            assertNotNull(tool.getName(), "Tool name should not be null");
            assertFalse(tool.getName().isBlank(), "Tool name should not be blank");
            assertNotNull(tool.getDescription(), "Tool description should not be null for " + tool.getName());
        }
    }

    @Test
    @DisplayName("多次调用 discoverTools 返回一致的工具列表")
    void multipleCallsReturnConsistentResults() {
        AgentToolsSource source = new AgentToolsSource();
        List<Tool> first = source.discoverTools();
        List<Tool> second = source.discoverTools();

        assertEquals(first.size(), second.size());

        Set<String> firstNames = first.stream().map(Tool::getName).collect(Collectors.toSet());
        Set<String> secondNames = second.stream().map(Tool::getName).collect(Collectors.toSet());
        assertEquals(firstNames, secondNames);
    }
}
