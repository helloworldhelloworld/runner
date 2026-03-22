package com.lightweightai.kernel.agent;

import java.util.List;

/**
 * 工具来源提供者接口（带生命周期管理）
 *
 * 扩展 ToolSource，增加 start/stop 生命周期，支持：
 * - 延迟初始化（start 时才连接和发现工具）
 * - 资源清理（stop 时注销工具和释放连接）
 * - 热加载（stop + start 实现重新发现）
 *
 * <pre>
 * // 使用示例
 * ToolSourceProvider provider = new LegacyScanProvider();
 * provider.start(registry);   // 扫描 + 注册
 * // ... 使用工具 ...
 * provider.stop(registry);    // 注销 + 清理
 * </pre>
 *
 * @see ToolSource
 * @see LegacyScanProvider
 * @see ToolSourceProviderManager
 */
public interface ToolSourceProvider extends ToolSource {

    /**
     * 提供者名称，用于日志和诊断
     *
     * @return 唯一标识名称，如 "legacy-scan"、"mcp:server-name"
     */
    String getName();

    /**
     * 启动提供者：连接资源、发现工具、注册到 registry
     *
     * @param registry 工具注册表，直接调用 registry.register() 注册工具
     */
    void start(ToolRegistry registry);

    /**
     * 停止提供者：注销工具、释放资源
     *
     * @param registry 工具注册表，调用 registry.unregister() 注销工具
     */
    void stop(ToolRegistry registry);

    /**
     * 是否正在运行
     *
     * @return true 表示已 start 且未 stop
     */
    boolean isRunning();

    /**
     * 默认实现返回空列表。
     * 生命周期提供者通过 start() 直接注册工具，不依赖 discoverTools()。
     * 支持一次性发现的提供者可以覆盖此方法。
     */
    @Override
    default List<Tool> discoverTools() {
        return List.of();
    }
}
