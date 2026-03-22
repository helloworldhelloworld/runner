package com.lightweightai.web.config;

import com.lightweightai.kernel.agent.LegacyScanProvider;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSourceProvider;
import com.lightweightai.kernel.agent.ToolSourceProviderManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * ToolSourceProvider 配置
 *
 * 根据 app.tool-source.mode 启用不同的工具加载模式：
 * - legacy（默认）：不激活任何 Provider，由 AgentConfig 直接 scanAndRegister
 * - dynamic：所有工具通过 ToolSourceProvider 注册
 * - hybrid：legacy 扫描 + ToolSourceProvider 叠加
 */
@Configuration
public class ToolSourceConfig {

    private static final Logger logger = LoggerFactory.getLogger(ToolSourceConfig.class);

    /**
     * dynamic 模式下，创建 LegacyScanProvider 替代 AgentConfig 中的 scanAndRegister
     */
    @Bean
    @ConditionalOnProperty(name = "app.tool-source.mode", havingValue = "dynamic")
    public LegacyScanProvider legacyScanProvider() {
        logger.info("Creating LegacyScanProvider for dynamic mode");
        return new LegacyScanProvider();
    }

    /**
     * dynamic 模式：ToolSourceProviderManager 管理所有 Provider 的生命周期
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "app.tool-source.mode", havingValue = "dynamic")
    public ToolSourceProviderManager dynamicToolSourceManager(
            ToolRegistry toolRegistry,
            List<ToolSourceProvider> providers) {
        logger.info("Starting ToolSourceProviderManager (dynamic mode) with {} providers", providers.size());
        return new ToolSourceProviderManager(toolRegistry, providers);
    }

    /**
     * hybrid 模式：legacy 扫描由 AgentConfig 完成，额外的 Provider 叠加
     * （此时不需要 LegacyScanProvider，因为 AgentConfig 已经做了 scanAndRegister）
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "app.tool-source.mode", havingValue = "hybrid")
    public ToolSourceProviderManager hybridToolSourceManager(
            ToolRegistry toolRegistry,
            List<ToolSourceProvider> providers) {
        logger.info("Starting ToolSourceProviderManager (hybrid mode) with {} providers", providers.size());
        return new ToolSourceProviderManager(toolRegistry, providers);
    }
}
