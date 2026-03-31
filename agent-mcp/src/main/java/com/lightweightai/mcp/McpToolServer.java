package com.lightweightai.mcp;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolRegistryListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * 将 ToolRegistry 暴露为 MCP 服务端（Async 版本）
 *
 * 使用 McpAsyncServer 支持异步工具执行和进度/日志通知转发。
 * 实现 ToolRegistryListener，自动同步 ToolRegistry 的动态变更到 MCP 客户端。
 */
public class McpToolServer implements ToolRegistryListener {

    private static final Logger logger = LoggerFactory.getLogger(McpToolServer.class);

    private final String serverName;
    private final String serverVersion;
    private final ToolRegistry toolRegistry;
    private final McpServerTransportProvider transportProvider;
    private McpAsyncServer asyncServer;
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
     * 将 ToolRegistry 中所有启用的工具暴露为 MCP 异步工具。
     * 支持进度通知（ProgressNotification）和日志通知（LoggingNotification）。
     * 启动后自动监听 ToolRegistry 变更，动态同步工具列表。
     */
    public void start() {
        if (asyncServer != null) {
            toolRegistry.removeListener(this);
            asyncServer.closeGracefully().block();
            logger.info("MCP server '{}' restarting — previous instance closed", serverName);
        }

        List<McpServerFeatures.AsyncToolSpecification> toolSpecs =
                McpToolAdapter.toAsyncMcpTools(toolRegistry);

        asyncServer = McpServer.async(transportProvider)
            .serverInfo(serverName, serverVersion)
            .capabilities(McpSchema.ServerCapabilities.builder()
                .tools(true)
                .logging()
                .build())
            .tools(toolSpecs)
            .build();

        toolRegistry.addListener(this);

        logger.info("MCP async server '{}' v{} started with {} tools",
            serverName, serverVersion, toolSpecs.size());
    }

    /**
     * 启动 MCP 服务端并阻塞当前线程
     */
    public void startAndBlock() {
        start();

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
     */
    public void addTool(Tool tool) {
        if (asyncServer != null) {
            asyncServer.addTool(McpToolAdapter.toAsyncMcpTool(tool)).block();
            notifyToolsChanged();
            logger.info("Added tool '{}' to MCP server '{}'", tool.getName(), serverName);
        }
    }

    /**
     * 动态移除工具
     */
    public void removeTool(String toolName) {
        if (asyncServer != null) {
            asyncServer.removeTool(toolName).block();
            notifyToolsChanged();
            logger.info("Removed tool '{}' from MCP server '{}'", toolName, serverName);
        }
    }

    /**
     * 通知 MCP 客户端工具列表已变更（tools/list_changed）
     */
    private void notifyToolsChanged() {
        if (asyncServer != null) {
            asyncServer.notifyToolsListChanged().subscribe(
                unused -> {},
                e -> logger.debug("No active MCP clients to notify: {}", e.getMessage())
            );
        }
    }

    // ==================== ToolRegistryListener 回调 ====================

    @Override
    public void onToolRegistered(Tool tool) {
        if (asyncServer != null) {
            asyncServer.addTool(McpToolAdapter.toAsyncMcpTool(tool)).block();
            notifyToolsChanged();
            logger.info("Auto-synced new tool '{}' to MCP server '{}'", tool.getName(), serverName);
        }
    }

    @Override
    public void onToolUnregistered(String toolName) {
        if (asyncServer != null) {
            asyncServer.removeTool(toolName).block();
            notifyToolsChanged();
            logger.info("Auto-removed tool '{}' from MCP server '{}'", toolName, serverName);
        }
    }

    @Override
    public void onToolEnabled(String toolName) {
        if (asyncServer != null) {
            toolRegistry.get(toolName).ifPresent(tool -> {
                asyncServer.addTool(McpToolAdapter.toAsyncMcpTool(tool)).block();
                notifyToolsChanged();
                logger.info("Re-enabled tool '{}' on MCP server '{}'", toolName, serverName);
            });
        }
    }

    @Override
    public void onToolDisabled(String toolName) {
        if (asyncServer != null) {
            asyncServer.removeTool(toolName).block();
            notifyToolsChanged();
            logger.info("Disabled tool '{}' on MCP server '{}'", toolName, serverName);
        }
    }

    /**
     * 关闭 MCP 服务端
     */
    public void close() {
        toolRegistry.removeListener(this);
        if (asyncServer != null) {
            asyncServer.closeGracefully().block();
            logger.info("MCP server '{}' closed", serverName);
        }
        shutdownLatch.countDown();
    }

    /**
     * 获取底层 MCP 异步服务端实例
     */
    public McpAsyncServer getAsyncServer() {
        return asyncServer;
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

        public Builder transportProvider(McpServerTransportProvider transportProvider) {
            this.transportProvider = transportProvider;
            return this;
        }

        public McpToolServer build() {
            if (toolRegistry == null) {
                throw new IllegalStateException("ToolRegistry is required");
            }
            if (transportProvider == null) {
                transportProvider = new StdioServerTransportProvider(
                    new JacksonMcpJsonMapper(new ObjectMapper()));
            }
            return new McpToolServer(this);
        }
    }
}
