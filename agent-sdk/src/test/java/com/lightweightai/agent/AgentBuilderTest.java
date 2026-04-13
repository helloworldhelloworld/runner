package com.lightweightai.agent;

import com.lightweightai.agent.plugin.Plugin;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentBuilder - fluent builder for creating Agent instances")
class AgentBuilderTest {

    // ==================== LLM provider configuration ====================

    @Test
    @DisplayName("claude sets provider and default model")
    void claudeSetsProviderAndDefaultModel() {
        AgentBuilder builder = new AgentBuilder();
        builder.claude("sk-test");

        assertEquals("claude", builder.getLlmProvider());
        assertEquals("sk-test", builder.getApiKey());
        assertEquals("claude-3-5-sonnet-20241022", builder.getModel());
    }

    @Test
    @DisplayName("claude with explicit model sets model")
    void claudeWithExplicitModel() {
        AgentBuilder builder = new AgentBuilder();
        builder.claude("sk-test", "claude-3-opus-20240229");

        assertEquals("claude-3-opus-20240229", builder.getModel());
    }

    @Test
    @DisplayName("openai sets provider and default model")
    void openaiSetsProviderAndDefaultModel() {
        AgentBuilder builder = new AgentBuilder();
        builder.openai("sk-openai");

        assertEquals("openai", builder.getLlmProvider());
        assertEquals("sk-openai", builder.getApiKey());
        assertEquals("gpt-4", builder.getModel());
    }

    @Test
    @DisplayName("openai with explicit model sets model")
    void openaiWithExplicitModel() {
        AgentBuilder builder = new AgentBuilder();
        builder.openai("sk-openai", "gpt-4-turbo");

        assertEquals("gpt-4-turbo", builder.getModel());
    }

    // ==================== tools ====================

    @Test
    @DisplayName("tools(ToolRegistry) sets tool registry")
    void toolsWithRegistry() {
        AgentBuilder builder = new AgentBuilder();
        ToolRegistry registry = new ToolRegistry();
        builder.claude("sk-test").tools(registry);

        assertSame(registry, builder.getToolRegistry());
    }

    @Test
    @DisplayName("tool(Tool) creates registry if needed and registers tool")
    void toolCreatesRegistryIfNeeded() {
        AgentBuilder builder = new AgentBuilder();
        assertNull(builder.getToolRegistry());

        builder.claude("sk-test").tool(new StubTool("test-tool"));

        assertNotNull(builder.getToolRegistry());
    }

    // ==================== plugins ====================

    @Test
    @DisplayName("plugin adds single plugin")
    void pluginAddsSingle() {
        AgentBuilder builder = new AgentBuilder();
        builder.claude("sk-test").plugin(new StubPlugin("p1"));

        assertEquals(1, builder.getPlugins().size());
    }

    @Test
    @DisplayName("plugins adds multiple plugins")
    void pluginsAddsMultiple() {
        AgentBuilder builder = new AgentBuilder();
        builder.claude("sk-test").plugins(new StubPlugin("p1"), new StubPlugin("p2"));

        assertEquals(2, builder.getPlugins().size());
    }

    @Test
    @DisplayName("getPlugins returns defensive copy")
    void getPluginsDefensiveCopy() {
        AgentBuilder builder = new AgentBuilder();
        builder.claude("sk-test").plugin(new StubPlugin("p1"));

        List<Plugin> copy = builder.getPlugins();
        copy.add(new StubPlugin("extra"));
        assertEquals(1, builder.getPlugins().size());
    }

    // ==================== options ====================

    @Test
    @DisplayName("withMemory enables memory")
    void withMemoryEnablesMemory() {
        AgentBuilder builder = new AgentBuilder();
        assertFalse(builder.isEnableMemory());

        builder.claude("sk-test").withMemory();
        assertTrue(builder.isEnableMemory());
    }

    @Test
    @DisplayName("options sets default chat options")
    void optionsSetsDefaults() {
        AgentBuilder builder = new AgentBuilder();
        ChatOptions options = ChatOptions.builder()
            .temperature(0.5)
            .maxTokens(1000)
            .build();
        builder.claude("sk-test").options(options);

        assertEquals(options, builder.getDefaultOptions());
    }

    @Test
    @DisplayName("timeout sets custom timeout")
    void timeoutSetsCustom() {
        AgentBuilder builder = new AgentBuilder();
        builder.claude("sk-test").timeout(Duration.ofSeconds(30));

        assertEquals(Duration.ofSeconds(30), builder.getTimeout());
    }

    @Test
    @DisplayName("default timeout is 5 minutes")
    void defaultTimeoutIs5Minutes() {
        AgentBuilder builder = new AgentBuilder();
        assertEquals(Duration.ofMinutes(5), builder.getTimeout());
    }

    // ==================== build ====================

    @Test
    @DisplayName("build succeeds with provider and key")
    void buildSucceeds() {
        Agent agent = new AgentBuilder()
            .claude("sk-test")
            .build();

        assertNotNull(agent);
    }

    @Test
    @DisplayName("build throws when no provider set")
    void buildThrowsWithoutProvider() {
        AgentBuilder builder = new AgentBuilder();
        assertThrows(IllegalStateException.class, builder::build);
    }

    // ==================== fluent API ====================

    @Test
    @DisplayName("builder methods are chainable")
    void builderMethodsChainable() {
        Agent agent = new AgentBuilder()
            .claude("sk-test", "claude-3-5-sonnet")
            .withMemory()
            .options(ChatOptions.builder().temperature(0.7).build())
            .timeout(Duration.ofMinutes(2))
            .build();

        assertNotNull(agent);
    }

    // ==================== test stubs ====================

    private static class StubTool implements Tool {
        private final String name;
        StubTool(String name) { this.name = name; }
        @Override public String getName() { return name; }
        @Override public String getDescription() { return "stub"; }
        @Override public ToolSchema getSchema() { return null; }
        @Override public ToolResult execute(Map<String, Object> args) { return ToolResult.success("ok"); }
    }

    private static class StubPlugin extends Plugin {
        private final String name;
        StubPlugin(String name) { this.name = name; }
        @Override public String getName() { return name; }
        @Override public String getDescription() { return "stub plugin"; }
    }
}
