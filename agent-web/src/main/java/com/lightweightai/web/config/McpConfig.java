package com.lightweightai.web.config;

import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.mcp.McpConfiguration;
import com.lightweightai.mcp.McpHeaderProvider;
import com.lightweightai.mcp.McpToolClient;
import com.lightweightai.mcp.McpToolWrapper;
import com.lightweightai.mcp.ToolClient;
import io.modelcontextprotocol.spec.McpClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 远程工具配置 — 启动时自动连接 MCP Server 并注册工具到 ToolRegistry
 *
 * <h2>核心设计：工具注册与 Header 注入解耦</h2>
 *
 * 调用方只需做两件事（互不依赖）：
 * <ol>
 *   <li>在 YAML 中声明 MCP Server 配置（含可选静态 headers）</li>
 *   <li>（可选）声明一个 {@link McpHeaderProvider} Bean 提供动态 headers</li>
 * </ol>
 *
 * 框架启动时自动完成：配置绑定 → header 合并 → transport 创建 → connect → discover → register。
 * 调用方（AgentLoop / ToolCallingLoop）通过 ToolRegistry 透明调用，完全无感。
 *
 * <h2>配置方式</h2>
 * <pre>
 * # application.yml
 * app:
 *   mcp:
 *     enabled: true
 *     servers:
 *       # STDIO 本地进程
 *       demo-agent:
 *         command: java
 *         args:
 *           - -cp
 *           - classpath
 *           - com.lightweightai.demo.McpServerRunner
 *         timeout: 60
 *
 *       # SSE 远程（带静态 header）
 *       remote-api:
 *         transport: sse
 *         url: http://api-server:8080/sse
 *         timeout: 45
 *         headers:
 *           X-Client-Version: "1.0"
 *
 *       # Streamable HTTP（动态 header 由 McpHeaderProvider Bean 注入）
 *       auth-service:
 *         transport: streamable_http
 *         url: http://auth-server:8080/mcp
 *         timeout: 30
 * </pre>
 *
 * <h2>调用方解耦示例</h2>
 * <pre>
 * // ===== 步骤 1: 声明 McpHeaderProvider Bean（独立于 MCP 注册） =====
 * &#64;Configuration
 * public class MyHeaderConfig {
 *
 *     &#64;Bean
 *     public McpHeaderProvider mcpHeaderProvider(TokenService tokenService) {
 *         // 每次 MCP HTTP 请求发送前调用，动态生成 header
 *         return () -> Map.of(
 *             "Authorization", "Bearer " + tokenService.getToken(),
 *             "X-Trace-Id", TraceContext.traceId()
 *         );
 *     }
 * }
 *
 * // ===== 步骤 2: YAML 中声明 server（无需关心 header 怎么来） =====
 * // app.mcp.enabled=true
 * // app.mcp.servers.auth-service.transport=streamable_http
 * // app.mcp.servers.auth-service.url=http://auth-server:8080/mcp
 *
 * // ===== 启动时自动发生 =====
 * // McpConfig 注入 McpProperties（YAML 绑定）+ McpHeaderProvider（动态 token）
 * //   → resolveHeaders() 合并: config.headers(静态) + provider.getHeaders()(动态)
 * //   → createTransport() 构建带 header 的 transport
 * //   → McpToolClient connect → discover → registerTools(ToolRegistry)
 *
 * // ===== 调用方完全无感 =====
 * // AgentLoop / ToolCallingLoop 只看到 ToolRegistry 中的 Tool:
 * //   toolRegistry.get("auth_tool").execute(args);  // header 已在 transport 层自动注入
 * </pre>
 *
 * @see McpProperties
 * @see McpHeaderProvider
 * @see ToolClient.Builder#createTransport
 * @see ToolClient.Builder#resolveHeaders
 */
@Configuration
@ConditionalOnProperty(name = "app.mcp.enabled", havingValue = "true")
@EnableConfigurationProperties(McpProperties.class)
public class McpConfig {

