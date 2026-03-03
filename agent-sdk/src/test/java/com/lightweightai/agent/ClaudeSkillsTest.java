package com.lightweightai.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lightweightai.agent.plugin.Plugin;
import com.lightweightai.kernel.core.ToolExecutor;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.plugin.JsonSchemaGenerator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Claude Skills integration - Plugin system with typed functions and JSON schema generation
 */
public class ClaudeSkillsTest {

    /**
     * Example parameter class with Jackson annotations
     */
    public static class AddRequest {
        @JsonProperty(required = true)
        @JsonSchemaGenerator.JsonPropertyDescription("First number")
        private int a;

        @JsonProperty(required = true)
        @JsonSchemaGenerator.JsonPropertyDescription("Second number")
        private int b;

        public AddRequest() {}

        public int getA() { return a; }
        public void setA(int a) { this.a = a; }

        public int getB() { return b; }
        public void setB(int b) { this.b = b; }
    }

    /**
     * Example Plugin with typed function
     */
    public static class MathPlugin extends Plugin {
        @Override
        public String getName() {
            return "math";
        }

        @Override
        public String getDescription() {
            return "Basic math operations";
        }

        public MathPlugin() {
            // Register typed function
            registerFunction(
                "add",
                "Add two numbers together",
                AddRequest.class,
                this::add
            );
        }

        private Integer add(AddRequest request) {
            return request.getA() + request.getB();
        }
    }

    @Test
    void shouldRegisterFunctionWithTypedParameters() {
        MathPlugin plugin = new MathPlugin();

        // Verify function is registered
        assertEquals(1, plugin.getFunctions().size());
        assertNotNull(plugin.getFunction("add"));
    }

    @Test
    void shouldGenerateClaudeToolDefinition() {
        MathPlugin plugin = new MathPlugin();

        // Get Claude tool definitions
        List<Map<String, Object>> tools = plugin.toClaudeTools();

        assertEquals(1, tools.size());

        Map<String, Object> tool = tools.get(0);
        assertEquals("add", tool.get("name"));
        assertEquals("Add two numbers together", tool.get("description"));

        // Verify input schema
        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = (Map<String, Object>) tool.get("input_schema");
        assertNotNull(inputSchema);
        assertEquals("object", inputSchema.get("type"));

        // Verify properties
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
        assertNotNull(properties);
        assertTrue(properties.containsKey("a"));
        assertTrue(properties.containsKey("b"));
    }

    @Test
    void shouldExecuteToolCall() throws Exception {
        MathPlugin plugin = new MathPlugin();
        ToolExecutor executor = new ToolExecutor();
        executor.registerFunctions(plugin.getFunctions());

        // Create a tool call
        Map<String, Object> addArgs = new HashMap<>();
        addArgs.put("a", 10);
        addArgs.put("b", 20);
        ToolCall toolCall = new ToolCall(
            "call_123",
            "add",
            addArgs
        );

        // Execute
        ToolResult result = executor.executeToolCall(toolCall);

        // Verify result
        assertFalse(result.isError());
        assertTrue(result.getContent().contains("30"));
    }

    @Test
    void shouldHandleToolNotFound() {
        ToolExecutor executor = new ToolExecutor();

        ToolCall toolCall = new ToolCall(
            "call_123",
            "unknown_tool",
            Collections.emptyMap()
        );

        ToolResult result = executor.executeToolCall(toolCall);

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("Tool not found"));
    }

    @Test
    void shouldExecuteMultipleToolCalls() throws Exception {
        MathPlugin plugin = new MathPlugin();
        ToolExecutor executor = new ToolExecutor();
        executor.registerFunctions(plugin.getFunctions());

        Map<String, Object> args1 = new HashMap<>();
        args1.put("a", 1);
        args1.put("b", 2);
        Map<String, Object> args2 = new HashMap<>();
        args2.put("a", 10);
        args2.put("b", 20);
        ToolCall call1 = new ToolCall("call_1", "add", args1);
        ToolCall call2 = new ToolCall("call_2", "add", args2);

        List<ToolResult> results = executor.executeToolCalls(Arrays.asList(call1, call2));

        assertEquals(2, results.size());
        assertFalse(results.get(0).isError());
        assertFalse(results.get(1).isError());
    }

    /**
     * Test plugin for duplicate registration test
     */
    public static class TestPlugin extends Plugin {
        @Override
        public String getName() {
            return "test";
        }

        @Override
        public String getDescription() {
            return "test plugin";
        }

        public void testDuplicateRegistration() {
            // Register same function twice - should throw
            registerFunction("test", input -> "result1");
            registerFunction("test", input -> "result2"); // Should throw
        }
    }

    @Test
    void shouldPreventDuplicateFunctionRegistration() {
        TestPlugin plugin = new TestPlugin();
        assertThrows(IllegalArgumentException.class, plugin::testDuplicateRegistration);
    }
}
