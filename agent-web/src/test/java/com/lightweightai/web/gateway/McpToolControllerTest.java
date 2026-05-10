package com.lightweightai.web.gateway;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("McpToolController — tool listing, detail, and definitions endpoints")
class McpToolControllerTest {

    private ToolRegistry toolRegistry;
    private McpToolController controller;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry();
        controller = new McpToolController(toolRegistry, null);
    }

    @Nested
    @DisplayName("GET /gateway/tools — listTools")
    class ListToolsTests {

        @Test
        @DisplayName("returns empty tool list when no tools registered")
        void emptyRegistry() {
            Map<String, Object> result = controller.listTools();

            assertEquals(0, result.get("total"));
            assertEquals(0, result.get("enabled"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
            assertTrue(tools.isEmpty());
        }

        @Test
        @DisplayName("returns registered tools with name, description, source, and enabled status")
        void listsRegisteredTools() {
            toolRegistry.register(new SimpleTool("weather", "Get weather info"));
            toolRegistry.register(new SimpleTool("calculator", "Do math"));

            Map<String, Object> result = controller.listTools();

            assertEquals(2, result.get("total"));
            assertEquals(2, result.get("enabled"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
            assertTrue(tools.stream().anyMatch(t -> "weather".equals(t.get("name"))));
            assertTrue(tools.stream().anyMatch(t -> "calculator".equals(t.get("name"))));

            Map<String, Object> weatherTool = tools.stream()
                    .filter(t -> "weather".equals(t.get("name"))).findFirst().orElseThrow();
            assertEquals("local", weatherTool.get("source"));
            assertEquals(true, weatherTool.get("enabled"));
            assertEquals("Get weather info", weatherTool.get("description"));
        }

        @Test
        @DisplayName("disabled tools show enabled=false")
        void disabledToolShowsCorrectStatus() {
            toolRegistry.register(new SimpleTool("tool1", "desc"));
            toolRegistry.disable("tool1");

            Map<String, Object> result = controller.listTools();

            assertEquals(1, result.get("total"));
            assertEquals(0, result.get("enabled"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
            Map<String, Object> tool = tools.get(0);
            assertEquals(false, tool.get("enabled"));
        }

        @Test
        @DisplayName("tools implementing ToolMetadata include category, tags, readOnly")
        void metadataToolsIncludeExtraFields() {
            toolRegistry.register(new MetadataToolImpl("file_reader", "Read files",
                    "file", List.of("io"), true));

            Map<String, Object> result = controller.listTools();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
            Map<String, Object> tool = tools.get(0);

            assertEquals("file", tool.get("category"));
            assertEquals(List.of("io"), tool.get("tags"));
            assertEquals(true, tool.get("readOnly"));
        }
    }

    @Nested
    @DisplayName("GET /gateway/tools/{toolName} — getToolDetail")
    class ToolDetailTests {

        @Test
        @DisplayName("returns tool detail for existing tool")
        void existingTool() {
            toolRegistry.register(new SimpleTool("weather", "Get weather info"));

            Map<String, Object> detail = controller.getToolDetail("weather");

            assertEquals("weather", detail.get("name"));
            assertEquals("Get weather info", detail.get("description"));
            assertEquals("local", detail.get("source"));
            assertEquals(true, detail.get("enabled"));
        }

        @Test
        @DisplayName("returns error map for non-existent tool")
        void nonExistentTool() {
            Map<String, Object> detail = controller.getToolDetail("nonexistent");

            assertTrue(detail.containsKey("error"));
            assertTrue(((String) detail.get("error")).contains("nonexistent"));
        }
    }

    @Nested
    @DisplayName("GET /gateway/tools/definitions — getToolDefinitions")
    class ToolDefinitionsTests {

        @Test
        @DisplayName("returns LLM tool definitions for enabled tools only")
        void returnsDefinitionsForEnabledOnly() {
            toolRegistry.register(new SimpleTool("enabled_tool", "I am enabled"));
            toolRegistry.register(new SimpleTool("disabled_tool", "I am disabled"));
            toolRegistry.disable("disabled_tool");

            Map<String, Object> result = controller.getToolDefinitions();

            assertEquals(1, result.get("count"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> defs = (List<Map<String, Object>>) result.get("tools");
            assertEquals(1, defs.size());
            assertEquals("enabled_tool", defs.get(0).get("name"));
            assertNotNull(defs.get(0).get("input_schema"),
                    "Tool definition must include input_schema for LLM");
        }
    }

    @Nested
    @DisplayName("GET /gateway/tools/mcp/servers — listMcpServers")
    class McpServerTests {

        @Test
        @DisplayName("returns enabled=false when no McpToolRegistrar configured")
        void noMcpRegistrar() {
            Map<String, Object> result = controller.listMcpServers();

            assertEquals(false, result.get("enabled"));
            @SuppressWarnings("unchecked")
            List<?> servers = (List<?>) result.get("servers");
            assertTrue(servers.isEmpty());
        }
    }

    // ==================== Test fixtures ====================

    private static class SimpleTool implements Tool {
        private final String name;
        private final String description;

        SimpleTool(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Override public String getName() { return name; }
        @Override public String getDescription() { return description; }
        @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
        @Override public ToolResult execute(Map<String, Object> args) {
            return ToolResult.success("ok");
        }
    }

    private static class MetadataToolImpl implements Tool, ToolMetadata {
        private final String name;
        private final String description;
        private final String category;
        private final List<String> tags;
        private final boolean readOnly;

        MetadataToolImpl(String name, String description, String category,
                         List<String> tags, boolean readOnly) {
            this.name = name;
            this.description = description;
            this.category = category;
            this.tags = tags;
            this.readOnly = readOnly;
        }

        @Override public String getName() { return name; }
        @Override public String getDescription() { return description; }
        @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
        @Override public ToolResult execute(Map<String, Object> args) { return ToolResult.success("ok"); }
        @Override public String getCategory() { return category; }
        @Override public List<String> getTags() { return tags; }
        @Override public boolean isReadOnly() { return readOnly; }
    }
}
