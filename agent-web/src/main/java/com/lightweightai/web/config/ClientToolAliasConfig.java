package com.lightweightai.web.config;

import com.lightweightai.kernel.agent.directive.ClientToolAliasRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * 启动期把 {@link ClientToolAliasProperties} 注入到全局
 * {@link ClientToolAliasRegistry}，使 {@code @ClientTool} 注解读取时能合并外部别名。
 *
 * <p>必须在任何 ClientTool 注册之前完成（{@code AgentConfig.toolRegistry()} 通过
 * 构造函数依赖本 Bean 来保证顺序）。</p>
 */
@Configuration
@EnableConfigurationProperties(ClientToolAliasProperties.class)
public class ClientToolAliasConfig {

    private static final Logger logger = LoggerFactory.getLogger(ClientToolAliasConfig.class);

    private final ClientToolAliasProperties properties;

    public ClientToolAliasConfig(ClientToolAliasProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void install() {
        ClientToolAliasRegistry.setSource(properties::aliasesFor);
        if (properties.getAliases().isEmpty()) {
            logger.info("ClientToolAliasRegistry installed (empty config)");
        } else {
            logger.info("ClientToolAliasRegistry installed with {} alias mappings: {}",
                properties.getAliases().size(), properties.getAliases().keySet());
        }
    }

    @PreDestroy
    public void uninstall() {
        ClientToolAliasRegistry.reset();
    }
}
