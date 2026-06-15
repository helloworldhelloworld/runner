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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

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
    private final List<McpToolClient> clients = new CopyOnWriteArrayList<>();

    /** 启动时连不上的 server，每隔这么多秒后台重连一次（不依赖重启，见 ADR-011）。 */
    @Value("${app.mcp.retry-seconds:15}")
    private long retrySeconds;

    private final ScheduledExecutorService retryScheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mcp-retry");
            t.setDaemon(true);
            return t;
        });

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

            // 每个 server 一个常驻"保活"任务：没连上就连、连上周期探活、断了重连+重注册。
            // 统一覆盖"启动时没起"和"运行中重启"，全程不需重启 runner（ADR-011）。
            final McpToolClient[] ref = { tryConnectReturning(name, serverConfig, toolRegistry) };
            if (ref[0] == null) {
                logger.warn("MCP server '{}' not reachable yet; (re)connecting every {}s in background",
                    name, retrySeconds);
            }
            retryScheduler.scheduleAtFixedRate(() -> {
                // 必须吞掉一切异常：scheduleAtFixedRate 一旦任务抛异常就**永久停掉后续 tick**，
                // 那样 server 重启后再也不会自动重连（实测踩到：0 次重连）。
                try {
                    ref[0] = healthTick(ref[0],
                        () -> ref[0].refreshTools(toolRegistry),
                        () -> tryConnectReturning(name, serverConfig, toolRegistry));
                } catch (Throwable t) {
                    logger.warn("MCP '{}' health tick error (will retry next tick): {}", name, t.toString());
                }
            }, retrySeconds, retrySeconds, TimeUnit.SECONDS);
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

    /**
     * 连接 + 注册一次：成功返回 client（并加入 clients 供清理），失败返回 {@code null}（仅记日志）。
     * 注册进共享 ToolRegistry —— Orchestrator 每请求重新快照 registry，故晚注册的工具下次请求即可见。
     */
    McpToolClient tryConnectReturning(String name, McpConfiguration.ServerConfig serverConfig,
                                      ToolRegistry toolRegistry) {
        try {
            McpToolClient client = connectMcpServer(name, serverConfig);
            int toolCount = client.registerTools(toolRegistry);
            clients.add(client);
            List<String> toolNames = client.getDiscoveredTools().stream()
                .map(McpToolWrapper::getName).toList();
            logger.info("MCP server '{}' connected, {} tools registered: {}",
                name, toolCount, toolNames);
            return client;
        } catch (Exception e) {
            logger.warn("Failed to connect MCP server '{}': {}", name, e.getMessage());
            return null;
        }
    }

    /**
     * 一次"保活"判定（可单测）：未连接→连接；已连接→探活；探活抛异常(连接已死)→重连。返回当前(或新)client。
     *
     * 统一覆盖两种情况、都不需重启 runner（ADR-011）：① 启动时 server 还没起(current=null→连)；
     * ② 运行中 server 重启/掉线(probe 抛→重连+重注册)。{@code probe} 用 {@code refreshTools}
     * （内部 listTools，连接死会抛）；{@code connect} 用 {@code tryConnectReturning}。
     */
    static McpToolClient healthTick(McpToolClient current, Runnable probe,
                                    Supplier<McpToolClient> connect) {
        if (current == null) {
            return connect.get();
        }
        try {
            probe.run();
            return current;
        } catch (Exception e) {  // 连接死 → 重连
            return connect.get();
        }
    }

    @PreDestroy
    public void cleanup() {
        retryScheduler.shutdownNow();
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
