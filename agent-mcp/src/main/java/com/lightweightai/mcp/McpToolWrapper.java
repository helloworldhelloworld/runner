package com.lightweightai.mcp;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolResult;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 将 MCP 远程工具包装为框架的 Tool 接口
 *
 * 从 MCP 服务端发现的工具可以通过此包装器转换为框架的 Tool 对象，
 * 注册到 ToolRegistry 后即可像本地工具一样使用。
 *
 * <pre>
 * // 从 MCP 客户端获取远程工具
 * McpSchema.ListToolsResult tools = mcpClient.listTools();
 * for (McpSchema.Tool mcpTool : tools.tools()) {
 *     Tool wrapped = new McpToolWrapper(mcpClient, mcpTool, "remote-server");
 *     registry.register(wrapped);
 * }
 * </pre>
 */
public class McpToolWrapper implements Tool, ToolMetadata {

    private static final Logger logger = LoggerFactory.getLogger(McpToolWrapper.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final McpSyncClient mcpClient;
    private final McpSchema.Tool mcpTool;
    private final String serverName;

    /**
     * @param mcpClient  MCP 同步客户端（用于调用远程工具）
     * @param mcpTool    MCP 工具定义
     * @param serverName MCP 服务端名称（用于分类和追踪）
     */
    public McpToolWrapper(McpSyncClient mcpClient, McpSchema.Tool mcpTool, String serverName) {
        this.mcpClient = mcpClient;
        this.mcpTool = mcpTool;
        this.serverName = serverName;
    }

    @Override
    public String getName() {
        return mcpTool.name();
    }

    @Override
    public String getDescription() {
        return mcpTool.description();
    }

    @Override
    public ToolSchema getSchema() {
        try {
            String schemaJson = mcpTool.inputSchema();
            if (schemaJson != null && !schemaJson.isBlank()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> schemaMap = objectMapper.readValue(schemaJson, Map.class);
                return new ToolSchema(schemaMap);
            }
        } catch (Exception e) {
            logger.warn("Failed to parse MCP tool schema for {}: {}", getName(), e.getMessage());
        }
        return ToolSchema.empty();
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        try {
            McpSchema.CallToolResult mcpResult = mcpClient.callTool(
                new McpSchema.CallToolRequest(getName(), args)
            );

            String content = extractContent(mcpResult);
            boolean isError = mcpResult.isError() != null && mcpResult.isError();

            if (isError) {
                return ToolResult.error(content);
            }
            return ToolResult.success(content);
        } catch (Exception e) {
            logger.error("MCP tool call failed: {} - {}", getName(), e.getMessage());
            return ToolResult.error("MCP call failed: " + e.getMessage());
        }
    }

    @Override
    public String getCategory() {
        return "mcp:" + serverName;
    }

    @Override
    public List<String> getTags() {
        return List.of("mcp", "remote", serverName);
    }

    @Override
    public String getAuthor() {
        return "mcp-server:" + serverName;
    }

    /**
     * 获取 MCP 服务端名称
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * 获取原始 MCP 工具定义
     */
    public McpSchema.Tool getMcpTool() {
        return mcpTool;
    }

    /**
     * 从 MCP CallToolResult 中提取文本内容
     */
    private String extractContent(McpSchema.CallToolResult result) {
        if (result.content() == null || result.content().isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (McpSchema.Content content : result.content()) {
            if (content instanceof McpSchema.TextContent textContent) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(textContent.text());
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "McpToolWrapper{name='" + getName() + "', server='" + serverName + "'}";
    }
}
