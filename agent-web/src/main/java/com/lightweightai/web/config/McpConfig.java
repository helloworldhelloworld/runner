package com.lightweightai.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.mcp.McpToolClient;
import com.lightweightai.mcp.McpToolWrapper;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MCP 远程工具配置
 *
 * 通过 application.yml 配置 MCP Server，启动时自动连接并注册远程工具到 ToolRegistry。
 * LLM 可以透明调用这些远程工具，和本地工具代码完全一样。
 *
 * <h2>配置方式</h2>
 * <pre>
 * # application.yml
 * app:
 *   mcp:
 *     enabled: true
 *     servers:
 *       demo-agent:
 *         command: java
 *         args: -cp,classpath,com.lightweightai.demo.McpServerRunner
 *         timeout: 60
 *       weather:
 *         command: npx
 *         args: -y,@weather/mcp-server
 *         env: API_KEY=${WEATHER_API_KEY}
 *         timeout: 30
 *       remote-api:
 *         transport: sse
 *         url: http://api-server:8080/sse
 *         timeout: 45
 * </pre>
 */
@Configuration
@ConditionalOnProperty(name = "app.mcp.enabled", havingValue = "true")
public class McpConfig {

    private static final Logger logger = LoggerFactory.getLogger(McpConfig.class);

    @Value("#{${app.mcp.servers:{}}}")
    private Map<String, Map<String, String>> mcpServers;

    private final List<McpToolClient> clients = new ArrayList<>();

    /**
     * 连接所有配置的 MCP Server，注册远程工具到 ToolRegistry
     */
    @Bean
    public McpToolRegistrar mcpToolRegistrar(ToolRegistry toolRegistry) {
        if (mcpServers == null || mcpServers.isEmpty()) {
            logger.info("No MCP servers configured");
            return new McpToolRegistrar(List.of());
        }

        for (Map.Entry<String, Map<String, String>> entry : mcpServers.entrySet()) {
            String name = entry.getKey();
            Map<String, String> config = entry.getValue();

            try {
                McpToolClient client = connectMcpServer(name, config);
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

    private McpToolClient connectMcpServer(String name, Map<String, String> config) {
        String transport = config.getOrDefault("transport", "stdio");
        long timeout = Long.parseLong(config.getOrDefault("timeout", "30"));

        McpClientTransport mcpTransport;
        if ("sse".equalsIgnoreCase(transport)) {
            String url = config.get("url");
            if (url == null || url.isBlank()) {
                throw new IllegalStateException("MCP server '" + name + "': SSE requires 'url'");
            }
            throw new UnsupportedOperationException(
                "SSE transport requires mcp-spring-webflux dependency");
        } else {
            // STDIO
            String command = config.get("command");
            if (command == null || command.isBlank()) {
                throw new IllegalStateException("MCP server '" + name + "': STDIO requires 'command'");
            }

            ServerParameters.Builder paramsBuilder = ServerParameters.builder(command);

            String argsStr = config.get("args");
            if (argsStr != null && !argsStr.isBlank()) {
                paramsBuilder.args(argsStr.split(","));
            }

            String envStr = config.get("env");
            if (envStr != null && !envStr.isBlank()) {
                Map<String, String> envMap = new java.util.HashMap<>();
                for (String pair : envStr.split(",")) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length == 2) {
                        envMap.put(kv[0].trim(), kv[1].trim());
                    }
                }
                if (!envMap.isEmpty()) {
                    paramsBuilder.env(envMap);
                }
            }

            mcpTransport = new StdioClientTransport(paramsBuilder.build(),
                new JacksonMcpJsonMapper(new ObjectMapper()));
        }

        McpToolClient client = McpToolClient.builder()
            .serverName(name)
            .transport(mcpTransport)
            .requestTimeout(Duration.ofSeconds(timeout))
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
