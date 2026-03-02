package com.lightweightai.kernel.agent;

import java.util.List;

/**
 * 工具来源接口
 *
 * 提供统一的工具发现机制，支持多种来源：
 * - SPI 扫描（ToolScanner）
 * - MCP 服务端（McpToolClient）
 * - 自定义实现
 *
 * <pre>
 * // 注册来自 MCP 服务端的工具
 * ToolSource mcpSource = mcpToolClient;
 * registry.registerFrom(mcpSource);
 *
 * // 注册来自 SPI 扫描的工具
 * ToolSource spiSource = () -&gt; ToolScanner.scan();
 * registry.registerFrom(spiSource);
 * </pre>
 */
@FunctionalInterface
public interface ToolSource {

    /**
     * 发现并返回可用的工具列表
     *
     * @return 工具列表
     */
    List<Tool> discoverTools();
}
