package com.lightweightai.web.gateway;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("McpToolController — tool listing and detail REST API")
class McpToolControllerTest {

    private ToolRegistry toolRegistry;
    private McpToolController controller;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry();
        controller = new McpToolController(toolRegistry, null);
    }

    private Tool simpleTool(String name, String desc) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return desc; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) { return ToolResult.success("ok"); }
        };
    }

    @Nested
    @DisplayName("GET /gateway/tools — listTools")
    class ListTools {

        @Test
        void emptyRegistryReturnsZeroTools() {
            Map<String, Object> result = controller.listTools();
            assertEquals(0, result.get("total"));
            @SuppressWarnings("unchecked")
            List<?> tools = (List<?>) result.get("tools");
            assertTrue(tools.isEmpty());
        }

        @Test
        void registeredToolsAppearInList() {
            toolRegistry.register(simpleTool("weather", "Get weather"));
            toolRegistry.register(simpleTool("math", "Do math"));

            Map<String, Object> result = controller.listTools();
            assertEquals(2, result.get("total"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
            assertEquals(2, tools.size());

            Map<String, Object> first = tools.stream()
                .filter(t -> "weather".equals(t.get("name")))
                .findFirst().orElseThrow();
            assertEquals("Get weather", first.get("description"));
            assertEquals("local", first.get("source"));
            assertEquals(true, first.get("enabled"));
        }

        @Test
        void includesEnabledCount() {
            toolRegistry.register(simpleTool("t1", "d1"));
            toolRegistry.register(simpleTool("t2", "d2"));
            toolRegistry.disable("t2");

            Map<String, Object> result = controller.listTools();
            assertEquals(2, result.get("total"));
            assertEquals(1, result.get("enabled"));
        }

        @Test
        void includesSchemaWhenPresent() {
            Tool toolWithSchema = new Tool() {
                @Override public String getName() { return "calc"; }
                @Override public String getDescription() { return "Calculate"; }
                @Override public ToolSchema getSchema() {
                    return ToolSchema.withProperties(Map.of(
                        "expr", Map.of("type", "string", "description", "expression")
                    ));
                }
                @Override public ToolResult execute(Map<String, Object> args) { return ToolResult.success("ok"); }
            };

            toolRegistry.register(toolWithSchema);
            Map<String, Object> result = controller.listTools();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
            assertNotNull(tools.get(0).get("input_schema"));
        }
    }

    @Nested
    @DisplayName("GET /gateway/tools/mcp/servers — listMcpServers")
    class ListMcpServers {

        @Test
        void nullRegistrarReturnsDisabled() {
            Map<String, Object> result = controller.listMcpServers();
            assertEquals(false, result.get("enabled"));
            @SuppressWarnings("unchecked")
            List<?> servers = (List<?>) result.get("servers");
            assertTrue(servers.isEmpty());
        }
    }

    @Nested
    @DisplayName("GET /gateway/tools/{toolName} — getToolDetail")
    class GetToolDetail {

        @Test
        void existingToolReturnsDetail() {
            toolRegistry.register(simpleTool("weather", "Check weather"));

            Map<String, Object> detail = controller.getToolDetail("weather");
            assertEquals("weather", detail.get("name"));
            assertEquals("Check weather", detail.get("description"));
            assertEquals(true, detail.get("enabled"));
            assertEquals("local", detail.get("source"));
        }

        @Test
        void missingToolReturnsError() {
            Map<String, Object> detail = controller.getToolDetail("nonexistent");
            assertTrue(detail.containsKey("error"));
            assertTrue(((String) detail.get("error")).contains("nonexistent"));
        }
    }

    @Nested
    @DisplayName("GET /gateway/tools/definitions — getToolDefinitions")
    class GetToolDefinitions {

        @Test
        void returnsToolDefinitionCount() {
            toolRegistry.register(simpleTool("t1", "d1"));
            toolRegistry.register(simpleTool("t2", "d2"));

            Map<String, Object> result = controller.getToolDefinitions();
            assertEquals(2, result.get("count"));
            @SuppressWarnings("unchecked")
            List<?> tools = (List<?>) result.get("tools");
            assertEquals(2, tools.size());
        }

        @Test
        void emptyRegistryReturnsZero() {
            Map<String, Object> result = controller.getToolDefinitions();
            assertEquals(0, result.get("count"));
        }
    }
}
