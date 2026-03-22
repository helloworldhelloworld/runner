package com.lightweightai.web.config;

import com.lightweightai.kernel.core.postprocess.StreamPostProcessor;
import com.lightweightai.kernel.gateway.ChatHandler;
import com.lightweightai.kernel.gateway.Gateway;
import com.lightweightai.kernel.gateway.SessionManager;
import com.lightweightai.kernel.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;

/**
 * Gateway Bean 注册
 *
 * 将 GatewayService（实现了 ChatHandler + SessionManager）组装为 kernel Gateway。
 * 自动注入所有已注册的 StreamPostProcessor Bean，按 order 排序后组装为后处理管道。
 */
@Configuration
public class GatewayConfig {

    private static final Logger logger = LoggerFactory.getLogger(GatewayConfig.class);

    @Bean
    public Gateway gateway(ChatHandler chatHandler,
                           SessionManager sessionManager,
                           Tracer tracer,
                           @Autowired(required = false) List<StreamPostProcessor> postProcessors) {
        List<StreamPostProcessor> processors = postProcessors != null ? postProcessors : Collections.emptyList();

        Gateway.Builder builder = Gateway.builder()
            .chatHandler(chatHandler)
            .sessionManager(sessionManager)
            .tracer(tracer);

        for (StreamPostProcessor processor : processors) {
            builder.addPostProcessor(processor);
            logger.info("Gateway post-processor registered: {} (order={})",
                processor.getName(), processor.getOrder());
        }

        Gateway gw = builder.build();
        logger.info("Gateway initialized with ChatHandler: {}, SessionManager: {}, postProcessors: {}",
            chatHandler.getClass().getSimpleName(),
            sessionManager.getClass().getSimpleName(),
            processors.size());
        return gw;
    }
}
