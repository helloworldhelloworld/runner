package com.lightweightai.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.mcp.McpToolAdapter;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import jakarta.annotation.PreDestroy;
import java.util.List;

/**
 * MCP HTTP Server 配置
 *
 * 将本地 ToolRegistry 通过 Streamable HTTP 协议暴露为 MCP Server，
 * 供外部 MCP Client 通过 HTTP 调用。
 *
 * <h2>配置</h2>
 * <pre>
 * # application.yml
 * app:
 *   mcp:
 *     server:
 *       enabled: true          # 启用 MCP HTTP Server
 *       name: agent-web        # Server 名称
 *       version: 0.1.0
 *       endpoint: /mcp         # HTTP 端点路径
 * </pre>
 *
 * <h2>外部 Client 连接方式</h2>
 * <pre>
 * # mcp-server.yaml（其他 MCP Client 的配置）
 * upstream:
 *   servers:
 *     agent-web:
 *       transport: streamable_http
 *       url: "http://localhost:8080/mcp"
 * </pre>
 */
@Configuration
@ConditionalOnProperty(name = "app.mcp.server.enabled", havingValue = "true")
public class McpServerConfig {

    private static final Logger logger = LoggerFactory.getLogger(McpServerConfig.class);

    @Value("${app.mcp.server.name:agent-web}")
    private String serverName;

    @Value("${app.mcp.server.version:0.1.0}")
    private String serverVersion;

    @Value("${app.mcp.server.endpoint:/mcp}")
    private String endpoint;

    private McpSyncServer mcpServer;

    @Bean
    WebMvcStreamableServerTransportProvider mcpStreamableHttpTransport(ObjectMapper objectMapper) {
        return new WebMvcStreamableServerTransportProvider(objectMapper, endpoint);
    }

    @Bean
    RouterFunction<ServerResponse> mcpRouterFunction(
            WebMvcStreamableServerTransportProvider transportProvider) {
        return transportProvider.getRouterFunction();
    }

    @Bean
    McpSyncServer mcpSyncServer(
            WebMvcStreamableServerTransportProvider transportProvider,
            ToolRegistry toolRegistry) {

        List<McpServerFeatures.SyncToolSpecification> toolSpecs =
            McpToolAdapter.toMcpTools(toolRegistry);

        mcpServer = McpServer.sync(transportProvider)
            .serverInfo(serverName, serverVersion)
            .capabilities(McpSchema.ServerCapabilities.builder()
                .tools(true)
                .build())
            .tools(toolSpecs.toArray(new McpServerFeatures.SyncToolSpecification[0]))
            .build();

        logger.info("MCP HTTP Server '{}' v{} started at {} with {} tools",
            serverName, serverVersion, endpoint, toolSpecs.size());

        return mcpServer;
    }

    @PreDestroy
    public void shutdown() {
        if (mcpServer != null) {
            mcpServer.close();
            logger.info("MCP HTTP Server '{}' closed", serverName);
        }
    }
}
