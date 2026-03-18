package com.lightweightai.web.gateway;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.mcp.McpToolWrapper;
import com.lightweightai.web.config.McpConfig.McpToolRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具管理 REST 接口
 *
 * 提供 MCP 工具查询、状态监控等功能，供前端展示和管理。
 */
@RestController
@RequestMapping("/gateway/tools")
@CrossOrigin(origins = "*")
public class McpToolController {

    private static final Logger logger = LoggerFactory.getLogger(McpToolController.class);

    private final ToolRegistry toolRegistry;
    private final McpToolRegistrar mcpToolRegistrar;

    public McpToolController(ToolRegistry toolRegistry,
                             @Autowired(required = false) McpToolRegistrar mcpToolRegistrar) {
        this.toolRegistry = toolRegistry;
        this.mcpToolRegistrar = mcpToolRegistrar;
    }

    /**
     * 列出所有已注册的工具
     *
     * 返回每个工具的名称、描述、来源（local/mcp）、Schema 等信息。
     * 前端可用于展示可用工具列表。
     */
    @GetMapping
    public Map<String, Object> listTools() {
        List<Map<String, Object>> toolList = new ArrayList<>();

        for (Tool tool : toolRegistry.getAll()) {
            Map<String, Object> toolInfo = new LinkedHashMap<>();
            toolInfo.put("name", tool.getName());
            toolInfo.put("description", tool.getDescription());
            toolInfo.put("enabled", toolRegistry.isEnabled(tool.getName()));

            if (tool instanceof McpToolWrapper mcpTool) {
                toolInfo.put("source", "mcp");
                toolInfo.put("server", mcpTool.getServerName());
            } else {
                toolInfo.put("source", "local");
            }

            if (tool instanceof ToolMetadata meta) {
                toolInfo.put("category", meta.getCategory());
                toolInfo.put("tags", meta.getTags());
                toolInfo.put("readOnly", meta.isReadOnly());
                toolInfo.put("destructive", meta.isDestructive());
                toolInfo.put("clientSide", meta.isClientSide());
            }

            if (tool.getSchema() != null) {
                toolInfo.put("input_schema", tool.getSchema().toMap());
            }

            toolList.add(toolInfo);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", toolList.size());
        result.put("enabled", toolRegistry.enabledCount());
        result.put("tools", toolList);
        return result;
    }

    /**
     * 列出 MCP 服务器及其工具
     */
    @GetMapping("/mcp/servers")
    public Map<String, Object> listMcpServers() {
        Map<String, Object> result = new LinkedHashMap<>();

        if (mcpToolRegistrar == null) {
            result.put("enabled", false);
            result.put("servers", List.of());
            return result;
        }

        result.put("enabled", true);
        result.put("serverCount", mcpToolRegistrar.getServerCount());
        result.put("totalToolCount", mcpToolRegistrar.getTotalToolCount());

        List<Map<String, Object>> servers = new ArrayList<>();
        mcpToolRegistrar.getClients().forEach(client -> {
            Map<String, Object> serverInfo = new LinkedHashMap<>();
            serverInfo.put("name", client.getServerName());

            List<Map<String, Object>> tools = new ArrayList<>();
            client.getDiscoveredTools().forEach(wrapper -> {
                Map<String, Object> toolInfo = new LinkedHashMap<>();
                toolInfo.put("name", wrapper.getName());
                toolInfo.put("description", wrapper.getDescription());
                toolInfo.put("enabled", toolRegistry.isEnabled(wrapper.getName()));
                tools.add(toolInfo);
            });
            serverInfo.put("toolCount", tools.size());
            serverInfo.put("tools", tools);
            servers.add(serverInfo);
        });

        result.put("servers", servers);
        return result;
    }

    /**
     * 获取单个工具详情
     */
    @GetMapping("/{toolName}")
    public Map<String, Object> getToolDetail(@PathVariable("toolName") String toolName) {
        return toolRegistry.get(toolName)
            .map(tool -> {
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("name", tool.getName());
                detail.put("description", tool.getDescription());
                detail.put("enabled", toolRegistry.isEnabled(tool.getName()));

                if (tool instanceof McpToolWrapper mcpTool) {
                    detail.put("source", "mcp");
                    detail.put("server", mcpTool.getServerName());
                } else {
                    detail.put("source", "local");
                }

                if (tool instanceof ToolMetadata meta) {
                    detail.put("category", meta.getCategory());
                    detail.put("tags", meta.getTags());
                    detail.put("readOnly", meta.isReadOnly());
                    detail.put("destructive", meta.isDestructive());
                    detail.put("idempotent", meta.isIdempotent());
                    detail.put("openWorld", meta.isOpenWorld());
                    detail.put("clientSide", meta.isClientSide());
                }

                if (tool.getSchema() != null) {
                    detail.put("input_schema", tool.getSchema().toMap());
                }

                return detail;
            })
            .orElse(Map.of("error", "Tool not found: " + toolName));
    }

    /**
     * 获取 LLM 工具定义（供调试用，与发送给 LLM 的格式一致）
     */
    @GetMapping("/definitions")
    public Map<String, Object> getToolDefinitions() {
        List<Map<String, Object>> definitions = toolRegistry.getToolDefinitions();
        return Map.of(
            "count", definitions.size(),
            "tools", definitions
        );
    }
}
