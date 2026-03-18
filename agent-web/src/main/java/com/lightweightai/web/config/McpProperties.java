package com.lightweightai.web.config;

import com.lightweightai.mcp.McpConfiguration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MCP 配置属性 — Spring Boot 类型化绑定
 *
 * 将 application.yml 中的 {@code app.mcp} 节直接绑定到
 * {@link McpConfiguration.ServerConfig} 类型化模型，支持嵌套 headers map、
 * args list 等结构，取代旧的扁平 {@code Map<String, String>} + {@code header.*} 前缀 hack。
 *
 * <h2>YAML 配置示例</h2>
 * <pre>
 * app:
 *   mcp:
 *     enabled: true
 *     servers:
 *       remote-api:
 *         transport: sse
 *         url: http://api-server:8080/sse
 *         timeout: 45
 *         headers:
 *           Authorization: "Bearer ${MCP_AUTH_TOKEN:}"
 *           X-Custom: "value"
 *       local-agent:
 *         transport: stdio
 *         command: java
 *         args:
 *           - -cp
 *           - classpath
 *           - com.example.McpServer
 * </pre>
 *
 * <h2>调用方解耦示例 — Header 与工具注册分离</h2>
 *
 * 调用方只需声明一个 {@link com.lightweightai.mcp.McpHeaderProvider} Bean，
 * 框架在启动时自动将它注入到 MCP transport 创建流程中，
 * 无需在注册工具时手动拼接 header：
 *
 * <pre>
 * // 1. 声明 HeaderProvider Bean — 与 MCP 注册完全解耦
 * &#64;Bean
 * public McpHeaderProvider mcpHeaderProvider(TokenService tokenService) {
 *     return () -> Map.of(
 *         "Authorization", "Bearer " + tokenService.getToken(),
 *         "X-Trace-Id", TraceContext.traceId()
 *     );
 * }
 *
 * // 2. YAML 中声明 MCP Server（可带静态 header，也可不带）
 * // app.mcp.servers.remote-api.transport=sse
 * // app.mcp.servers.remote-api.url=http://...
 * // app.mcp.servers.remote-api.headers.X-Client-Version=1.0
 *
 * // 3. 启动时自动发生：
 * //    McpConfig 注入 McpProperties + McpHeaderProvider
 * //    → 对每个 server 调用 ToolClient.Builder.createTransport(name, config, headerProvider)
 * //    → resolveHeaders() 合并 静态 config.headers + 动态 provider.getHeaders()
 * //    → 构建 transport → connect → discover → register 到 ToolRegistry
 * //
 * // 调用方（如 AgentLoop / ToolCallingLoop）完全无感：
 * //    toolRegistry.get("remote_tool").execute(args)  // 透明调用，header 已在 transport 层注入
 * </pre>
 *
 * @see McpConfiguration.ServerConfig
 * @see com.lightweightai.mcp.McpHeaderProvider
 * @see McpConfig
 */
@ConfigurationProperties(prefix = "app.mcp")
public class McpProperties {

    private boolean enabled = false;

    private Map<String, McpConfiguration.ServerConfig> servers = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, McpConfiguration.ServerConfig> getServers() {
        return servers;
    }

    public void setServers(Map<String, McpConfiguration.ServerConfig> servers) {
        this.servers = servers;
    }

    /**
     * 转换为 McpConfiguration，供 ToolClient.Builder.fromConfig() 使用
     */
    public McpConfiguration toMcpConfiguration() {
        McpConfiguration config = new McpConfiguration();
        config.setServers(servers);
        return config;
    }
}
