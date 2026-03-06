package com.lightweightai.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.core.ToolExecutor;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一工具客户端 — 本地工具、MCP 工具、客户端工具无感切换
 *
 * 面向应用的统一入口：调用方只看到 ToolExecutor/ToolRegistry，
 * 不需要关心某个工具是本地执行、MCP 远程执行、还是派发到客户端执行。
 *
 * <pre>
 * // 配置驱动
 * ToolClient client = ToolClient.create()
 *     .registerLocal(new MathTools())              // 本地 @ToolFunction
 *     .fromConfig(config)                          // MCP 工具自动 connect → discover → register
 *     .build();
 *
 * // 使用：调用方完全不需要知道工具来源
 * ToolExecutor executor = client.getToolExecutor();
 * executor.executeToolCall(new ToolCall("1", "add", args));           // → 本地执行
 * executor.executeToolCall(new ToolCall("2", "get_forecast", args));  // → MCP 远程执行
 * // 客户端工具通过 isClientSide() 标记，由 ToolExecutor + ToolExecutionContext 路由到端
 *
 * // 清理
 * client.close();
 * </pre>
 */
public class ToolClient implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ToolClient.class);

    private final ToolRegistry registry;
    private final ToolExecutor toolExecutor;
    private final List<McpToolClient> mcpClients;

    private ToolClient(Builder builder) {
        this.registry = builder.registry;
        this.toolExecutor = new ToolExecutor(registry);
        this.mcpClients = builder.mcpClients;
    }

    /**
     * 获取统一的 ToolExecutor
     *
     * 通过此 executor 调用任何工具，不区分本地/MCP/客户端
     */
    public ToolExecutor getToolExecutor() {
        return toolExecutor;
    }

    /**
     * 获取底层 ToolRegistry
     */
    public ToolRegistry getToolRegistry() {
        return registry;
    }

    /**
     * 获取所有已连接的 MCP 客户端
     */
    public List<McpToolClient> getMcpClients() {
        return List.copyOf(mcpClients);
    }

    /**
     * 运行时动态添加 MCP 服务端
     */
    public McpToolClient addMcpServer(String name, McpClientTransport transport) {
        return addMcpServer(name, transport, Duration.ofSeconds(30));
    }

    /**
     * 运行时动态添加 MCP 服务端
     */
    public McpToolClient addMcpServer(String name, McpClientTransport transport, Duration timeout) {
        McpToolClient client = McpToolClient.builder()
            .serverName(name)
            .transport(transport)
            .requestTimeout(timeout)
            .build();
        client.initialize();
        client.registerTools(registry);
        mcpClients.add(client);

        logger.info("Dynamically added MCP server '{}', total tools: {}", name, registry.enabledCount());
        return client;
    }

    /**
     * 运行时动态注册本地工具
     */
    public void registerLocal(Tool tool) {
        registry.register(tool);
    }

    /**
     * 运行时动态注册本地注解工具
     */
    public int registerLocal(Object annotated) {
        return registry.registerObject(annotated);
    }

    @Override
    public void close() {
        for (McpToolClient client : mcpClients) {
            try {
                client.close();
            } catch (Exception e) {
                logger.warn("Failed to close MCP client '{}': {}", client.getServerName(), e.getMessage());
            }
        }
        mcpClients.clear();
        logger.info("ToolClient closed, all MCP connections released");
    }

    @Override
    public String toString() {
        return String.format("ToolClient{tools=%d, mcpServers=%d}",
            registry.enabledCount(), mcpClients.size());
    }

    public static Builder create() {
        return new Builder();
    }

    // ==================== Builder ====================

    public static class Builder {
        private final ToolRegistry registry = new ToolRegistry();
        private final List<McpToolClient> mcpClients = new ArrayList<>();
        private boolean autoScan = false;
        private McpHeaderProvider headerProvider;

        /**
         * 注册本地注解工具（@ToolFunction）
         */
        public Builder registerLocal(Object annotated) {
            registry.registerObject(annotated);
            return this;
        }

        /**
         * 注册本地 Tool 接口实现
         */
        public Builder registerLocal(Tool tool) {
            registry.register(tool);
            return this;
        }

        /**
         * 批量注册本地 Tool
         */
        public Builder registerLocal(Collection<Tool> tools) {
            registry.registerAll(tools);
            return this;
        }

        /**
         * 设置全局 Header 提供者
         *
         * 每次 MCP HTTP 请求发送前调用，动态添加 Header。
         * 与 ServerConfig.headers 静态配置合并（动态优先）。
         *
         * <pre>
         * ToolClient.create()
         *     .headerProvider(() -> Map.of(
         *         "Authorization", "Bearer " + tokenService.getToken()))
         *     .fromConfig(config)
         *     .build();
         * </pre>
         */
        public Builder headerProvider(McpHeaderProvider headerProvider) {
            this.headerProvider = headerProvider;
            return this;
        }

        /**
         * 启用 SPI 自动扫描
         */
        public Builder autoScan() {
            this.autoScan = true;
            return this;
        }

        /**
         * 从 McpConfiguration 批量添加 MCP 服务端
         *
         * <pre>
         * McpConfiguration config = loadFromYaml("mcp-config.yaml");
         * ToolClient client = ToolClient.create()
         *     .fromConfig(config)
         *     .build();
         * </pre>
         */
        public Builder fromConfig(McpConfiguration config) {
            if (config == null || config.getServers() == null) {
                return this;
            }

            for (Map.Entry<String, McpConfiguration.ServerConfig> entry : config.getEnabledServers().entrySet()) {
                String name = entry.getKey();
                McpConfiguration.ServerConfig serverConfig = entry.getValue();

                McpClientTransport transport = createTransport(name, serverConfig, headerProvider);
                Duration timeout = Duration.ofSeconds(serverConfig.getTimeoutSeconds());
                addMcpServer(name, transport, timeout);

                logger.info("Added MCP server '{}' from config (transport={})", name, serverConfig.getTransport());
            }
            return this;
        }

        /**
         * 合并静态配置 headers 和动态 headerProvider
         *
         * 动态 provider 的值优先于静态配置（同名 key 覆盖）。
         */
        static Map<String, String> resolveHeaders(McpConfiguration.ServerConfig config,
                                                    McpHeaderProvider headerProvider) {
            Map<String, String> merged = new HashMap<>();
            // 静态配置
            if (config.getHeaders() != null && !config.getHeaders().isEmpty()) {
                merged.putAll(config.getHeaders());
            }
            // 动态 provider 覆盖
            if (headerProvider != null) {
                Map<String, String> dynamic = headerProvider.getHeaders();
                if (dynamic != null && !dynamic.isEmpty()) {
                    merged.putAll(dynamic);
                }
            }
            return merged;
        }

        static McpClientTransport createTransport(String name,
                                                    McpConfiguration.ServerConfig config,
                                                    McpHeaderProvider headerProvider) {
            String transport = config.getTransport();
            Map<String, String> headers = resolveHeaders(config, headerProvider);

            if ("streamable_http".equalsIgnoreCase(transport)
                    || "streamable-http".equalsIgnoreCase(transport)
                    || "http".equalsIgnoreCase(transport)) {
                if (config.getUrl() == null || config.getUrl().isBlank()) {
                    throw new IllegalStateException(
                        "MCP server '" + name + "': Streamable HTTP transport requires 'url'");
                }
                java.net.URI uri = java.net.URI.create(config.getUrl());
                String baseUrl = uri.getScheme() + "://" + uri.getAuthority();
                String endpoint = uri.getRawPath();
                if (endpoint == null || endpoint.isEmpty()) {
                    endpoint = "/mcp";
                }
                var builder = HttpClientStreamableHttpTransport.builder(baseUrl)
                    .endpoint(endpoint);
                if (!headers.isEmpty()) {
                    builder.customizeRequest(reqBuilder -> {
                        headers.forEach(reqBuilder::header);
                    });
                }
                return builder.build();
            }

            if ("sse".equalsIgnoreCase(transport)) {
                if (config.getUrl() == null || config.getUrl().isBlank()) {
                    throw new IllegalStateException(
                        "MCP server '" + name + "': SSE transport requires 'url'");
                }
                var sseBuilder = HttpClientSseClientTransport.builder(config.getUrl())
                    .sseEndpoint("/sse");
                if (!headers.isEmpty()) {
                    java.net.http.HttpRequest.Builder reqBuilder = java.net.http.HttpRequest.newBuilder();
                    headers.forEach(reqBuilder::header);
                    sseBuilder.requestBuilder(reqBuilder);
                }
                return sseBuilder.build();
            }

            if (config.getCommand() == null || config.getCommand().isBlank()) {
                throw new IllegalStateException(
                    "MCP server '" + name + "': STDIO transport requires 'command'");
            }

            ServerParameters.Builder paramsBuilder = ServerParameters.builder(config.getCommand());
            if (config.getArgs() != null && !config.getArgs().isEmpty()) {
                paramsBuilder.args(config.getArgs().toArray(new String[0]));
            }
            if (config.getEnv() != null && !config.getEnv().isEmpty()) {
                paramsBuilder.env(config.getEnv());
            }
            return new StdioClientTransport(paramsBuilder.build(),
                new JacksonMcpJsonMapper(new ObjectMapper()));
        }

        public Builder addMcpServer(String name, McpClientTransport transport) {
            return addMcpServer(name, transport, Duration.ofSeconds(30));
        }

        public Builder addMcpServer(String name, McpClientTransport transport, Duration timeout) {
            McpToolClient client = McpToolClient.builder()
                .serverName(name)
                .transport(transport)
                .requestTimeout(timeout)
                .build();
            client.initialize();
            client.registerTools(registry);
            mcpClients.add(client);
            return this;
        }

        public Builder addMcpClient(McpToolClient client) {
            client.registerTools(registry);
            mcpClients.add(client);
            return this;
        }

        public ToolClient build() {
            if (autoScan) {
                registry.scanAndRegister();
            }
            return new ToolClient(this);
        }
    }
}
