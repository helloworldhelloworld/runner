package com.lightweightai.mcp;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.core.ToolExecutor;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * McpToolManager 测试
 *
 * 验证核心设计：ToolExecutor 透明调用本地工具和 MCP 工具
 */
@DisplayName("McpToolManager - Transparent tool execution")
class McpToolManagerTest {

    @Test
    @DisplayName("Local tools registered via builder are accessible")
    void shouldRegisterLocalTools() {
        McpToolManager manager = McpToolManager.create()
            .registerLocal(new FakeLocalTool("add", "Add numbers"))
            .registerLocal(new FakeLocalTool("multiply", "Multiply numbers"))
            .build();

        assertEquals(2, manager.getToolRegistry().enabledCount());
        assertTrue(manager.getToolRegistry().has("add"));
        assertTrue(manager.getToolRegistry().has("multiply"));

        manager.close();
    }

    @Test
    @DisplayName("ToolExecutor calls local and MCP tools with identical code")
    void shouldCallToolsTransparently() {
        // Register both "local" and "simulated MCP" tools
        Tool localTool = new FakeLocalTool("local_add", "Local add");
        Tool mcpTool = new FakeMcpTool("mcp_forecast", "MCP weather forecast");

        McpToolManager manager = McpToolManager.create()
            .registerLocal(localTool)
            .registerLocal(mcpTool)  // In real usage, this comes from addMcpServer()
            .build();

        ToolExecutor executor = manager.getToolExecutor();

        // Same calling code for both
        ToolResult localResult = executor.executeToolCall(
            new ToolCall("1", "local_add", Collections.singletonMap("a", 1)));
        ToolResult mcpResult = executor.executeToolCall(
            new ToolCall("2", "mcp_forecast", Collections.singletonMap("city", "Tokyo")));

        assertFalse(localResult.isError());
        assertFalse(mcpResult.isError());
        assertEquals("local:local_add:executed", localResult.getContent());
        assertEquals("mcp:mcp_forecast:executed", mcpResult.getContent());

        manager.close();
    }

    @Test
    @DisplayName("ToolExecutor returns error for unknown tools")
    void shouldReturnErrorForUnknownTool() {
        McpToolManager manager = McpToolManager.create()
            .registerLocal(new FakeLocalTool("known", "Known tool"))
            .build();

        ToolExecutor executor = manager.getToolExecutor();
        ToolResult result = executor.executeToolCall(
            new ToolCall("1", "unknown_tool", Collections.emptyMap()));

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("not found"));

        manager.close();
    }

    @Test
    @DisplayName("getToolDefinitions includes all tools regardless of source")
    void shouldIncludeAllToolDefinitions() {
        McpToolManager manager = McpToolManager.create()
            .registerLocal(new FakeLocalTool("local1", "Local 1"))
            .registerLocal(new FakeLocalTool("local2", "Local 2"))
            .registerLocal(new FakeMcpTool("mcp1", "MCP 1"))
            .build();

        List<Map<String, Object>> definitions = manager.getToolExecutor().getToolDefinitions();
        assertEquals(3, definitions.size());

        manager.close();
    }

    @Test
    @DisplayName("Dynamic tool registration after build")
    void shouldSupportDynamicRegistration() {
        McpToolManager manager = McpToolManager.create()
            .registerLocal(new FakeLocalTool("initial", "Initial"))
            .build();

        assertEquals(1, manager.getToolRegistry().enabledCount());

        manager.registerLocal(new FakeLocalTool("dynamic", "Dynamic"));
        assertEquals(2, manager.getToolRegistry().enabledCount());

        ToolResult result = manager.getToolExecutor().executeToolCall(
            new ToolCall("1", "dynamic", Collections.emptyMap()));
        assertFalse(result.isError());

        manager.close();
    }

    @Test
    @DisplayName("Close clears MCP clients list")
    void shouldCloseCleanly() {
        McpToolManager manager = McpToolManager.create()
            .registerLocal(new FakeLocalTool("tool", "Tool"))
            .build();

        assertTrue(manager.getMcpClients().isEmpty());
        manager.close();
        assertTrue(manager.getMcpClients().isEmpty());
    }

    @Test
    @DisplayName("Tools from different sources coexist in same registry")
    void shouldMixToolSourcesInRegistry() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new FakeLocalTool("local", "Local tool"));
        registry.register(new FakeMcpTool("mcp", "MCP tool"));

        // Both accessible via same registry
        assertTrue(registry.has("local"));
        assertTrue(registry.has("mcp"));

        // Both callable via same ToolExecutor
        ToolExecutor executor = new ToolExecutor(registry);
        ToolResult r1 = executor.executeToolCall(new ToolCall("1", "local", Collections.emptyMap()));
        ToolResult r2 = executor.executeToolCall(new ToolCall("2", "mcp", Collections.emptyMap()));

        assertFalse(r1.isError());
        assertFalse(r2.isError());

        // Can distinguish source via ToolMetadata if needed
        Tool mcpTool = registry.get("mcp").orElseThrow();
        assertTrue(mcpTool instanceof ToolMetadata);
        assertTrue(((ToolMetadata) mcpTool).getCategory().startsWith("mcp:"));
    }

    // ==================== Test Helpers ====================

    /**
     * Simulates a local tool
     */
    private static class FakeLocalTool implements Tool {
        private final String name;
        private final String description;

        FakeLocalTool(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Override public String getName() { return name; }
        @Override public String getDescription() { return description; }
        @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
        @Override public ToolResult execute(Map<String, Object> args) {
            return ToolResult.success("local:" + name + ":executed");
        }
    }

    /**
     * Simulates what McpToolWrapper does (implements Tool + ToolMetadata)
     * In real usage, McpToolWrapper wraps mcpClient.callTool() in execute()
     */
    private static class FakeMcpTool implements Tool, ToolMetadata {
        private final String name;
        private final String description;

        FakeMcpTool(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Override public String getName() { return name; }
        @Override public String getDescription() { return description; }
        @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
        @Override public ToolResult execute(Map<String, Object> args) {
            // Real McpToolWrapper: mcpClient.callTool(new CallToolRequest(name, args))
            return ToolResult.success("mcp:" + name + ":executed");
        }

        @Override public String getCategory() { return "mcp:test-server"; }
        @Override public List<String> getTags() { return Arrays.asList("mcp", "remote"); }
        @Override public String getAuthor() { return "mcp-server:test-server"; }
    }
}
