package com.lightweightai.mcp;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSource;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * MCP 工具客户端（Reactive 版本）
 *
 * 基于 McpAsyncClient，支持 ProgressNotification 和 LoggingNotification 路由。
 * 实现 ToolSource 接口，可直接作为 ToolRegistry 的工具来源。
 */
public class McpToolClient implements ToolSource, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(McpToolClient.class);

    private final String serverName;
    private final McpAsyncClient asyncClient;
    private final ProgressNotificationRouter progressRouter;
    private final LoggingNotificationRouter loggingRouter;
    private List<McpToolWrapper> discoveredTools;

    private McpToolClient(Builder builder) {
        this.serverName = builder.serverName;
        this.progressRouter = new ProgressNotificationRouter();
        this.loggingRouter = new LoggingNotificationRouter();

        this.asyncClient = McpClient.async(builder.transport)
            .requestTimeout(builder.timeout)
            .progressConsumer(notification -> {
                progressRouter.route(notification);
                return Mono.empty();
            })
            .loggingConsumer(notification -> {
                loggingRouter.route(notification);
                return Mono.empty();
            })
            .build();
    }

    /**
     * 初始化连接并执行 MCP 握手
     */
    public McpToolClient initialize() {
        asyncClient.initialize().block();
        logger.info("MCP client connected to server '{}'", serverName);
        return this;
    }

    // ==================== ToolSource 接口 ====================

    @Override
    public List<Tool> discoverTools() {
        return new ArrayList<>(discoverMcpTools());
    }

    // ==================== MCP 工具发现 ====================

    public List<McpToolWrapper> discoverMcpTools() {
        McpSchema.ListToolsResult result = asyncClient.listTools().block();
        discoveredTools = new ArrayList<>();

        if (result != null && result.tools() != null) {
            for (McpSchema.Tool mcpTool : result.tools()) {
                McpToolWrapper wrapper = new McpToolWrapper(this, mcpTool, serverName);
                discoveredTools.add(wrapper);
                logger.debug("Discovered MCP tool: {} from server '{}'", mcpTool.name(), serverName);
            }
        }

        logger.info("Discovered {} tools from MCP server '{}'",
                discoveredTools.size(), serverName);
        return discoveredTools;
    }

    public List<McpToolWrapper> discoverMcpTools(Predicate<Tool> filter) {
        return discoverMcpTools().stream()
            .filter(filter)
            .toList();
    }

    public int registerTools(ToolRegistry registry) {
        return registerTools(registry, tool -> true);
    }

    public int registerTools(ToolRegistry registry, Predicate<Tool> filter) {
        List<McpToolWrapper> tools = discoverMcpTools(filter);
        for (McpToolWrapper tool : tools) {
            registry.register(tool);
        }
        logger.info("Registered {} MCP tools from '{}' into ToolRegistry", tools.size(), serverName);
        return tools.size();
    }

    // ==================== Reactive 工具调用 ====================

    /**
     * Reactive 工具调用（供 McpToolWrapper 使用）
     *
     * @param name          工具名
     * @param args          参数
     * @param progressToken 进度 token（传入 _meta.progressToken）
     * @return Mono of CallToolResult
     */
    public Mono<McpSchema.CallToolResult> callToolReactive(
            String name, Map<String, Object> args, String progressToken) {
        Map<String, Object> meta = progressToken != null
            ? Map.of("progressToken", progressToken)
            : Map.of();
        return asyncClient.callTool(new McpSchema.CallToolRequest(name, args, meta));
    }

    // ==================== Router 访问 ====================

    public ProgressNotificationRouter getProgressRouter() {
        return progressRouter;
    }

    public LoggingNotificationRouter getLoggingRouter() {
        return loggingRouter;
    }

    // ==================== 其他方法 ====================

    public List<McpToolWrapper> getDiscoveredTools() {
        return discoveredTools != null ? discoveredTools : List.of();
    }

    public String getServerName() {
        return serverName;
    }

    public McpAsyncClient getAsyncClient() {
        return asyncClient;
    }

    @Override
    public void close() {
        asyncClient.close();
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

        public Builder transport(McpClientTransport transport) {
            this.transport = transport;
            return this;
        }

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
