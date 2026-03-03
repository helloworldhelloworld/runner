package com.lightweightai.mcp;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSource;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * MCP 工具客户端
 *
 * 实现 ToolSource 接口，可直接作为 ToolRegistry 的工具来源。
 * 连接到外部 MCP 服务端，发现并导入其工具到框架的 ToolRegistry。
 * 导入后的工具可以像本地工具一样被 LLM 调用。
 *
 * <pre>
 * // 方式一：通过 ToolSource 注册（推荐）
 * McpToolClient client = McpToolClient.builder()
 *     .serverName("weather-server")
 *     .transport(new StdioClientTransport(serverParams))
 *     .build();
 * client.initialize();
 * registry.registerFrom(client);
 *
 * // 方式二：手动注册
 * List&lt;Tool&gt; tools = client.discoverTools();
 * client.registerTools(registry);
 *
 * // 使用完毕后关闭
 * client.close();
 * </pre>
 */
public class McpToolClient implements ToolSource, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(McpToolClient.class);

    private final String serverName;
    private final McpSyncClient mcpClient;
    private List<McpToolWrapper> discoveredTools;

    private McpToolClient(Builder builder) {
        this.serverName = builder.serverName;
        this.mcpClient = McpClient.sync(builder.transport)
            .requestTimeout(builder.timeout)
            .build();
    }

    /**
     * 初始化连接并执行 MCP 握手
     */
    public McpToolClient initialize() {
        mcpClient.initialize();
        logger.info("MCP client connected to server '{}'", serverName);
        return this;
    }

    // ==================== ToolSource 接口 ====================

    /**
     * 实现 ToolSource 接口：发现 MCP 服务端上的所有工具
     *
     * 返回的工具可以直接注册到 ToolRegistry：
     * <pre>registry.registerFrom(mcpClient);</pre>
     *
     * @return 包装为框架 Tool 的工具列表
     */
    @Override
    public List<Tool> discoverTools() {
        return new ArrayList<>(discoverMcpTools());
    }

    // ==================== MCP 工具发现 ====================

    /**
     * 发现 MCP 服务端上的所有工具（带类型信息）
     *
     * @return McpToolWrapper 列表（可访问 MCP 特有属性）
     */
    public List<McpToolWrapper> discoverMcpTools() {
        McpSchema.ListToolsResult result = mcpClient.listTools();
        discoveredTools = new ArrayList<>();

        if (result.tools() != null) {
            for (McpSchema.Tool mcpTool : result.tools()) {
                McpToolWrapper wrapper = new McpToolWrapper(mcpClient, mcpTool, serverName);
                discoveredTools.add(wrapper);
                logger.debug("Discovered MCP tool: {} from server '{}'", mcpTool.name(), serverName);
            }
        }

        logger.info("Discovered {} tools from MCP server '{}'", discoveredTools.size(), serverName);
        return discoveredTools;
    }

    /**
     * 发现并按条件过滤工具
     *
     * @param filter 过滤条件
     * @return 满足条件的工具列表
     */
    public List<McpToolWrapper> discoverMcpTools(Predicate<Tool> filter) {
        return discoverMcpTools().stream()
            .filter(filter)
            .toList();
    }

    /**
     * 发现工具并注册到 ToolRegistry
     *
     * @param registry 目标注册表
     * @return 注册的工具数量
     */
    public int registerTools(ToolRegistry registry) {
        return registerTools(registry, tool -> true);
    }

    /**
     * 发现工具并按条件过滤后注册到 ToolRegistry
     *
     * @param registry 目标注册表
     * @param filter   过滤条件
     * @return 注册的工具数量
     */
    public int registerTools(ToolRegistry registry, Predicate<Tool> filter) {
        List<McpToolWrapper> tools = discoverMcpTools(filter);
        for (McpToolWrapper tool : tools) {
            registry.register(tool);
        }
        logger.info("Registered {} MCP tools from '{}' into ToolRegistry", tools.size(), serverName);
        return tools.size();
    }

    /**
     * 获取已发现的工具列表
     */
    public List<McpToolWrapper> getDiscoveredTools() {
        return discoveredTools != null ? discoveredTools : List.of();
    }

    /**
     * 获取 MCP 服务端名称
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * 获取底层 MCP 同步客户端
     */
    public McpSyncClient getMcpClient() {
        return mcpClient;
    }

    @Override
    public void close() {
        mcpClient.close();
        logger.info("MCP client for server '{}' closed", serverName);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String serverName = "mcp-server";
        private McpClientTransport transport;
        private Duration timeout = Duration.ofSeconds(30);

        public Builder serverName(String serverName) {
            this.serverName = serverName;
            return this;
        }

        /**
         * 设置 MCP 传输层
         *
         * 支持 STDIO (StdioClientTransport)、SSE (HttpClientSseTransport) 等
         */
        public Builder transport(McpClientTransport transport) {
            this.transport = transport;
            return this;
        }

        /**
         * 设置请求超时时间
         */
        public Builder requestTimeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public McpToolClient build() {
            if (transport == null) {
                throw new IllegalStateException("Transport is required");
            }
            return new McpToolClient(this);
        }
    }
}
