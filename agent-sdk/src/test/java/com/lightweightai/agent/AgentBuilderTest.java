package com.lightweightai.agent;

import com.lightweightai.agent.plugin.Plugin;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentBuilder - fluent agent construction")
class AgentBuilderTest {

    @Nested
    @DisplayName("LLM Provider configuration")
    class ProviderConfig {

        @Test
        void claude_withDefaultModel() {
            Agent agent = Agent.builder()
                .claude("sk-test-key")
                .build();

            assertNotNull(agent);
        }

        @Test
        void claude_withCustomModel() {
            Agent agent = Agent.builder()
                .claude("sk-test-key", "claude-3-opus-20240229")
                .build();

            assertNotNull(agent);
        }

        @Test
        void openai_withDefaultModel() {
            Agent agent = Agent.builder()
                .openai("sk-test-key")
                .build();

            assertNotNull(agent);
        }

        @Test
        void openai_withCustomModel() {
            Agent agent = Agent.builder()
                .openai("sk-test-key", "gpt-4-turbo")
                .build();

            assertNotNull(agent);
        }

        @Test
        void build_withoutProvider_throws() {
            assertThrows(IllegalStateException.class,
                () -> Agent.builder().build());
        }
    }

    @Nested
    @DisplayName("Plugin management")
    class PluginConfig {

        @Test
        void singlePlugin() {
            Plugin mockPlugin = createMockPlugin("test-plugin");
            Agent agent = Agent.builder()
                .claude("sk-key")
                .plugin(mockPlugin)
                .build();

            assertEquals(1, agent.getPlugins().size());
        }

        @Test
        void multiplePlugins() {
            Plugin p1 = createMockPlugin("p1");
            Plugin p2 = createMockPlugin("p2");
            Agent agent = Agent.builder()
                .claude("sk-key")
                .plugins(p1, p2)
                .build();

            assertEquals(2, agent.getPlugins().size());
        }

        @Test
        void addPluginDynamically() {
            Agent agent = Agent.builder()
                .claude("sk-key")
                .build();

            Plugin p = createMockPlugin("dynamic");
            agent.addPlugin(p);

            assertEquals(1, agent.getPlugins().size());
        }
    }

    @Nested
    @DisplayName("Tool registration")
    class ToolConfig {

        @Test
        void toolRegistry_canBeSet() {
            ToolRegistry registry = new ToolRegistry();
            Agent agent = Agent.builder()
                .claude("sk-key")
                .tools(registry)
                .build();

            assertNotNull(agent);
        }

        @Test
        void singleTool_createsRegistryAutomatically() {
            Tool mockTool = createMockTool("calc");
            Agent agent = Agent.builder()
                .claude("sk-key")
                .tool(mockTool)
                .build();

            assertNotNull(agent);
        }
    }

    @Nested
    @DisplayName("Memory and options")
    class MemoryAndOptions {

        @Test
        void withMemory_enablesConversationMemory() {
            Agent agent = Agent.builder()
                .claude("sk-key")
                .withMemory()
                .build();

            assertNotNull(agent.getMemory());
        }

        @Test
        void withoutMemory_memoryIsNull() {
            Agent agent = Agent.builder()
                .claude("sk-key")
                .build();

            assertNull(agent.getMemory());
        }

        @Test
        void clearMemory_worksWhenEnabled() {
            Agent agent = Agent.builder()
                .claude("sk-key")
                .withMemory()
                .build();

            agent.clearMemory(); // should not throw
            assertEquals(0, agent.getMemory().getMessageCount());
        }

        @Test
        void clearMemory_worksWhenDisabled() {
            Agent agent = Agent.builder()
                .claude("sk-key")
                .build();

            agent.clearMemory(); // should not throw even without memory
        }

        @Test
        void timeout_canBeSet() {
            Agent agent = Agent.builder()
                .claude("sk-key")
                .timeout(Duration.ofSeconds(30))
                .build();

            assertNotNull(agent);
        }

        @Test
        void options_canBeSet() {
            ChatOptions opts = ChatOptions.builder()
                .temperature(0.5)
                .maxTokens(512)
                .build();

            Agent agent = Agent.builder()
                .claude("sk-key")
                .options(opts)
                .build();

            assertNotNull(agent);
        }
    }

    // === Test helpers ===

    private Plugin createMockPlugin(String name) {
        return new Plugin() {
            @Override
            public String getName() { return name; }
            @Override
            public String getDescription() { return "Mock plugin: " + name; }
            @Override
            public List<com.lightweightai.kernel.plugin.PluginFunction> getFunctions() {
                return Collections.emptyList();
            }
            @Override
            public List<Map<String, Object>> toClaudeTools() {
                return Collections.emptyList();
            }
        };
    }

    private Tool createMockTool(String name) {
        return new Tool() {
            @Override
            public String getName() { return name; }
            @Override
            public String getDescription() { return "Mock tool: " + name; }
            @Override
            public ToolSchema getSchema() {
                return ToolSchema.empty();
            }
            @Override
            public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("mock result");
            }
        };
    }
}
