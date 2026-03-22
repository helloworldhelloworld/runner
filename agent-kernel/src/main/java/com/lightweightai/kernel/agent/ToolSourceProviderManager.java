package com.lightweightai.kernel.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 工具来源提供者管理器
 *
 * 简单的生命周期协调器：
 * - 构造时启动所有提供者
 * - close() 时逆序停止所有提供者
 * - 每个提供者独立 try-catch，一个失败不影响其他
 *
 * Spring 管理时 @PreDestroy 自动调用 close()。
 */
public class ToolSourceProviderManager implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ToolSourceProviderManager.class);

    private final ToolRegistry registry;
    private final List<ToolSourceProvider> providers;

    /**
     * 创建管理器并立即启动所有提供者
     *
     * @param registry  工具注册表
     * @param providers 提供者列表
     */
    public ToolSourceProviderManager(ToolRegistry registry, List<ToolSourceProvider> providers) {
        this.registry = Objects.requireNonNull(registry, "ToolRegistry cannot be null");
        this.providers = new ArrayList<>(providers);
        startAll();
    }

    private void startAll() {
        for (ToolSourceProvider provider : providers) {
            try {
                provider.start(registry);
                logger.info("ToolSourceProvider '{}' started successfully", provider.getName());
            } catch (Exception e) {
                logger.error("Failed to start ToolSourceProvider '{}': {}",
                    provider.getName(), e.getMessage(), e);
            }
        }
    }

    @Override
    public void close() {
        // 逆序停止
        for (int i = providers.size() - 1; i >= 0; i--) {
            ToolSourceProvider provider = providers.get(i);
            try {
                if (provider.isRunning()) {
                    provider.stop(registry);
                    logger.info("ToolSourceProvider '{}' stopped", provider.getName());
                }
            } catch (Exception e) {
                logger.warn("Error stopping ToolSourceProvider '{}': {}",
                    provider.getName(), e.getMessage());
            }
        }
    }

    /**
     * 获取所有提供者（只读）
     */
    public List<ToolSourceProvider> getProviders() {
        return Collections.unmodifiableList(providers);
    }

    /**
     * 获取正在运行的提供者数量
     */
    public long getRunningCount() {
        return providers.stream().filter(ToolSourceProvider::isRunning).count();
    }
}
