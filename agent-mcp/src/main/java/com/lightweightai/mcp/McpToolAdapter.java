package com.lightweightai.mcp;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.llm.ToolResult;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 将框架的 Tool 适配为 MCP SyncToolSpecification
 *
 * 用于将 ToolRegistry 中的工具暴露为 MCP 服务端工具，
 * 使外部 MCP 客户端可以发现并调用这些工具。
 *
 * <pre>
 * // 将单个 Tool 转换为 MCP 工具规格
 * McpServerFeatures.SyncToolSpecification spec = McpToolAdapter.toMcpTool(myTool);
 *
 * // 将整个 ToolRegistry 转换
 * List&lt;McpServerFeatures.SyncToolSpecification&gt; specs = McpToolAdapter.toMcpTools(registry);
 * </pre>
 */
public class McpToolAdapter {

    private static final Logger logger = LoggerFactory.getLogger(McpToolAdapter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private McpToolAdapter() {
        // Utility class
    }

    /**
     * 将框架 Tool 转换为 MCP SyncToolSpecification
     *
     * @param tool 框架 Tool 实例
     * @return MCP 工具规格
     */
    public static McpServerFeatures.SyncToolSpecification toMcpTool(Tool tool) {
        // Convert ToolSchema to JSON string for MCP
        String inputSchema = schemaToJson(tool);

        McpSchema.Tool mcpTool = new McpSchema.Tool(
            tool.getName(),
            tool.getDescription(),
            inputSchema
        );

        return new McpServerFeatures.SyncToolSpecification(
            mcpTool,
            (exchange, args) -> {
                try {
                    ToolResult result = tool.execute(args);
                    return new CallToolResult(
                        List.of(new TextContent(result.getContent())),
                        result.isError()
                    );
                } catch (Exception e) {
                    logger.error("Tool execution failed: {} - {}", tool.getName(), e.getMessage());
                    return new CallToolResult(
                        List.of(new TextContent("Error: " + e.getMessage())),
                        true
                    );
                }
            }
        );
    }

    /**
     * 将 ToolRegistry 中所有启用的工具转换为 MCP 工具规格列表
     *
     * @param registry 工具注册表
     * @return MCP 工具规格列表
     */
    public static List<McpServerFeatures.SyncToolSpecification> toMcpTools(ToolRegistry registry) {
        return registry.getEnabled().stream()
            .map(McpToolAdapter::toMcpTool)
            .collect(Collectors.toList());
    }

    /**
     * 将 ToolSchema 转换为 JSON 字符串（MCP 要求的格式）
     */
    private static String schemaToJson(Tool tool) {
        try {
            return objectMapper.writeValueAsString(tool.getSchema().toMap());
        } catch (JsonProcessingException e) {
            logger.warn("Failed to serialize schema for tool {}, using empty schema", tool.getName());
            return "{\"type\":\"object\",\"properties\":{}}";
        }
    }
}