    private static final Logger logger = LoggerFactory.getLogger(McpConfig.class);

    private final McpProperties mcpProperties;
    private final McpHeaderProvider headerProvider;
    private final List<McpToolClient> clients = new ArrayList<>();

    /**
     * @param mcpProperties  YAML 类型化绑定（app.mcp.servers → ServerConfig）
     * @param headerProvider 可选的动态 Header 提供者 Bean（为 null 时仅使用静态 config headers）
     */
    public McpConfig(McpProperties mcpProperties,
                     @Autowired(required = false) McpHeaderProvider headerProvider) {
        this.mcpProperties = mcpProperties;
        this.headerProvider = headerProvider;
    }

    /**
     * 连接所有配置的 MCP Server，注册远程工具到 ToolRegistry
     */
    @Bean
    public McpToolRegistrar mcpToolRegistrar(ToolRegistry toolRegistry) {
        Map<String, McpConfiguration.ServerConfig> servers = mcpProperties.getServers();
        if (servers == null || servers.isEmpty()) {
            logger.info("No MCP servers configured");
            return new McpToolRegistrar(List.of());
        }

        for (Map.Entry<String, McpConfiguration.ServerConfig> entry : servers.entrySet()) {
            String name = entry.getKey();
            McpConfiguration.ServerConfig serverConfig = entry.getValue();

            if (!serverConfig.isEnabled()) {
                logger.info("MCP server '{}' is disabled, skipping", name);
                continue;
            }

            try {
                McpToolClient client = connectMcpServer(name, serverConfig);
                int toolCount = client.registerTools(toolRegistry);
                clients.add(client);

                List<String> toolNames = client.getDiscoveredTools().stream()
                    .map(McpToolWrapper::getName).toList();
                logger.info("MCP server '{}' connected, {} tools registered: {}",
                    name, toolCount, toolNames);
            } catch (Exception e) {
                logger.warn("Failed to connect MCP server '{}': {}", name, e.getMessage());
            }
        }

        logger.info("MCP integration: {} servers connected, tools registered into ToolRegistry",
            clients.size());
        return new McpToolRegistrar(clients);
    }

    /**
     * 连接单个 MCP Server — 委托 ToolClient.Builder.createTransport() 统一创建 transport
     *
     * header 合并逻辑（resolveHeaders）：
     *   静态 config.headers + 动态 headerProvider.getHeaders() → 动态优先覆盖同名 key
     */
    private McpToolClient connectMcpServer(String name, McpConfiguration.ServerConfig serverConfig) {
        // 单一职责：transport 创建逻辑统一在 ToolClient.Builder 中，不再重复
        McpClientTransport transport = ToolClient.Builder.createTransport(
            name, serverConfig, headerProvider);

        Duration timeout = Duration.ofSeconds(serverConfig.getTimeoutSeconds());

        McpToolClient client = McpToolClient.builder()
            .serverName(name)
            .transport(transport)
            .requestTimeout(timeout)
            .build();
        client.initialize();
        return client;
    }

    @PreDestroy
    public void cleanup() {
        for (McpToolClient client : clients) {
            try {
                client.close();
                logger.info("MCP client '{}' closed", client.getServerName());
            } catch (Exception e) {
                logger.warn("Error closing MCP client '{}': {}", client.getServerName(), e.getMessage());
            }
        }
    }

    /**
     * MCP 工具注册结果，供其他 Bean 注入查询
     */
    public static class McpToolRegistrar {
        private final List<McpToolClient> clients;

        public McpToolRegistrar(List<McpToolClient> clients) {
            this.clients = clients;
        }

        public List<McpToolClient> getClients() {
            return clients;
        }

        public int getServerCount() {
            return clients.size();
        }

        public int getTotalToolCount() {
            return clients.stream()
                .mapToInt(c -> c.getDiscoveredTools().size())
                .sum();
        }
    }
}
