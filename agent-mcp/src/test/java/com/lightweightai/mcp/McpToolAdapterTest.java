package com.lightweightai.mcp;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolResult;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * McpToolAdapter 测试
 *
 * 验证框架 Tool → MCP SyncToolSpecification 的转换逻辑
 */
@DisplayName("McpToolAdapter - Tool to MCP conversion")
class McpToolAdapterTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    @Test
    @DisplayName("Convert simple tool to MCP SyncToolSpecification")
    void shouldConvertSimpleToolToMcp() {
        Map<String, Object> nameSchema = new HashMap<>();
        nameSchema.put("type", "string");
        nameSchema.put("description", "Name to greet");
        Tool tool = new SimpleTool("greet", "Say hello",
            ToolSchema.withRequired(
                Collections.singletonMap("name", (Object) nameSchema),
                "name"
            ),
            args -> ToolResult.success("Hello, " + args.get("name") + "!")
        );

        McpServerFeatures.SyncToolSpecification spec = McpToolAdapter.toMcpTool(tool);

        assertNotNull(spec);
        assertEquals("greet", spec.tool().name());
        assertEquals("Say hello", spec.tool().description());
        assertNotNull(spec.tool().inputSchema());
    }

    @Test
    @DisplayName("Convert tool with empty schema to MCP")
    void shouldConvertToolWithEmptySchema() {
        Tool tool = new SimpleTool("get_time", "Get current time",
            ToolSchema.empty(),
            args -> ToolResult.success("2026-02-28T12:00:00")
        );

        McpServerFeatures.SyncToolSpecification spec = McpToolAdapter.toMcpTool(tool);

        assertNotNull(spec);
        assertEquals("get_time", spec.tool().name());
    }

    @Test
    @DisplayName("MCP tool handler executes framework tool successfully")
    void shouldExecuteToolViaHandler() {
        Map<String, Object> aSchema = new HashMap<>();
        aSchema.put("type", "number");
        Map<String, Object> bSchema = new HashMap<>();
        bSchema.put("type", "number");
        Map<String, Object> addProps = new HashMap<>();
        addProps.put("a", aSchema);
        addProps.put("b", bSchema);
        Tool tool = new SimpleTool("add", "Add numbers",
            ToolSchema.withRequired(
                addProps,
                "a", "b"
            ),
            args -> {
                double a = ((Number) args.get("a")).doubleValue();
                double b = ((Number) args.get("b")).doubleValue();
                return ToolResult.success(String.valueOf((int) (a + b)));
            }
        );

        McpServerFeatures.SyncToolSpecification spec = McpToolAdapter.toMcpTool(tool);

        // Simulate MCP handler invocation
        Map<String, Object> addCallArgs = new HashMap<>();
        addCallArgs.put("a", 10);
        addCallArgs.put("b", 20);
        McpSchema.CallToolResult result = spec.call().apply(null, addCallArgs);

        assertNotNull(result);
        assertFalse(result.isError());
        assertFalse(result.content().isEmpty());
        assertTrue(result.content().get(0) instanceof McpSchema.TextContent);
        assertEquals("30", ((McpSchema.TextContent) result.content().get(0)).text());
    }

    @Test
    @DisplayName("MCP tool handler returns error on tool failure")
    void shouldReturnErrorOnToolFailure() {
        Tool tool = new SimpleTool("fail", "Always fails",
            ToolSchema.empty(),
            args -> ToolResult.error("Something went wrong")
        );

        McpServerFeatures.SyncToolSpecification spec = McpToolAdapter.toMcpTool(tool);
        McpSchema.CallToolResult result = spec.call().apply(null, Collections.emptyMap());

        assertNotNull(result);
        assertTrue(result.isError());
    }

    @Test
    @DisplayName("MCP tool handler catches exceptions gracefully")
    void shouldCatchExceptionInHandler() {
        Tool tool = new SimpleTool("boom", "Throws exception",
            ToolSchema.empty(),
            args -> { throw new RuntimeException("Boom!"); }
        );

        McpServerFeatures.SyncToolSpecification spec = McpToolAdapter.toMcpTool(tool);
        McpSchema.CallToolResult result = spec.call().apply(null, Collections.emptyMap());

        assertNotNull(result);
        assertTrue(result.isError());
        assertTrue(((McpSchema.TextContent) result.content().get(0)).text().contains("Boom!"));
    }

    @Test
    @DisplayName("Convert entire ToolRegistry to MCP tools")
    void shouldConvertRegistryToMcpTools() {
        registry.register(new SimpleTool("tool1", "Tool 1", ToolSchema.empty(),
            args -> ToolResult.success("result1")));
        registry.register(new SimpleTool("tool2", "Tool 2", ToolSchema.empty(),
            args -> ToolResult.success("result2")));
        registry.register(new SimpleTool("tool3", "Tool 3", ToolSchema.empty(),
            args -> ToolResult.success("result3")));

        List<McpServerFeatures.SyncToolSpecification> specs = McpToolAdapter.toMcpTools(registry);

        assertEquals(3, specs.size());
    }

    @Test
    @DisplayName("Disabled tools are excluded from MCP conversion")
    void shouldExcludeDisabledTools() {
        registry.register(new SimpleTool("enabled", "Enabled", ToolSchema.empty(),
            args -> ToolResult.success("ok")));
        registry.register(new SimpleTool("disabled", "Disabled", ToolSchema.empty(),
            args -> ToolResult.success("ok")));
        registry.disable("disabled");

        List<McpServerFeatures.SyncToolSpecification> specs = McpToolAdapter.toMcpTools(registry);

        assertEquals(1, specs.size());
        assertEquals("enabled", specs.get(0).tool().name());
    }

    // ==================== Helper ====================

    @FunctionalInterface
    interface ToolExecutor {
        ToolResult execute(Map<String, Object> args);
    }

    private static class SimpleTool implements Tool {
        private final String name;
        private final String description;
        private final ToolSchema schema;
        private final ToolExecutor executor;

        SimpleTool(String name, String description, ToolSchema schema, ToolExecutor executor) {
            this.name = name;
            this.description = description;
            this.schema = schema;
            this.executor = executor;
        }

        @Override public String getName() { return name; }
        @Override public String getDescription() { return description; }
        @Override public ToolSchema getSchema() { return schema; }
        @Override public ToolResult execute(Map<String, Object> args) { return executor.execute(args); }
    }
}
