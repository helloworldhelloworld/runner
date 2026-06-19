package com.lightweightai.web.gateway;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.mcp.McpToolClient;
import com.lightweightai.mcp.McpToolWrapper;
import com.lightweightai.web.config.McpConfig.McpToolRegistrar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("McpToolController - REST endpoints for tool management")
class McpToolControllerTest {

    private ToolRegistry toolRegistry;
    private McpToolController controller;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry();
    }

    private Tool stubTool(String name, String description) {
        Tool tool = mock(Tool.class);
        when(tool.getName()).thenReturn(name);
        when(tool.getDescription()).thenReturn(description);
        when(tool.getSchema()).thenReturn(ToolSchema.empty());
        return tool;
    }

    // ==================== listTools ====================

    @Nested
    @DisplayName("GET /gateway/tools")
    class ListTools {

        @Test
        @DisplayName("returns empty list when no tools registered")
        void emptyRegistry() {
            controller = new McpToolController(toolRegistry, null);

            Map<String, Object> result = controller.listTools();

            assertEquals(0, result.get("total"));
            assertEquals(0, result.get("enabled"));
            assertTrue(((List<?>) result.get("tools")).isEmpty());
        }

        @Test
        @DisplayName("lists local tools with correct metadata")
        void listsLocalTools() {
            Tool tool = stubTool("calculator", "Does math");
            toolRegistry.register(tool);
            controller = new McpToolController(toolRegistry, null);

            Map<String, Object> result = controller.listTools();

            assertEquals(1, result.get("total"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
            assertEquals(1, tools.size());
            assertEquals("calculator", tools.get(0).get("name"));
            assertEquals("Does math", tools.get(0).get("description"));
            assertEquals("local", tools.get(0).get("source"));
            assertEquals(true, tools.get(0).get("enabled"));
        }

        @Test
        @DisplayName("disabled tools show enabled=false")
        void disabledToolShowsFalse() {
            Tool tool = stubTool("disabled_tool", "Disabled");
            toolRegistry.register(tool);
            toolRegistry.disable("disabled_tool");
            controller = new McpToolController(toolRegistry, null);

            Map<String, Object> result = controller.listTools();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
            assertEquals(false, tools.get(0).get("enabled"));
            assertEquals(0, result.get("enabled"));
        }

        @Test
        @DisplayName("multiple tools are all listed")
        void multipleTools() {
            toolRegistry.register(stubTool("a", "Tool A"));
            toolRegistry.register(stubTool("b", "Tool B"));
            toolRegistry.register(stubTool("c", "Tool C"));
            controller = new McpToolController(toolRegistry, null);

            Map<String, Object> result = controller.listTools();

            assertEquals(3, result.get("total"));
        }
    }

    // ==================== listMcpServers ====================

    @Nested
    @DisplayName("GET /gateway/tools/mcp/servers")
    class ListMcpServers {

        @Test
        @DisplayName("returns disabled when no McpToolRegistrar")
        void noRegistrar() {
            controller = new McpToolController(toolRegistry, null);

            Map<String, Object> result = controller.listMcpServers();

            assertEquals(false, result.get("enabled"));
            assertTrue(((List<?>) result.get("servers")).isEmpty());
        }

        @Test
        @DisplayName("returns server info when registrar present")
        void withRegistrar() {
            McpToolClient mockClient = mock(McpToolClient.class);
            when(mockClient.getServerName()).thenReturn("test-server");

            McpToolWrapper mockWrapper = mock(McpToolWrapper.class);
            when(mockWrapper.getName()).thenReturn("remote_tool");
            when(mockWrapper.getDescription()).thenReturn("A remote tool");
            when(mockClient.getDiscoveredTools()).thenReturn(List.of(mockWrapper));

            // Register the wrapper so isEnabled works
            Tool wrapperAsTool = mock(Tool.class);
            when(wrapperAsTool.getName()).thenReturn("remote_tool");
            when(wrapperAsTool.getSchema()).thenReturn(ToolSchema.empty());
            toolRegistry.register(wrapperAsTool);

            McpToolRegistrar registrar = new McpToolRegistrar(List.of(mockClient));
            controller = new McpToolController(toolRegistry, registrar);

            Map<String, Object> result = controller.listMcpServers();

            assertEquals(true, result.get("enabled"));
            assertEquals(1, result.get("serverCount"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> servers = (List<Map<String, Object>>) result.get("servers");
            assertEquals(1, servers.size());
            assertEquals("test-server", servers.get(0).get("name"));
            assertEquals(1, servers.get(0).get("toolCount"));
        }
    }

    // ==================== getToolDetail ====================

    @Nested
    @DisplayName("GET /gateway/tools/{toolName}")
    class GetToolDetail {

        @Test
        @DisplayName("returns detail for existing tool")
        void existingTool() {
            Tool tool = stubTool("my_tool", "My description");
            toolRegistry.register(tool);
            controller = new McpToolController(toolRegistry, null);

            Map<String, Object> result = controller.getToolDetail("my_tool");

            assertEquals("my_tool", result.get("name"));
            assertEquals("My description", result.get("description"));
            assertEquals("local", result.get("source"));
            assertEquals(true, result.get("enabled"));
        }

        @Test
        @DisplayName("returns error for non-existent tool")
        void nonExistentTool() {
            controller = new McpToolController(toolRegistry, null);

            Map<String, Object> result = controller.getToolDetail("no_such_tool");

            assertTrue(result.containsKey("error"));
            assertTrue(result.get("error").toString().contains("no_such_tool"));
        }
    }

    // ==================== getToolDefinitions ====================

    @Nested
    @DisplayName("GET /gateway/tools/definitions")
    class GetToolDefinitions {

        @Test
        @DisplayName("returns empty definitions when no tools")
        void emptyDefinitions() {
            controller = new McpToolController(toolRegistry, null);

            Map<String, Object> result = controller.getToolDefinitions();

            assertEquals(0, result.get("count"));
            assertTrue(((List<?>) result.get("tools")).isEmpty());
        }

        @Test
        @DisplayName("returns LLM-format definitions for enabled tools only")
        void definitionsExcludeDisabled() {
            toolRegistry.register(stubTool("enabled_tool", "Enabled"));
            toolRegistry.register(stubTool("disabled_tool", "Disabled"));
            toolRegistry.disable("disabled_tool");
            controller = new McpToolController(toolRegistry, null);

            Map<String, Object> result = controller.getToolDefinitions();

            assertEquals(1, result.get("count"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
            assertEquals("enabled_tool", tools.get(0).get("name"));
        }

        @Test
        @DisplayName("definitions include name, description, and input_schema")
        void definitionStructure() {
            toolRegistry.register(stubTool("calc", "Calculator"));
            controller = new McpToolController(toolRegistry, null);

            Map<String, Object> result = controller.getToolDefinitions();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
            Map<String, Object> def = tools.get(0);
            assertEquals("calc", def.get("name"));
            assertEquals("Calculator", def.get("description"));
            assertNotNull(def.get("input_schema"));
        }
    }
}
