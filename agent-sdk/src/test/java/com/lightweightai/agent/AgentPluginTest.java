package com.lightweightai.agent;

import com.lightweightai.agent.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD: Test for Agent with plugins
 *
 * User Story: 作为开发者，我希望轻松地给Agent添加插件功能
 */
class AgentPluginTest {

    @Test
    void shouldAddPluginToAgent() {
        // Given: 一个简单的插件
        Plugin mathPlugin = new TestMathPlugin();

        // When: 添加插件到Agent
        Agent agent = Agent.builder()
                .claude("test-api-key")
                .plugin(mathPlugin)
                .build();

        // Then: 插件应该被添加
        assertNotNull(agent);
        assertEquals(1, agent.getPlugins().size());
        assertEquals("math", agent.getPlugins().get(0).getName());
    }

    @Test
    void shouldAddMultiplePlugins() {
        // Given: 多个插件
        Plugin mathPlugin = new TestMathPlugin();
        Plugin timePlugin = new TestTimePlugin();

        // When: 添加多个插件
        Agent agent = Agent.builder()
                .claude("test-api-key")
                .plugin(mathPlugin)
                .plugin(timePlugin)
                .build();

        // Then: 所有插件都应该被添加
        assertEquals(2, agent.getPlugins().size());
    }

    @Test
    void shouldAddPluginsUsingVarargs() {
        // Given: 多个插件
        Plugin math = new TestMathPlugin();
        Plugin time = new TestTimePlugin();

        // When: 使用varargs添加
        Agent agent = Agent.builder()
                .claude("test-api-key")
                .plugins(math, time)
                .build();

        // Then: 所有插件都应该被添加
        assertEquals(2, agent.getPlugins().size());
    }

    // === 测试用的简单插件 ===

    static class TestMathPlugin extends Plugin {
        @Override
        public String getName() {
            return "math";
        }

        @Override
        public String getDescription() {
            return "Math operations";
        }
    }

    static class TestTimePlugin extends Plugin {
        @Override
        public String getName() {
            return "time";
        }

        @Override
        public String getDescription() {
            return "Time operations";
        }
    }
}
