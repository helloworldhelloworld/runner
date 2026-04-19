package com.lightweightai.agent;

import com.lightweightai.agent.plugin.Plugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentBuilder - Agent 构建器")
class AgentBuilderTest {

    @Nested
    @DisplayName("Provider 配置")
    class ProviderConfigTests {

        @Test
        @DisplayName("Claude 默认模型")
        void claudeDefaultModel() {
            AgentBuilder builder = new AgentBuilder();
            builder.claude("sk-test");

            assertEquals("claude", builder.getLlmProvider());
            assertEquals("sk-test", builder.getApiKey());
            assertEquals("claude-3-5-sonnet-20241022", builder.getModel());
        }

        @Test
        @DisplayName("Claude 自定义模型")
        void claudeCustomModel() {
            AgentBuilder builder = new AgentBuilder();
            builder.claude("sk-test", "claude-3-opus-20240229");

            assertEquals("claude", builder.getLlmProvider());
            assertEquals("claude-3-opus-20240229", builder.getModel());
        }

        @Test
        @DisplayName("OpenAI 默认模型")
        void openaiDefaultModel() {
            AgentBuilder builder = new AgentBuilder();
            builder.openai("sk-test");

            assertEquals("openai", builder.getLlmProvider());
            assertEquals("gpt-4", builder.getModel());
        }

        @Test
        @DisplayName("OpenAI 自定义模型")
        void openaiCustomModel() {
            AgentBuilder builder = new AgentBuilder();
            builder.openai("sk-test", "gpt-4-turbo");

            assertEquals("openai", builder.getLlmProvider());
            assertEquals("gpt-4-turbo", builder.getModel());
        }
    }

    @Nested
    @DisplayName("Build 验证")
    class BuildValidationTests {

        @Test
        @DisplayName("缺少 provider 和 apiKey 时 build 抛异常")
        void shouldThrowWhenNoProvider() {
            AgentBuilder builder = new AgentBuilder();
            assertThrows(IllegalStateException.class, builder::build);
        }

        @Test
        @DisplayName("有 provider 和 apiKey 时 build 成功")
        void shouldBuildSuccessfully() {
            Agent agent = Agent.builder()
                    .claude("test-key")
                    .build();
            assertNotNull(agent);
        }
    }

    @Nested
    @DisplayName("Plugin 注册")
    class PluginRegistrationTests {

        @Test
        @DisplayName("注册单个插件")
        void shouldRegisterSinglePlugin() {
            AgentBuilder builder = new AgentBuilder();
            builder.claude("test-key");

            Plugin plugin = new StubPlugin("test-plugin");
            builder.plugin(plugin);

            assertEquals(1, builder.getPlugins().size());
            assertEquals("test-plugin", builder.getPlugins().get(0).getName());
        }

        @Test
        @DisplayName("注册多个插件（varargs）")
        void shouldRegisterMultiplePlugins() {
            AgentBuilder builder = new AgentBuilder();
            builder.claude("test-key");

            builder.plugins(new StubPlugin("a"), new StubPlugin("b"), new StubPlugin("c"));
            assertEquals(3, builder.getPlugins().size());
        }

        @Test
        @DisplayName("getPlugins 返回副本（防御性拷贝）")
        void pluginListShouldBeDefensiveCopy() {
            AgentBuilder builder = new AgentBuilder();
            builder.claude("test-key");
            builder.plugin(new StubPlugin("p"));

            var list = builder.getPlugins();
            list.clear();

            assertEquals(1, builder.getPlugins().size());
        }
    }

    @Nested
    @DisplayName("选项配置")
    class OptionsTests {

        @Test
        @DisplayName("设置默认 ChatOptions")
        void shouldSetDefaultOptions() {
            ChatOptions opts = ChatOptions.builder().temperature(0.3).build();
            AgentBuilder builder = new AgentBuilder();
            builder.claude("test-key").options(opts);

            assertSame(opts, builder.getDefaultOptions());
        }

        @Test
        @DisplayName("启用记忆")
        void shouldEnableMemory() {
            AgentBuilder builder = new AgentBuilder();
            builder.claude("test-key").withMemory();

            assertTrue(builder.isEnableMemory());
        }

        @Test
        @DisplayName("默认不启用记忆")
        void memoryShouldBeDisabledByDefault() {
            AgentBuilder builder = new AgentBuilder();
            assertFalse(builder.isEnableMemory());
        }

        @Test
        @DisplayName("设置超时时间")
        void shouldSetTimeout() {
            AgentBuilder builder = new AgentBuilder();
            builder.claude("test-key").timeout(Duration.ofMinutes(10));

            assertEquals(Duration.ofMinutes(10), builder.getTimeout());
        }
    }

    @Nested
    @DisplayName("Fluent API 链式调用")
    class FluentApiTests {

        @Test
        @DisplayName("链式调用返回同一 builder")
        void shouldSupportChaining() {
            AgentBuilder builder = new AgentBuilder();
            AgentBuilder result = builder
                    .claude("test-key")
                    .withMemory()
                    .plugin(new StubPlugin("p"))
                    .options(ChatOptions.builder().build())
                    .timeout(Duration.ofMinutes(3));

            assertSame(builder, result);
        }
    }

    static class StubPlugin extends Plugin {
        private final String name;

        StubPlugin(String name) {
            this.name = name;
        }

        @Override
        public String getName() { return name; }

        @Override
        public String getDescription() { return "Stub plugin: " + name; }
    }
}
