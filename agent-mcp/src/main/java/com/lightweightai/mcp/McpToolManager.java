package com.lightweightai.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.core.ToolExecutor;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 统一工具管理器 — MCP 工具与本地工具无感切换
 *
 * 核心设计：调用方只看到 ToolExecutor/ToolRegistry，不知道某个工具是本地的还是 MCP 的。
 * MCP 连接生命周期由 McpToolManager 自动管理。
 *
 * <pre>
 * // 方式一：配置驱动（推荐）
 * McpConfiguration config = new McpConfiguration();
 * config.addServer("weather", McpConfiguration.ServerConfig.stdio("npx", "-y", "@weather/mcp-server"));
 * config.addServer("filesystem", McpConfiguration.ServerConfig.stdio("npx", "-y", "@mcp/server-filesystem", "/tmp"));
 *
 * McpToolManager manager = McpToolManager.create()
 *     .fromConfig(config)                        // 自动 connect → discover → register
 *     .registerLocal(new MathTools())             // 本地工具
 *     .build();
 *
 * // 方式二：程序化注册
 * McpToolManager manager = McpToolManager.create()
 *     .registerLocal(new MathTools())            // 本地 @ToolFunction
 *     .registerLocal(new RandomNumberTool())     // 本地 implements Tool
 *     .addMcpServer("weather",                   // MCP: 自动 connect → discover → register
 *         new StdioClientTransport(weatherParams))
 *     .addMcpServer("filesystem",
 *         new StdioClientTransport(fsParams))
 *     .build();
 *
 * // 使用：调用方完全不需要知道工具来源
 * ToolExecutor executor = manager.getToolExecutor();
 * executor.executeToolCall(new ToolCall("1", "add", args));         // → 本地执行
 * executor.executeToolCall(new ToolCall("2", "get_forecast", args)); // → 自动走 MCP
 * executor.executeToolCall(new ToolCall("3", "read_file", args));   // → 自动走 MCP
 *
 * // ToolCallingLoop 也完全透明
 * ToolCallingLoop loop = ToolCallingLoop.builder()
 *     .provider(llm)
 *     .toolExecutor(executor)
 *     .build();
 * loop.executeWithTools(messages, options);  // LLM 调用任何工具，框架自动路由
 *
 * // 清理
 * manager.close();  // 自动关闭所有 MCP 连接
 * </pre>
 */
public class McpToolManager implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(McpToolManager.class);

    private final ToolRegistry registry;
    private final ToolExecutor toolExecutor;
    private final List<McpToolClient> mcpClients;

    private McpToolManager(Builder builder) {
        this.registry = builder.registry;
        this.toolExecutor = new ToolExecutor(registry);
        this.mcpClients = builder.mcpClients;
    }

    /**
     * 获取统一的 ToolExecutor
     *
     * 通过此 executor 调用任何工具，不区分本地/MCP
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
     *
     * @param name      服务端名称
     * @param transport 传输层
     * @return 新创建的 McpToolClient
     */
    public McpToolClient addMcpServer(String name, McpClientTransport transport) {
        return addMcpServer(name, transport, Duration.ofSeconds(30));
    }

    /**
     * 运行时动态添加 MCP 服务端
     *
     * @param name      服务端名称
     * @param transport 传输层
     * @param timeout   请求超时
     * @return 新创建的 McpToolClient
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

    /**
     * 关闭所有 MCP 连接
     */
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
        logger.info("McpToolManager closed, all MCP connections released");
    }

    @Override
    public String toString() {
        return String.format("McpToolManager{tools=%d, mcpServers=%d}",
            registry.enabledCount(), mcpClients.size());
    }

    /**
     * 创建构建器
     */
    public static Builder create() {
        return new Builder();
    }

    // ==================== Builder ====================

    public static class Builder {
        private final ToolRegistry registry = new ToolRegistry();
        private final List<McpToolClient> mcpClients = new ArrayList<>();
        private boolean autoScan = false;

        /**
         * 注册本地注解工具（@ToolFunction）
         *
         * @param annotated 包含 @ToolFunction 方法的对象
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
         * 启用 SPI 自动扫描
         */
        public Builder autoScan() {
            this.autoScan = true;
            return this;
        }

        /**
         * 从 McpConfiguration 批量添加 MCP 服务端
         *
         * 遍历配置中所有已启用的服务端，自动创建 transport 并连接。
         * 支持 STDIO 和 SSE 传输类型。
         *
         * <pre>
         * McpConfiguration config = loadFromYaml("mcp-config.yaml");
         * McpToolManager manager = McpToolManager.create()
         *     .fromConfig(config)
         *     .build();
         * </pre>
         *
         * @param config MCP 配置
         */
        public Builder fromConfig(McpConfiguration config) {
            if (config == null || config.getServers() == null) {
                return this;
            }

            for (Map.Entry<String, McpConfiguration.ServerConfig> entry : config.getEnabledServers().entrySet()) {
                String name = entry.getKey();
                McpConfiguration.ServerConfig serverConfig = entry.getValue();

                McpClientTransport transport = createTransport(name, serverConfig);
                Duration timeout = Duration.ofSeconds(serverConfig.getTimeoutSeconds());
                addMcpServer(name, transport, timeout);

                logger.info("Added MCP server '{}' from config (transport={})", name, serverConfig.getTransport());
            }
            return this;
        }

        /**
         * 根据 ServerConfig 创建对应的 MCP 传输层
         */
        private static McpClientTransport createTransport(String name, McpConfiguration.ServerConfig config) {
            String transport = config.getTransport();
            if ("sse".equalsIgnoreCase(transport) || "http".equalsIgnoreCase(transport)) {
                if (config.getUrl() == null || config.getUrl().isBlank()) {
                    throw new IllegalStateException(
                        "MCP server '" + name + "': SSE transport requires 'url'");
                }
                // HttpClientSseClientTransport 在 mcp 核心模块中，使用 JDK HttpClient，无需 Spring
                return HttpClientSseClientTransport.builder(config.getUrl())
                    .sseEndpoint("/sse")
                    .build();
            }

            // Default: STDIO
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

        /**
         * 添加 MCP 服务端
         *
         * build() 时自动执行：connect → discover → register
         *
         * @param name      服务端名称（用于追踪工具来源）
         * @param transport MCP 传输层（STDIO/SSE 等）
         */
        public Builder addMcpServer(String name, McpClientTransport transport) {
            return addMcpServer(name, transport, Duration.ofSeconds(30));
        }

        /**
         * 添加 MCP 服务端（自定义超时）
         */
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

        /**
         * 直接使用已初始化的 McpToolClient
         */
        public Builder addMcpClient(McpToolClient client) {
            client.registerTools(registry);
            mcpClients.add(client);
            return this;
        }

        /**
         * 构建 McpToolManager
         */
        public McpToolManager build() {
            if (autoScan) {
                registry.scanAndRegister();
            }
            return new McpToolManager(this);
        }
    }
}
