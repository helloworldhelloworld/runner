package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.InMemoryProvider;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Transmission chain: ToolRegistry → AgentLoop.llmOptions → LLMProvider.complete()
 *
 * Existing tests verify that AgentLoop.getLlmOptions().getToolDefinitions() is populated
 * (data-at-rest check). This test goes further: it asserts that the ACTUAL LLMOptions
 * received by the LLMProvider during a live call contains the tool definitions
 * (data-in-motion check).
 *
 * This matters because ToolCallingLoop creates its own internal LLMOptions or passes
 * through the one from AgentLoop. If any intermediate code strips or replaces the options,
 * tools become invisible to the LLM.
 */
@DisplayName("Transmission: ToolRegistry → AgentLoop → ToolCallingLoop → LLMProvider options")
class AgentLoopToolDefinitionTransmissionTest {

    private MemoryProvider memory;

    @BeforeEach
    void setUp() {
        memory = new InMemoryProvider();
    }

    @Test
    @DisplayName("sync: provider.complete() 收到的 LLMOptions 包含注册工具的 definitions")
    void syncPathTransmitsToolDefinitionsToProvider() {
        Tool searchTool = noopTool("search", "Search the web");
        Tool readTool = noopTool("read_file", "Read a file");

        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("done");

        AgentLoop loop = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(memory)
                .addTool(searchTool)
                .addTool(readTool)
                .systemPrompt("test")
                .build();

        loop.run("hello", "sess-1");

        LLMOptions receivedOptions = spy.lastOptions();
        assertNotNull(receivedOptions, "Provider must receive LLMOptions");
        List<Map<String, Object>> defs = receivedOptions.getToolDefinitions();
        assertNotNull(defs, "toolDefinitions must not be null in received options");
        assertEquals(2, defs.size(), "Provider should see 2 tool definitions");

        List<String> names = defs.stream().map(d -> (String) d.get("name")).toList();
        assertTrue(names.contains("search"), "search tool must reach provider");
        assertTrue(names.contains("read_file"), "read_file tool must reach provider");
    }

    @Test
    @DisplayName("reactive: provider.completeStreamReactive() 收到的 LLMOptions 包含工具 definitions")
    void reactivePathTransmitsToolDefinitionsToProvider() {
        Tool calcTool = noopTool("calc", "Calculator");

        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("done");

        AgentLoop loop = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(memory)
                .addTool(calcTool)
                .systemPrompt("test")
                .build();

        StepVerifier.create(loop.runReactive("compute", "sess-2"))
                .thenConsumeWhile(e -> true)
                .verifyComplete();

        LLMOptions receivedOptions = spy.lastOptions();
        assertNotNull(receivedOptions, "Provider must receive LLMOptions in reactive path");
        List<Map<String, Object>> defs = receivedOptions.getToolDefinitions();
        assertNotNull(defs);
        assertEquals(1, defs.size());
        assertEquals("calc", defs.get(0).get("name"));
    }

    @Test
    @DisplayName("sync: tool 调用后第二轮 LLM call 仍然携带 toolDefinitions")
    void toolDefinitionsPersistAcrossToolCallingRounds() {
        Tool echoTool = new Tool() {
            @Override public String getName() { return "echo"; }
            @Override public String getDescription() { return "Echo"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("echoed");
            }
        };

        CapturingLLMProvider spy = CapturingLLMProvider.toolThenEnd(
                "echo", Map.of("input", "hi"), "final answer");

        AgentLoop loop = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(memory)
                .addTool(echoTool)
                .systemPrompt("test")
                .build();

        loop.run("echo hi", "sess-3");

        assertEquals(2, spy.callCount(), "Should have 2 LLM calls (tool_use + end_turn)");

        for (int i = 0; i < spy.optionsHistory().size(); i++) {
            LLMOptions opts = spy.optionsHistory().get(i);
            assertNotNull(opts.getToolDefinitions(),
                    "toolDefinitions must be present in call #" + (i + 1));
            assertEquals(1, opts.getToolDefinitions().size(),
                    "toolDefinitions count must be 1 in call #" + (i + 1));
            assertEquals("echo", opts.getToolDefinitions().get(0).get("name"),
                    "echo tool must be in call #" + (i + 1));
        }
    }

    @Test
    @DisplayName("caller-supplied toolDefinitions 传递到 provider，不被 registry 覆盖")
    void callerSuppliedDefinitionsReachProvider() {
        Tool registryTool = noopTool("from_registry", "Should NOT appear");

        List<Map<String, Object>> custom = List.of(
                Map.of("name", "custom_only", "description", "Caller-supplied tool",
                        "input_schema", Map.of("type", "object")));

        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("done");

        AgentLoop loop = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(memory)
                .addTool(registryTool)
                .llmOptions(LLMOptions.builder().toolDefinitions(custom).build())
                .systemPrompt("test")
                .build();

        loop.run("hi", "sess-4");

        LLMOptions received = spy.lastOptions();
        List<Map<String, Object>> defs = received.getToolDefinitions();
        assertEquals(1, defs.size());
        assertEquals("custom_only", defs.get(0).get("name"),
                "Caller-supplied definitions must override registry-derived ones");
    }

    @Test
    @DisplayName("tool 的 schema 正确嵌套在 toolDefinitions 的 input_schema 中")
    void toolSchemaCorrectlyNestedInDefinition() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of("type", "string", "description", "Search query")
                ),
                "required", List.of("query")
        );

        Tool toolWithSchema = new Tool() {
            @Override public String getName() { return "search"; }
            @Override public String getDescription() { return "Search engine"; }
            @Override public ToolSchema getSchema() { return new ToolSchema(schema); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("result");
            }
        };

        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("done");

        AgentLoop loop = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(memory)
                .addTool(toolWithSchema)
                .systemPrompt("test")
                .build();

        loop.run("search something", "sess-5");

        LLMOptions received = spy.lastOptions();
        Map<String, Object> def = received.getToolDefinitions().get(0);
        assertEquals("search", def.get("name"));
        assertEquals("Search engine", def.get("description"));

        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = (Map<String, Object>) def.get("input_schema");
        assertNotNull(inputSchema, "input_schema must be present in tool definition");
        assertEquals("object", inputSchema.get("type"));
        assertNotNull(inputSchema.get("properties"), "properties must be nested in input_schema");
    }

    private static Tool noopTool(String name, String description) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return description; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("ok");
            }
        };
    }
}
