package com.lightweightai.mcp;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSourceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * MCP 工具来源提供者
 *
 * 包装 McpToolClient 的生命周期为 ToolSourceProvider 接口：
 * - start(): 初始化 MCP 连接 + 发现工具 + 注册
 * - stop(): 注销工具 + 关闭连接
 *
 * McpToolClient 已实现 AutoCloseable，天然适配。
 */
public class McpSourceProvider implements ToolSourceProvider {

    private static final Logger logger = LoggerFactory.getLogger(McpSourceProvider.class);

    private final McpToolClient client;
    private volatile boolean running = false;
    private List<String> registeredToolNames = List.of();

    /**
     * 创建 MCP 来源提供者
     *
     * @param client 未初始化的 McpToolClient（start 时初始化）
     */
    public McpSourceProvider(McpToolClient client) {
        this.client = Objects.requireNonNull(client, "McpToolClient cannot be null");
    }

    @Override
    public String getName() {
        return "mcp:" + client.getServerName();
    }

    @Override
    public void start(ToolRegistry registry) {
        if (running) {
            logger.warn("McpSourceProvider '{}' already running, ignoring start()", getName());
            return;
        }

        client.initialize();
        List<Tool> tools = client.discoverTools();
        for (Tool tool : tools) {
            registry.register(tool);
        }
        registeredToolNames = tools.stream().map(Tool::getName).toList();
        running = true;
        logger.info("McpSourceProvider '{}' started, registered {} tools",
            client.getServerName(), tools.size());
    }

    @Override
    public void stop(ToolRegistry registry) {
        if (!running) {
            return;
        }
        for (String name : registeredToolNames) {
            registry.unregister(name);
        }
        registeredToolNames = List.of();
        try {
            client.close();
        } catch (Exception e) {
            logger.warn("Error closing McpToolClient '{}': {}",
                client.getServerName(), e.getMessage());
        }
        running = false;
        logger.info("McpSourceProvider '{}' stopped", client.getServerName());
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * 获取底层 McpToolClient
     */
    public McpToolClient getClient() {
        return client;
    }
}
