package com.lightweightai.agent;

import com.lightweightai.agent.plugin.Plugin;
import com.lightweightai.kernel.agent.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class AgentBuilderTest {

    static class NoopPlugin extends Plugin {
        private final String name;
        NoopPlugin(String name) { this.name = name; }
        @Override public String getName() { return name; }
        @Override public String getDescription() { return "noop"; }
    }

    static class EchoPlugin extends Plugin {
        EchoPlugin() {
            registerFunction("echo", (Function<Map<String, Object>, Object>) m -> m.get("text"));
        }
        @Override public String getName() { return "echo"; }
        @Override public String getDescription() { return "echoes"; }
    }

    @Test
    void shouldRequireProviderAndApiKey() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> Agent.builder().build());
        assertTrue(ex.getMessage().toLowerCase().contains("required"));
    }

    @Test
    void shouldBuildClaudeAgentWithDefaults() {
        AgentBuilder b = Agent.builder().claude("k").options(ChatOptions.builder().build());
        Agent agent = b.build();
        assertNotNull(agent);
        assertEquals("claude", b.getLlmProvider());
        assertEquals("k", b.getApiKey());
        assertEquals("claude-3-5-sonnet-20241022", b.getModel());
        assertFalse(b.isEnableMemory());
        assertEquals(Duration.ofMinutes(5), b.getTimeout());
    }

    @Test
    void shouldBuildOpenAIAgentWithCustomModel() {
        AgentBuilder b = Agent.builder().openai("k", "gpt-4o");
        Agent agent = b.build();
        assertNotNull(agent);
        assertEquals("openai", b.getLlmProvider());
        assertEquals("gpt-4o", b.getModel());
    }

    @Test
    void shouldRegisterPluginsAndExposeThemViaAgent() {
        Plugin p1 = new NoopPlugin("p1");
        Plugin p2 = new NoopPlugin("p2");
        Agent agent = Agent.builder().claude("k").plugins(p1, p2).build();
        List<Plugin> plugins = agent.getPlugins();
        assertEquals(2, plugins.size());
        assertEquals("p1", plugins.get(0).getName());
    }

    @Test
    void shouldEnableMemoryWhenRequested() {
        Agent withMem = Agent.builder().claude("k").withMemory().build();
        Agent noMem = Agent.builder().claude("k").build();
        assertNotNull(withMem.getMemory());
        assertNull(noMem.getMemory());
    }

    @Test
    void clearMemoryShouldBeNoOpWhenMemoryDisabled() {
        Agent agent = Agent.builder().claude("k").build();
        assertDoesNotThrow(agent::clearMemory);
    }

    @Test
    void shouldDynamicallyAddPlugin() {
        Agent agent = Agent.builder().claude("k").build();
        assertEquals(0, agent.getPlugins().size());
        Agent returned = agent.addPlugin(new EchoPlugin());
        assertSame(agent, returned);
        assertEquals(1, agent.getPlugins().size());
    }

    @Test
    void shouldAcceptCustomToolRegistry() {
        ToolRegistry registry = new ToolRegistry();
        AgentBuilder b = Agent.builder().claude("k").tools(registry);
        assertSame(registry, b.getToolRegistry());
        assertNotNull(b.build());
    }

    @Test
    void shouldLazilyCreateToolRegistryWhenAddingAnnotatedObject() {
        AgentBuilder b = Agent.builder().claude("k");
        assertNull(b.getToolRegistry());
        b.tools(new Object()); // empty, but ensures registry is created
        assertNotNull(b.getToolRegistry());
    }

    @Test
    void shouldRespectCustomTimeout() {
        AgentBuilder b = Agent.builder().claude("k").timeout(Duration.ofSeconds(30));
        assertEquals(Duration.ofSeconds(30), b.getTimeout());
    }

    @Test
    void chatStreamShouldNotBeImplementedYet() {
        Agent agent = Agent.builder().claude("k").build();
        assertThrows(UnsupportedOperationException.class,
                () -> agent.chatStream("hi", new StreamCallback() {}));
    }
}
