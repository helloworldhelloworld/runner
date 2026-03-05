package com.lightweightai.mcp;

import com.lightweightai.kernel.agent.ToolRegistry;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * 将 ToolRegistry 暴露为 MCP 服务端
 *
 * 使外部 MCP 客户端（Claude Desktop、Cursor、其他 AI Agent）
 * 能够发现并调用框架注册的所有工具。
 *
 * <p>使用方式：</p>
 * <pre>
 * // 使用 STDIO 传输（标准输入/输出）
 * McpToolServer server = McpToolServer.builder()
 *     .serverName("my-agent")
 *     .serverVersion("1.0.0")
 *     .toolRegistry(registry)
 *     .build();
 * server.start();
 *
 * // 使用自定义传输
 * McpToolServer server = McpToolServer.builder()
 *     .serverName("my-agent")
 *     .toolRegistry(registry)
 *     .transportProvider(myTransportProvider)
 *     .build();
 * </pre>
 */
public class McpToolServer {

    private static final Logger logger = LoggerFactory.getLogger(McpToolServer.class);

    private final String serverName;
    private final String serverVersion;
    private final ToolRegistry toolRegistry;
    private final McpServerTransportProvider transportProvider;
    private McpSyncServer syncServer;
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    private McpToolServer(Builder builder) {
        this.serverName = builder.serverName;
        this.serverVersion = builder.serverVersion;
        this.toolRegistry = builder.toolRegistry;
        this.transportProvider = builder.transportProvider;
    }

    /**
     * 启动 MCP 服务端
     *
     * 将 ToolRegistry 中所有启用的工具暴露为 MCP 工具。
     */
    public void start() {
        List<McpServerFeatures.SyncToolSpecification> toolSpecs = McpToolAdapter.toMcpTools(toolRegistry);

        syncServer = McpServer.sync(transportProvider)
            .serverInfo(serverName, serverVersion)
            .capabilities(McpSchema.ServerCapabilities.builder()
                .tools(true)
                .build())
            .tools(toolSpecs.toArray(new McpServerFeatures.SyncToolSpecification[0]))
            .build();

        logger.info("MCP server '{}' v{} started with {} tools",
            serverName, serverVersion, toolSpecs.size());
    }

    /**
     * 启动 MCP 服务端并阻塞当前线程，直到 close() 被调用
     *
     * 适用于独立进程模式（如 Claude Desktop / Cursor 通过 STDIO 连接）。
     * 进程会保持运行直到外部客户端断开或手动调用 close()。
     *
     * <pre>
     * McpToolServer server = McpToolServer.builder()
     *     .serverName("my-agent")
     *     .toolRegistry(registry)
     *     .build();
     * server.startAndBlock();  // 进程保持运行
     * </pre>
     */
    public void startAndBlock() {
        start();

        // 注册 shutdown hook，确保 Ctrl+C 时优雅关闭
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received, closing MCP server '{}'...", serverName);
            close();
        }));

        logger.info("MCP server '{}' is running. Press Ctrl+C to stop.", serverName);

        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.info("MCP server '{}' interrupted", serverName);
        }
    }

    /**
     * 动态添加工具到运行中的 MCP 服务端
     *
     * @param tool 框架 Tool 实例
     */
    public void addTool(com.lightweightai.kernel.agent.Tool tool) {
        if (syncServer != null) {
            syncServer.addTool(McpToolAdapter.toMcpTool(tool));
            logger.info("Added tool '{}' to MCP server '{}'", tool.getName(), serverName);
        }
    }

    /**
     * 关闭 MCP 服务端
     */
    public void close() {
        if (syncServer != null) {
            syncServer.close();
            logger.info("MCP server '{}' closed", serverName);
        }
        shutdownLatch.countDown();
    }

    /**
     * 获取底层 MCP 同步服务端实例
     */
    public McpSyncServer getSyncServer() {
        return syncServer;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String serverName = "lightweight-ai-agent";
        private String serverVersion = "0.1.0";
        private ToolRegistry toolRegistry;
        private McpServerTransportProvider transportProvider;

        public Builder serverName(String serverName) {
            this.serverName = serverName;
            return this;
        }

        public Builder serverVersion(String serverVersion) {
            this.serverVersion = serverVersion;
            return this;
        }

        public Builder toolRegistry(ToolRegistry toolRegistry) {
            this.toolRegistry = toolRegistry;
            return this;
        }

        /**
         * 设置自定义传输提供者
         *
         * 可选：STDIO、SSE（需要 mcp-spring-webflux 或 mcp-spring-webmvc）等
         */
        public Builder transportProvider(McpServerTransportProvider transportProvider) {
            this.transportProvider = transportProvider;
            return this;
        }

        public McpToolServer build() {
            if (toolRegistry == null) {
                throw new IllegalStateException("ToolRegistry is required");
            }
            if (transportProvider == null) {
                transportProvider = new StdioServerTransportProvider();
            }
            return new McpToolServer(this);
        }
    }
}
