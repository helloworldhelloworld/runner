package com.lightweightai.kernel.agent;

/**
 * 工具来源 Provider — 带生命周期的工具注册接口。
 *
 * 比 ToolSource（一次性 discoverTools()）增加了 start/stop 生命周期，
 * 直接操作 ToolRegistry，支持热加载和卸载。
 *
 * 内置实现：
 * - LegacyScanProvider: 包装现有 SPI 扫描逻辑
 * - ManualToolProvider: 手动注册需要构造参数的工具
 *
 * Phase 2 可扩展：
 * - DynamicPluginProvider: 目录扫描 + WatchService 热加载
 * - McpServerProvider: MCP 服务器工具发现
 */
public interface ToolSourceProvider {

    /**
     * 来源类型标识，用于日志和调试
     */
    String sourceType();

    /**
     * 启动 Provider，向 registry 注册工具
     */
    void start(ToolRegistry registry);

    /**
     * 停止 Provider，从 registry 注销工具（可选实现）
     */
    default void stop(ToolRegistry registry) {}
}
