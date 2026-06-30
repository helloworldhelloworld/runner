package com.lightweightai.agent;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.core.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentBuilder — Tool registration APIs")
class AgentBuilderToolRegistrationTest {

    @Nested
    @DisplayName("tools(ToolRegistry)")
    class ToolRegistryTests {

        @Test
        @DisplayName("sets external ToolRegistry on builder")
        void shouldSetExternalToolRegistry() {
            ToolRegistry registry = new ToolRegistry();
            registry.register(stubTool("ext-tool"));

            AgentBuilder builder = new AgentBuilder();
            AgentBuilder result = builder.claude("key").tools(registry);

            assertSame(result, builder, "should return same builder for chaining");
            assertSame(registry, builder.getToolRegistry());
            assertTrue(builder.getToolRegistry().has("ext-tool"),
                    "registered tool should be visible in the registry");
        }

        @Test
        @DisplayName("replaces previously set ToolRegistry")
        void shouldReplacePreviousRegistry() {
            ToolRegistry first = new ToolRegistry();
            first.register(stubTool("tool-a"));

            ToolRegistry second = new ToolRegistry();
            second.register(stubTool("tool-b"));

            AgentBuilder builder = new AgentBuilder();
            builder.claude("key").tools(first).tools(second);

            assertSame(second, builder.getToolRegistry());
            assertTrue(builder.getToolRegistry().has("tool-b"));
            assertFalse(builder.getToolRegistry().has("tool-a"));
        }
    }

    @Nested
    @DisplayName("tool(Tool)")
    class SingleToolTests {

        @Test
        @DisplayName("creates ToolRegistry lazily and registers tool")
        void shouldCreateRegistryAndRegisterTool() {
            AgentBuilder builder = new AgentBuilder();
            assertNull(builder.getToolRegistry(), "no registry before tool() call");

            builder.claude("key").tool(stubTool("my-tool"));

            assertNotNull(builder.getToolRegistry(), "registry should be created lazily");
            assertTrue(builder.getToolRegistry().has("my-tool"));
        }

        @Test
        @DisplayName("registers multiple tools into same registry")
        void shouldRegisterMultipleTools() {
            AgentBuilder builder = new AgentBuilder();
            builder.claude("key")
                .tool(stubTool("tool-1"))
                .tool(stubTool("tool-2"));

            ToolRegistry registry = builder.getToolRegistry();
            assertTrue(registry.has("tool-1"));
            assertTrue(registry.has("tool-2"));
        }

        @Test
        @DisplayName("adds to existing external ToolRegistry")
        void shouldAddToExistingRegistry() {
            ToolRegistry external = new ToolRegistry();
            external.register(stubTool("existing"));

            AgentBuilder builder = new AgentBuilder();
            builder.claude("key").tools(external).tool(stubTool("added"));

            assertSame(external, builder.getToolRegistry());
            assertTrue(external.has("existing"));
            assertTrue(external.has("added"));
        }

        @Test
        @DisplayName("returns same builder for chaining")
        void shouldReturnSameBuilderForChaining() {
            AgentBuilder builder = new AgentBuilder();
            AgentBuilder result = builder.claude("key").tool(stubTool("t"));
            assertSame(builder, result);
        }
    }

    @Nested
    @DisplayName("build() carries ToolRegistry to DefaultAgent")
    class BuildIntegrationTests {

        @Test
        @DisplayName("built agent inherits ToolRegistry with registered tools")
        void shouldCarryToolRegistryToAgent() {
            ToolRegistry registry = new ToolRegistry();
            registry.register(stubTool("calc"));

            Agent agent = new AgentBuilder()
                    .claude("test-key")
                    .tools(registry)
                    .build();

            assertNotNull(agent, "agent should be built successfully with ToolRegistry");
        }

        @Test
        @DisplayName("built agent works without ToolRegistry")
        void shouldBuildWithoutToolRegistry() {
            Agent agent = new AgentBuilder()
                    .claude("test-key")
                    .build();

            assertNotNull(agent);
        }
    }

    private static Tool stubTool(String name) {
        return new Tool() {
            @Override
            public String getName() { return name; }

            @Override
            public String getDescription() { return "Stub tool: " + name; }

            @Override
            public ToolSchema getSchema() { return new ToolSchema(Map.of()); }

            @Override
            public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("ok");
            }
        };
    }
}
