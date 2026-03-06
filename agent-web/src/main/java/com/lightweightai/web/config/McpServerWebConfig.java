package com.lightweightai.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.mcp.McpToolServer;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;

/**
 * MCP Server HTTP 传输配置 — 以 SSE 或 Streamable HTTP 方式对外暴露工具
 *
 * 启用后，ToolRegistry 中的所有工具将通过 MCP 协议暴露给外部 MCP 客户端
 * （如 Claude Desktop、Cursor、其他 AI Agent）。
 *
 * <h2>配置方式</h2>
 * <pre>
 * # application.yml
 * app:
 *   mcp-server:
 *     enabled: true
 *     transport: streamable_http   # 或 sse
 *     name: my-agent
 *     version: 1.0.0
 * </pre>
 *
 * <h2>端点</h2>
 * <ul>
 *   <li>Streamable HTTP: POST /mcp</li>
 *   <li>SSE: GET /mcp/sse（SSE 流）+ POST /mcp/sse（消息）</li>
 * </ul>
 */
@Configuration
@ConditionalOnProperty(name = "app.mcp-server.enabled", havingValue = "true")
@ConditionalOnClass(name = "io.modelcontextprotocol.server.transport.HttpServletStreamableHttpTransportProvider")
public class McpServerWebConfig {

    private static final Logger logger = LoggerFactory.getLogger(McpServerWebConfig.class);

    @Value("${app.mcp-server.transport:streamable_http}")
    private String transport;

    @Value("${app.mcp-server.name:lightweight-ai-agent}")
    private String serverName;

    @Value("${app.mcp-server.version:0.1.0}")
    private String serverVersion;

    private McpToolServer mcpToolServer;

    @Bean
    public McpServerTransportProvider mcpServerTransportProvider(ObjectMapper objectMapper) {
        JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(objectMapper);

        McpServerTransportProvider provider;
        if ("sse".equalsIgnoreCase(transport)) {
            provider = createSseTransport(jsonMapper);
            logger.info("MCP Server transport: SSE (endpoint: /mcp/sse)");
        } else {
            provider = createStreamableHttpTransport(jsonMapper);
            logger.info("MCP Server transport: Streamable HTTP (endpoint: /mcp)");
        }
        return provider;
    }

    @Bean
    public McpToolServer mcpToolServer(ToolRegistry toolRegistry,
                                        McpServerTransportProvider transportProvider) {
        mcpToolServer = McpToolServer.builder()
            .serverName(serverName)
            .serverVersion(serverVersion)
            .toolRegistry(toolRegistry)
            .transportProvider(transportProvider)
            .build();
        mcpToolServer.start();

        logger.info("MCP Server '{}' v{} started with {} tools (transport={})",
            serverName, serverVersion, toolRegistry.enabledCount(), transport);
        return mcpToolServer;
    }

    @PreDestroy
    public void shutdown() {
        if (mcpToolServer != null) {
            mcpToolServer.close();
            logger.info("MCP Server '{}' stopped", serverName);
        }
    }

    /**
     * 创建 Streamable HTTP 传输（推荐）
     *
     * MCP SDK 的 HttpServletStreamableHttpTransportProvider 会自动注册
     * Servlet 处理 POST /mcp 请求。
     */
    private McpServerTransportProvider createStreamableHttpTransport(JacksonMcpJsonMapper jsonMapper) {
        try {
            Class<?> clazz = Class.forName(
                "io.modelcontextprotocol.server.transport.HttpServletStreamableHttpTransportProvider");
            var constructor = clazz.getConstructor(
                io.modelcontextprotocol.spec.McpJsonMapper.class, String.class);
            return (McpServerTransportProvider) constructor.newInstance(jsonMapper, "/mcp");
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to create Streamable HTTP transport. Ensure mcp-spring-webmvc is on classpath.", e);
        }
    }

    /**
     * 创建 SSE 传输（旧版兼容）
     *
     * MCP SDK 的 HttpServletSseServerTransportProvider 会自动注册
     * Servlet 处理 SSE 连接和消息。
     */
    private McpServerTransportProvider createSseTransport(JacksonMcpJsonMapper jsonMapper) {
        try {
            Class<?> clazz = Class.forName(
                "io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider");
            var constructor = clazz.getConstructor(
                io.modelcontextprotocol.spec.McpJsonMapper.class, String.class);
            return (McpServerTransportProvider) constructor.newInstance(jsonMapper, "/mcp/sse");
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to create SSE transport. Ensure mcp-spring-webmvc is on classpath.", e);
        }
    }
}
