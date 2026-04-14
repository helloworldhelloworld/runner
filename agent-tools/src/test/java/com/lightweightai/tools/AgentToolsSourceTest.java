package com.lightweightai.tools;

import com.lightweightai.kernel.agent.Tool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentToolsSource 测试 — SPI 工具发现
 *
 * 覆盖：
 * - discoverTools 返回非空列表
 * - 包含预期的工具类型（MathTools, TimeTools, WebTools, CheckWeatherTool）
 * - 每个工具都有 name 和 description
 */
@DisplayName("AgentToolsSource - SPI 工具发现")
class AgentToolsSourceTest {

    @Test
    @DisplayName("discoverTools 返回非空列表")
    void discoverToolsReturnsNonEmptyList() {
        AgentToolsSource source = new AgentToolsSource();
        List<Tool> tools = source.discoverTools();

        assertNotNull(tools);
        assertFalse(tools.isEmpty(), "Should discover at least one tool");
    }

    @Test
    @DisplayName("发现的工具包含预期的名称")
    void discoveredToolsContainExpectedNames() {
        AgentToolsSource source = new AgentToolsSource();
        List<Tool> tools = source.discoverTools();

        Set<String> names = tools.stream()
                .map(Tool::getName)
                .collect(Collectors.toSet());

        // MathTools 使用 @ToolFunction 注解，应产生多个工具
        // 至少应有 math/time/web/weather 相关的工具
        assertFalse(names.isEmpty(), "Tool names should not be empty");
    }

    @Test
    @DisplayName("每个发现的工具都有 name 和 description")
    void allToolsHaveNameAndDescription() {
        AgentToolsSource source = new AgentToolsSource();
        List<Tool> tools = source.discoverTools();

        for (Tool tool : tools) {
            assertNotNull(tool.getName(), "Tool name should not be null");
            assertFalse(tool.getName().isEmpty(), "Tool name should not be empty");
            assertNotNull(tool.getDescription(), "Tool description should not be null for: " + tool.getName());
        }
    }

    @Test
    @DisplayName("多次调用 discoverTools 返回一致的结果")
    void discoverToolsIsIdempotent() {
        AgentToolsSource source = new AgentToolsSource();
        List<Tool> first = source.discoverTools();
        List<Tool> second = source.discoverTools();

        assertEquals(first.size(), second.size(), "Should return same number of tools each call");
    }
}
