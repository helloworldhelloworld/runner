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
import com.lightweightai.web.skillcreator.ToolSchemaRepository;
import com.lightweightai.web.websocket.SessionAwareClientToolDispatcher;
import com.lightweightai.web.websocket.SpringChatWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Spring WebSocket 配置 — 在 Tomcat 8080 端口注册 /ws/chat
 *
 * 当 app.websocket.provider=spring（默认）时激活。
 * 前端连接 ws://host:8080/ws/chat。
 */
@Configuration
@EnableWebSocket
@ConditionalOnProperty(name = "app.websocket.provider", havingValue = "spring", matchIfMissing = true)
public class SpringWebSocketConfig implements WebSocketConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(SpringWebSocketConfig.class);

    private final Gateway gateway;
    private final CrisisDetector crisisDetector;
    private final ToolRegistry toolRegistry;
    private final ChatService chatService;
    private final SoulComfortChatService soulComfortChatService;
    private final AssessmentService assessmentService;
    private final UserService userService;
    private final LLMProvider llmProvider;
    private final McpConfig.McpToolRegistrar mcpToolRegistrar;
    private final SessionAwareClientToolDispatcher sessionAwareDispatcher;
    private final SkillCreatorService skillCreatorService;
    private final ToolSchemaRepository toolSchemaRepository;

    public SpringWebSocketConfig(Gateway gateway,
                                  CrisisDetector crisisDetector,
                                  ToolRegistry toolRegistry,
                                  ChatService chatService,
                                  SoulComfortChatService soulComfortChatService,
                                  AssessmentService assessmentService,
                                  UserService userService,
                                  LLMProvider llmProvider,
                                  @Autowired(required = false) McpConfig.McpToolRegistrar mcpToolRegistrar,
                                  SessionAwareClientToolDispatcher sessionAwareDispatcher,
                                  SkillCreatorService skillCreatorService,
                                  ToolSchemaRepository toolSchemaRepository) {
        this.gateway = gateway;
        this.crisisDetector = crisisDetector;
        this.toolRegistry = toolRegistry;
        this.chatService = chatService;
        this.soulComfortChatService = soulComfortChatService;
        this.assessmentService = assessmentService;
        this.userService = userService;
        this.llmProvider = llmProvider;
        this.mcpToolRegistrar = mcpToolRegistrar;
        this.sessionAwareDispatcher = sessionAwareDispatcher;
        this.skillCreatorService = skillCreatorService;
        this.toolSchemaRepository = toolSchemaRepository;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        logger.info("Using Spring WebSocket on Tomcat (same port as HTTP)");
        registry.addHandler(
                new SpringChatWebSocketHandler(gateway, crisisDetector, toolRegistry,
                    chatService, soulComfortChatService, assessmentService,
                    userService, llmProvider, mcpToolRegistrar, sessionAwareDispatcher,
                    skillCreatorService, toolSchemaRepository),
                "/ws/chat")
            .setAllowedOrigins("*");
    }

    /**
     * 配置 Tomcat WebSocket 容器参数
     */
    @org.springframework.context.annotation.Bean
    public org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean createWebSocketContainer() {
        var container = new org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(64 * 1024);
        container.setMaxBinaryMessageBufferSize(64 * 1024);
        container.setAsyncSendTimeout(10000L);
        container.setMaxSessionIdleTimeout(300000L);
        return container;
    }
}
