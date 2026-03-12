package com.lightweightai.web.config;

import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.gateway.Gateway;
import com.lightweightai.safety.CrisisDetector;
import com.lightweightai.web.websocket.VertxChatWebSocketHandler;
import com.lightweightai.web.websocket.VertxWebSocketServer;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Vert.x WebSocket 配置。
 *
 * 替代原有的 Spring WebSocket 配置（WebSocketConfig），
 * 使用 Vert.x 的高性能 event loop 模型处理 WebSocket 连接。
 *
 * <p>Vert.x HttpServer 运行在独立端口（默认 8081），
 * 与 Spring Boot 的 Tomcat（端口 8080）互不干扰。
 */
@Configuration
public class VertxWebSocketConfig {

    @Bean(destroyMethod = "close")
    public Vertx vertx() {
        VertxOptions options = new VertxOptions()
            .setEventLoopPoolSize(Math.max(2, Runtime.getRuntime().availableProcessors()))
            .setWorkerPoolSize(20)
            .setWarningExceptionTime(5_000_000_000L);  // 5秒 event loop 阻塞告警
        return Vertx.vertx(options);
    }

    @Bean
    public VertxChatWebSocketHandler vertxChatWebSocketHandler(
            Gateway gateway,
            CrisisDetector crisisDetector,
            ToolRegistry toolRegistry) {
        return new VertxChatWebSocketHandler(gateway, crisisDetector, toolRegistry);
    }

    @Bean
    public VertxWebSocketServer vertxWebSocketServer(
            Vertx vertx,
            VertxChatWebSocketHandler handler,
            @Value("${vertx.websocket.port:8081}") int port) {
        return new VertxWebSocketServer(vertx, handler, port);
    }
}
