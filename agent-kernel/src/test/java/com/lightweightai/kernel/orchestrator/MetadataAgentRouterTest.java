package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.gateway.GatewayRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MetadataAgentRouter - metadata 路由策略")
class MetadataAgentRouterTest {

    private AgentRegistry registry;
    private MetadataAgentRouter router;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
        registry.register(AgentProfile.builder().agentId("email").build());
        registry.register(AgentProfile.builder().agentId("code").build());
        registry.register(AgentProfile.builder().agentId("general").build());
        registry.setDefault("general");
        router = new MetadataAgentRouter(registry);
    }

    @Test
    @DisplayName("metadata 中有 agentId 且存在 → 路由到目标 Agent")
    void routesToExistingAgent() {
        GatewayRequest request = GatewayRequest.builder()
                .message("review this code")
                .metadata("agentId", "code")
                .build();

        String routed = router.route(request);

        assertEquals("code", routed);
    }

    @Test
    @DisplayName("metadata 中有 agentId=email → 路由到 email Agent")
    void routesToEmailAgent() {
        GatewayRequest request = GatewayRequest.builder()
                .message("分类这封邮件")
                .metadata("agentId", "email")
                .build();

        assertEquals("email", router.route(request));
    }

    @Test
    @DisplayName("metadata 中无 agentId → fallback 到 default")
    void fallbackWhenNoAgentId() {
        GatewayRequest request = GatewayRequest.builder()
                .message("hello")
                .build();

        assertEquals("general", router.route(request));
    }

    @Test
    @DisplayName("metadata 中 agentId 不存在于 registry → fallback 到 default")
    void fallbackWhenAgentNotRegistered() {
        GatewayRequest request = GatewayRequest.builder()
                .message("test")
                .metadata("agentId", "nonexistent-agent")
                .build();

        assertEquals("general", router.route(request));
    }

    @Test
    @DisplayName("metadata 中有其他字段但无 agentId → fallback 到 default")
    void fallbackWhenOtherMetadataPresent() {
        GatewayRequest request = GatewayRequest.builder()
                .message("test")
                .metadata("userId", "user-123")
                .metadata("channel", "web")
                .build();

        assertEquals("general", router.route(request));
    }

    @Test
    @DisplayName("agentId 作为整数传入时也能路由（toString 兼容）")
    void routesWithNonStringAgentId() {
        registry.register(AgentProfile.builder().agentId("42").build());
        GatewayRequest request = GatewayRequest.builder()
                .message("test")
                .metadata("agentId", 42)
                .build();

        assertEquals("42", router.route(request));
    }

    @Test
    @DisplayName("实现 AgentRouter 接口")
    void implementsAgentRouter() {
        assertTrue(router instanceof AgentRouter);
    }
}
