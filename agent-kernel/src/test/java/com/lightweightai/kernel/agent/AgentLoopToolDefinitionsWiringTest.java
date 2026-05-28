package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.memory.InMemoryProvider;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentLoop: toolDefinitions auto-injection wiring")
class AgentLoopToolDefinitionsWiringTest {

    private ToolRegistry toolRegistry;
    private InMemoryProvider memory;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry();
        toolRegistry.register(noopTool("search"));
        toolRegistry.register(noopTool("calculate"));
        memory = new InMemoryProvider();
    }

    @Test
    @DisplayName("toolDefinitions from ToolRegistry are auto-injected into LLMOptions when caller does not provide them")
    void autoInjectsToolDefinitionsWhenNotProvided() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("done");

        AgentLoop agent = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(memory)
                .toolRegistry(toolRegistry)
                .systemPrompt("test")
                .build();

        agent.run("hello", "session-1");

        LLMOptions captured = spy.lastOptions();
        assertNotNull(captured, "LLMOptions must be captured");
        List<Map<String, Object>> defs = captured.getToolDefinitions();
        assertNotNull(defs, "toolDefinitions must not be null");
        assertEquals(2, defs.size(), "should contain 2 tool definitions");

        List<String> names = defs.stream().map(d -> (String) d.get("name")).toList();
        assertTrue(names.contains("search"));
        assertTrue(names.contains("calculate"));
    }

    @Test
    @DisplayName("caller-provided toolDefinitions take precedence over auto-injection")
    void callerProvidedToolDefinitionsPreserved() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("done");

        List<Map<String, Object>> callerDefs = List.of(
                Map.of("name", "custom_tool", "description", "custom"));

        AgentLoop agent = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(memory)
                .toolRegistry(toolRegistry)
                .llmOptions(LLMOptions.builder().toolDefinitions(callerDefs).build())
                .systemPrompt("test")
                .build();

        agent.run("hello", "session-2");

        LLMOptions captured = spy.lastOptions();
        assertNotNull(captured);
        List<Map<String, Object>> defs = captured.getToolDefinitions();
        assertEquals(1, defs.size(), "caller-provided defs should be preserved");
        assertEquals("custom_tool", defs.get(0).get("name"));
    }

    @Test
    @DisplayName("empty ToolRegistry results in empty toolDefinitions")
    void emptyRegistryNoToolDefs() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("done");

        AgentLoop agent = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(memory)
                .toolRegistry(new ToolRegistry())
                .systemPrompt("test")
                .build();

        agent.run("hello", "session-3");

        LLMOptions captured = spy.lastOptions();
        assertNotNull(captured);
        assertTrue(captured.getToolDefinitions() == null || captured.getToolDefinitions().isEmpty());
    }

    @Test
    @DisplayName("toolDefinitions survive the full chain: AgentLoop → ToolCallingLoop → LLMProvider")
    void toolDefinitionsReachProvider() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("final answer");

        AgentLoop agent = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(memory)
                .toolRegistry(toolRegistry)
                .systemPrompt("You are helpful")
                .build();

        AgentResponse response = agent.run("what is 2+2?", "session-4");

        assertNotNull(response);
        assertEquals("final answer", response.getText());

        LLMOptions opts = spy.lastOptions();
        assertNotNull(opts.getToolDefinitions());
        assertFalse(opts.getToolDefinitions().isEmpty(),
                "tool definitions must reach the LLM provider through the full chain");
    }

    @Test
    @DisplayName("reactive path also carries toolDefinitions to provider")
    void reactivePathCarriesToolDefinitions() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("streamed");

        AgentLoop agent = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(memory)
                .toolRegistry(toolRegistry)
                .systemPrompt("test")
                .build();

        agent.runReactive("hello reactive", "session-5").collectList().block();

        LLMOptions captured = spy.lastOptions();
        assertNotNull(captured);
        List<Map<String, Object>> defs = captured.getToolDefinitions();
        assertNotNull(defs);
        assertEquals(2, defs.size());
    }

    private static Tool noopTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name + " tool"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("ok");
            }
        };
    }
}
