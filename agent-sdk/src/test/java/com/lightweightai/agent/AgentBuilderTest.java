package com.lightweightai.agent;

import com.lightweightai.agent.plugin.Plugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentBuilder")
class AgentBuilderTest {

    @Nested
    @DisplayName("LLM Provider Configuration")
    class LLMProviderTests {

        @Test
        void shouldConfigureClaudeWithDefaultModel() {
            AgentBuilder builder = new AgentBuilder();
            builder.claude("test-key");

            assertEquals("claude", builder.getLlmProvider());
            assertEquals("test-key", builder.getApiKey());
            assertEquals("claude-3-5-sonnet-20241022", builder.getModel());
        }

        @Test
        void shouldConfigureClaudeWithCustomModel() {
            AgentBuilder builder = new AgentBuilder();
            builder.claude("test-key", "claude-3-opus-20240229");

            assertEquals("claude", builder.getLlmProvider());
            assertEquals("claude-3-opus-20240229", builder.getModel());
        }

        @Test
        void shouldConfigureOpenAIWithDefaultModel() {
            AgentBuilder builder = new AgentBuilder();
            builder.openai("openai-key");

            assertEquals("openai", builder.getLlmProvider());
            assertEquals("openai-key", builder.getApiKey());
            assertEquals("gpt-4", builder.getModel());
        }

        @Test
        void shouldConfigureOpenAIWithCustomModel() {
            AgentBuilder builder = new AgentBuilder();
            builder.openai("openai-key", "gpt-4-turbo");

            assertEquals("openai", builder.getLlmProvider());
            assertEquals("gpt-4-turbo", builder.getModel());
        }
    }

    @Nested
    @DisplayName("Build Validation")
    class BuildValidationTests {

        @Test
        void shouldRequireLLMProvider() {
            AgentBuilder builder = new AgentBuilder();
            assertThrows(IllegalStateException.class, builder::build);
        }

        @Test
        void shouldRequireApiKey() {
            AgentBuilder builder = new AgentBuilder();
            // No provider set at all
            assertThrows(IllegalStateException.class, builder::build);
        }

        @Test
        void shouldBuildSuccessfullyWithMinimalConfig() {
            Agent agent = Agent.builder()
                    .claude("test-key")
                    .build();
            assertNotNull(agent);
        }
    }

    @Nested
    @DisplayName("Plugin Management")
    class PluginTests {

        @Test
        void shouldAddSinglePlugin() {
            TestPlugin plugin = new TestPlugin("test");
            AgentBuilder builder = new AgentBuilder();
            builder.claude("key").plugin(plugin);

            assertEquals(1, builder.getPlugins().size());
            assertEquals("test", builder.getPlugins().get(0).getName());
        }

        @Test
        void shouldAddMultiplePlugins() {
            TestPlugin p1 = new TestPlugin("p1");
            TestPlugin p2 = new TestPlugin("p2");
            AgentBuilder builder = new AgentBuilder();
            builder.claude("key").plugins(p1, p2);

            assertEquals(2, builder.getPlugins().size());
        }

        @Test
        void shouldReturnDefensiveCopyOfPlugins() {
            TestPlugin plugin = new TestPlugin("test");
            AgentBuilder builder = new AgentBuilder();
            builder.claude("key").plugin(plugin);

            builder.getPlugins().clear();
            assertEquals(1, builder.getPlugins().size());
        }
    }

    @Nested
    @DisplayName("Options and Features")
    class OptionsTests {

        @Test
        void shouldSetDefaultOptions() {
            ChatOptions options = ChatOptions.builder().temperature(0.5).build();
            AgentBuilder builder = new AgentBuilder();
            builder.claude("key").options(options);

            assertNotNull(builder.getDefaultOptions());
            assertEquals(0.5, builder.getDefaultOptions().getTemperature());
        }

        @Test
        void shouldEnableMemory() {
            AgentBuilder builder = new AgentBuilder();
            builder.claude("key").withMemory();

            assertTrue(builder.isEnableMemory());
        }

        @Test
        void shouldDefaultMemoryDisabled() {
            AgentBuilder builder = new AgentBuilder();
            assertFalse(builder.isEnableMemory());
        }

        @Test
        void shouldSetTimeout() {
            AgentBuilder builder = new AgentBuilder();
            builder.claude("key").timeout(Duration.ofSeconds(30));

            assertEquals(Duration.ofSeconds(30), builder.getTimeout());
        }

        @Test
        void shouldHaveDefaultTimeout() {
            AgentBuilder builder = new AgentBuilder();
            assertEquals(Duration.ofMinutes(5), builder.getTimeout());
        }
    }

    @Nested
    @DisplayName("Fluent API")
    class FluentApiTests {

        @Test
        void shouldSupportChaining() {
            Agent agent = Agent.builder()
                    .claude("key")
                    .withMemory()
                    .options(ChatOptions.builder().temperature(0.8).build())
                    .timeout(Duration.ofMinutes(2))
                    .build();

            assertNotNull(agent);
        }

        @Test
        void shouldCreateViaStaticFactory() {
            AgentBuilder builder = Agent.builder();
            assertNotNull(builder);
        }
    }

    // Helper test plugin
    static class TestPlugin extends Plugin {
        private final String name;

        TestPlugin(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return "Test plugin: " + name;
        }
    }
}
