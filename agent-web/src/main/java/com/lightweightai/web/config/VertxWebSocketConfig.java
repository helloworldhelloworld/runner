package com.lightweightai.web.config;

import com.lightweightai.assessment.AssessmentService;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.gateway.Gateway;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.safety.CrisisDetector;
import com.lightweightai.user.UserService;
import com.lightweightai.web.service.ChatService;
import com.lightweightai.web.service.SoulComfortChatService;
import com.lightweightai.web.skillcreator.SkillCreatorService;
import com.lightweightai.web.websocket.VertxChatWebSocketHandler;
import com.lightweightai.web.websocket.VertxWebSocketServer;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Vert.x WebSocket 配置 — 独立端口（默认 8081）
 *
 * 当 app.websocket.provider=vertx 时激活。
 * 使用 Vert.x 的高性能 event loop 模型处理 WebSocket 连接。
 * 前端连接 ws://host:8081/ws/chat。
 */
@Configuration
@ConditionalOnProperty(name = "app.websocket.provider", havingValue = "vertx")
public class VertxWebSocketConfig {

    private static final Logger logger = LoggerFactory.getLogger(VertxWebSocketConfig.class);

    @Bean(destroyMethod = "close")
    public Vertx vertx() {
        VertxOptions options = new VertxOptions()
            .setEventLoopPoolSize(Math.max(2, Runtime.getRuntime().availableProcessors()))
            .setWorkerPoolSize(20)
            .setWarningExceptionTime(5_000_000_000L);
        return Vertx.vertx(options);
    }

    @Bean
    public VertxChatWebSocketHandler vertxChatWebSocketHandler(
            Gateway gateway,
            CrisisDetector crisisDetector,
            ToolRegistry toolRegistry,
            ChatService chatService,
            SoulComfortChatService soulComfortChatService,
            SkillCreatorService skillCreatorService,
            AssessmentService assessmentService,
            UserService userService,
            LLMProvider llmProvider,
            @Autowired(required = false) McpConfig.McpToolRegistrar mcpToolRegistrar) {
        return new VertxChatWebSocketHandler(gateway, crisisDetector, toolRegistry,
            chatService, soulComfortChatService, skillCreatorService, assessmentService, userService,
            llmProvider, mcpToolRegistrar);
    }

    @Bean
    public VertxWebSocketServer vertxWebSocketServer(
            Vertx vertx,
            VertxChatWebSocketHandler handler,
            @Value("${vertx.websocket.port:8081}") int port) {
        logger.info("Using Vert.x WebSocket on port {}", port);
        return new VertxWebSocketServer(vertx, handler, port);
    }
}
