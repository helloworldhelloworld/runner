package com.lightweightai.web.config;

import com.lightweightai.kernel.gateway.ChatHandler;
import com.lightweightai.kernel.gateway.Gateway;
import com.lightweightai.kernel.gateway.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway Bean 注册
 *
 * 将 GatewayService（实现了 ChatHandler + SessionManager）组装为 kernel Gateway。
 */
@Configuration
public class GatewayConfig {

    private static final Logger logger = LoggerFactory.getLogger(GatewayConfig.class);

    @Bean
    public Gateway gateway(ChatHandler chatHandler, SessionManager sessionManager) {
        Gateway gw = Gateway.builder()
            .chatHandler(chatHandler)
            .sessionManager(sessionManager)
            .build();
        logger.info("Gateway initialized with ChatHandler: {}, SessionManager: {}",
            chatHandler.getClass().getSimpleName(),
            sessionManager.getClass().getSimpleName());
        return gw;
    }
}
