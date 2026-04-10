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

@DisplayName("McpToolController - MCP 工具管理 REST 接口")
class McpToolControllerTest {

    private ToolRegistry toolRegistry;
    private McpToolController controller;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry();
        controller = new McpToolController(toolRegistry, null);
    }

    // ==================== 列出工具 ====================

    @Nested
    @DisplayName("列出所有工具")
    class ListTools {

        @Test
        @DisplayName("空注册表返回空列表")
        void shouldReturnEmptyWhenNoTools() {
            Map<String, Object> result = controller.listTools();

            assertEquals(0, result.get("total"));
            assertEquals(0, result.get("enabled"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
            assertTrue(tools.isEmpty());
        }

        @Test
        @DisplayName("注册工具后返回正确信息")
        void shouldListRegisteredTools() {
            toolRegistry.register(createTool("calculator", "数学计算"));
            toolRegistry.register(createTool("weather", "天气查询"));

            Map<String, Object> result = controller.listTools();

            assertEquals(2, result.get("total"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
            assertEquals(2, tools.size());
        }

        @Test
        @DisplayName("工具信息包含 name 和 description")
        void shouldIncludeToolDetails() {
            toolRegistry.register(createTool("calculator", "数学计算"));

            Map<String, Object> result = controller.listTools();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
            Map<String, Object> tool = tools.get(0);

            assertEquals("calculator", tool.get("name"));
            assertEquals("数学计算", tool.get("description"));
            assertEquals("local", tool.get("source"));
        }

        @Test
        @DisplayName("ToolMetadata 信息包含在结果中")
        void shouldIncludeMetadataInfo() {
            toolRegistry.register(new MetadataTool("gps", "位置"));

            Map<String, Object> result = controller.listTools();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
            Map<String, Object> tool = tools.get(0);

            assertEquals("location", tool.get("category"));
            assertTrue(tool.get("clientSide") instanceof Boolean);
        }
    }

    // ==================== MCP 服务器 ====================

    @Nested
    @DisplayName("MCP 服务器列表")
    class McpServers {

        @Test
        @DisplayName("无 MCP registrar 返回禁用状态")
        void shouldReturnDisabledWhenNoRegistrar() {
            Map<String, Object> result = controller.listMcpServers();

            assertEquals(false, result.get("enabled"));
            @SuppressWarnings("unchecked")
            List<?> servers = (List<?>) result.get("servers");
            assertTrue(servers.isEmpty());
        }
    }

    // ==================== 工具详情 ====================

    @Nested
    @DisplayName("工具详情")
    class ToolDetail {

        @Test
        @DisplayName("存在的工具返回详情")
        void shouldReturnToolDetail() {
            toolRegistry.register(createTool("calculator", "数学计算"));

            Map<String, Object> detail = controller.getToolDetail("calculator");

            assertEquals("calculator", detail.get("name"));
            assertEquals("数学计算", detail.get("description"));
            assertEquals("local", detail.get("source"));
        }

        @Test
        @DisplayName("不存在的工具返回错误信息")
        void shouldReturnErrorForMissingTool() {
            Map<String, Object> detail = controller.getToolDetail("nonexistent");

            assertTrue(detail.containsKey("error"));
            assertTrue(detail.get("error").toString().contains("nonexistent"));
        }
    }

    // ==================== 工具定义 ====================

    @Test
    @DisplayName("获取 LLM 工具定义")
    void shouldReturnToolDefinitions() {
        toolRegistry.register(createTool("calculator", "数学计算"));

        Map<String, Object> result = controller.getToolDefinitions();

        assertNotNull(result.get("count"));
        assertNotNull(result.get("tools"));
    }

    // ==================== 辅助方法 ====================

    private static Tool createTool(String name, String description) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return description; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("ok");
            }
        };
    }

    /** 实现 Tool + ToolMetadata 的具名内部类 */
    private static class MetadataTool implements Tool, ToolMetadata {
        private final String name;
        private final String description;

        MetadataTool(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Override public String getName() { return name; }
        @Override public String getDescription() { return description; }
        @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
        @Override public ToolResult execute(Map<String, Object> args) {
            return ToolResult.success("ok");
        }
        @Override public String getCategory() { return "location"; }
        @Override public boolean isClientSide() { return true; }
        @Override public List<String> getTags() { return List.of("client", "gps"); }
    }
}
