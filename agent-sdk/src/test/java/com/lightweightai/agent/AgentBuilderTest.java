package com.lightweightai.agent;

import com.lightweightai.agent.plugin.Plugin;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentBuilder - Agent 构建器")
class AgentBuilderTest {

    @Nested
    @DisplayName("必要参数校验")
    class ValidationTests {

        @Test
        @DisplayName("无 provider 和 apiKey 时 build 抛异常")
        void shouldFailWithoutProviderAndKey() {
            assertThrows(IllegalStateException.class, () -> Agent.builder().build());
        }

        @Test
        @DisplayName("有 provider 无 apiKey 时 build 抛异常")
        void shouldFailWithoutApiKey() {
            assertThrows(IllegalStateException.class, () -> {
                AgentBuilder builder = new AgentBuilder();
                builder.build();
            });
        }
    }

    @Nested
    @DisplayName("Claude provider")
    class ClaudeTests {

        @Test
        @DisplayName("claude(apiKey) 使用默认模型")
        void shouldSetDefaultClaudeModel() {
            Agent agent = Agent.builder().claude("key-123").build();
            assertNotNull(agent);
        }

        @Test
        @DisplayName("claude(apiKey, model) 使用指定模型")
        void shouldSetCustomClaudeModel() {
            Agent agent = Agent.builder().claude("key-123", "claude-3-opus-20240229").build();
            assertNotNull(agent);
        }
    }

    @Nested
    @DisplayName("OpenAI provider")
    class OpenAITests {

        @Test
        @DisplayName("openai(apiKey) 使用默认模型")
        void shouldSetDefaultOpenAIModel() {
            Agent agent = Agent.builder().openai("key-456").build();
            assertNotNull(agent);
        }

        @Test
        @DisplayName("openai(apiKey, model) 使用指定模型")
        void shouldSetCustomOpenAIModel() {
            Agent agent = Agent.builder().openai("key-456", "gpt-4-turbo").build();
            assertNotNull(agent);
        }
    }

    @Nested
    @DisplayName("插件注册")
    class PluginTests {

        @Test
        @DisplayName("添加单个插件")
        void shouldAddSinglePlugin() {
            Plugin mockPlugin = createMockPlugin("test-plugin");
            Agent agent = Agent.builder()
                    .claude("key")
                    .plugin(mockPlugin)
                    .build();

            assertEquals(1, agent.getPlugins().size());
            assertEquals("test-plugin", agent.getPlugins().get(0).getName());
        }

        @Test
        @DisplayName("添加多个插件")
        void shouldAddMultiplePlugins() {
            Agent agent = Agent.builder()
                    .claude("key")
                    .plugins(createMockPlugin("p1"), createMockPlugin("p2"))
                    .build();

            assertEquals(2, agent.getPlugins().size());
        }

        @Test
        @DisplayName("运行时动态添加插件")
        void shouldAddPluginDynamically() {
            Agent agent = Agent.builder().claude("key").build();
            agent.addPlugin(createMockPlugin("dynamic"));

            assertEquals(1, agent.getPlugins().size());
        }
    }

    @Nested
    @DisplayName("记忆管理")
    class MemoryTests {

        @Test
        @DisplayName("默认不启用记忆")
        void shouldDefaultToNoMemory() {
            Agent agent = Agent.builder().claude("key").build();
            assertNull(agent.getMemory());
        }

        @Test
        @DisplayName("withMemory 启用记忆")
        void shouldEnableMemory() {
            Agent agent = Agent.builder().claude("key").withMemory().build();
            assertNotNull(agent.getMemory());
        }

        @Test
        @DisplayName("clearMemory 不会报错（未启用时）")
        void shouldSafelyClearMemoryWhenDisabled() {
            Agent agent = Agent.builder().claude("key").build();
            assertDoesNotThrow(agent::clearMemory);
        }

        @Test
        @DisplayName("clearMemory 清空记忆（启用时）")
        void shouldClearMemoryWhenEnabled() {
            Agent agent = Agent.builder().claude("key").withMemory().build();
            assertNotNull(agent.getMemory());
            assertDoesNotThrow(agent::clearMemory);
        }
    }

    @Nested
    @DisplayName("Tool 注册")
    class ToolTests {

        @Test
        @DisplayName("通过 tool() 注册单个工具")
        void shouldRegisterSingleTool() {
            Agent agent = Agent.builder()
                    .claude("key")
                    .tool(createMockTool("calc"))
                    .build();
            assertNotNull(agent);
        }

        @Test
        @DisplayName("通过 tools(ToolRegistry) 注册工具集")
        void shouldRegisterToolRegistry() {
            var registry = new com.lightweightai.kernel.agent.ToolRegistry();
            registry.register(createMockTool("tool1"));

            Agent agent = Agent.builder()
                    .claude("key")
                    .tools(registry)
                    .build();
            assertNotNull(agent);
        }
    }

    @Nested
    @DisplayName("选项配置")
    class OptionsTests {

        @Test
        @DisplayName("设置默认 ChatOptions")
        void shouldSetDefaultOptions() {
            ChatOptions opts = ChatOptions.builder()
                    .temperature(0.5)
                    .maxTokens(1000)
                    .build();

            Agent agent = Agent.builder()
                    .claude("key")
                    .options(opts)
                    .build();
            assertNotNull(agent);
        }

        @Test
        @DisplayName("设置超时时间")
        void shouldSetTimeout() {
            Agent agent = Agent.builder()
                    .claude("key")
                    .timeout(Duration.ofMinutes(10))
                    .build();
            assertNotNull(agent);
        }
    }

    @Nested
    @DisplayName("链式调用")
    class FluentTests {

        @Test
        @DisplayName("所有 builder 方法支持链式调用")
        void shouldSupportFluentApi() {
            Agent agent = Agent.builder()
                    .claude("key", "claude-3-5-sonnet-20241022")
                    .plugin(createMockPlugin("p1"))
                    .withMemory()
                    .options(ChatOptions.builder().temperature(0.7).build())
                    .timeout(Duration.ofMinutes(3))
                    .build();

            assertNotNull(agent);
            assertEquals(1, agent.getPlugins().size());
            assertNotNull(agent.getMemory());
        }
    }

    // ==================== 辅助方法 ====================

    private Plugin createMockPlugin(String name) {
        return new Plugin() {
            @Override
            public String getName() { return name; }

            @Override
            public String getDescription() { return "Mock plugin: " + name; }

            @Override
            public Map<String, com.lightweightai.kernel.plugin.PluginFunction> getFunctions() {
                return Map.of();
            }

            @Override
            public List<Map<String, Object>> toClaudeTools() { return List.of(); }
        };
    }

    private Tool createMockTool(String name) {
        return new Tool() {
            @Override
            public String getName() { return name; }

            @Override
            public String getDescription() { return "Mock tool: " + name; }

            @Override
            public ToolSchema getSchema() { return null; }

            @Override
            public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("id", "result");
            }
        };
    }
}
