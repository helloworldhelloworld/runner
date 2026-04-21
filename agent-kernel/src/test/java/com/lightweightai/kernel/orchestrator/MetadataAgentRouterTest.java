package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.gateway.GatewayRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MetadataAgentRouter - 基于 metadata 的 Agent 路由")
class MetadataAgentRouterTest {

    private AgentRegistry registry;
    private MetadataAgentRouter router;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("comfort")
                .systemPrompt("I provide comfort")
                .build());
        registry.register(AgentProfile.builder()
                .agentId("assessment")
                .systemPrompt("I assess")
                .build());
        registry.setDefault("comfort");
        router = new MetadataAgentRouter(registry);
    }

    @Test
    @DisplayName("metadata 中包含有效 agentId 时路由到指定 agent")
    void routesToSpecifiedAgent() {
        GatewayRequest request = GatewayRequest.builder()
                .message("hello")
                .metadata("agentId", "assessment")
                .build();

        assertEquals("assessment", router.route(request));
    }

    @Test
    @DisplayName("metadata 中无 agentId 时 fallback 到 default agent")
    void fallsBackToDefaultWhenNoAgentId() {
        GatewayRequest request = GatewayRequest.builder()
                .message("hello")
                .build();

        assertEquals("comfort", router.route(request));
    }

    @Test
    @DisplayName("metadata 中 agentId 不存在于 registry 时 fallback 到 default")
    void fallsBackToDefaultWhenAgentNotFound() {
        GatewayRequest request = GatewayRequest.builder()
                .message("hello")
                .metadata("agentId", "nonexistent")
                .build();

        assertEquals("comfort", router.route(request));
    }

    @Test
    @DisplayName("agentId 为 null 时 fallback 到 default")
    void fallsBackWhenAgentIdIsNull() {
        GatewayRequest request = GatewayRequest.builder()
                .message("hello")
                .metadata("agentId", null)
                .build();

        assertEquals("comfort", router.route(request));
    }

    @Test
    @DisplayName("未设置显式 default 时路由到第一个注册的 agent")
    void fallsBackToFirstRegisteredWhenNoExplicitDefault() {
        AgentRegistry reg = new AgentRegistry();
        reg.register(AgentProfile.builder().agentId("first").build());
        reg.register(AgentProfile.builder().agentId("second").build());
        MetadataAgentRouter r = new MetadataAgentRouter(reg);

        GatewayRequest request = GatewayRequest.builder()
                .message("hello")
                .build();

        String result = r.route(request);
        assertNotNull(result);
    }
}
