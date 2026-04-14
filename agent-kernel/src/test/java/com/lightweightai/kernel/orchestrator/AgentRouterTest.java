package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.gateway.GatewayRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentRouter - Agent 路由策略")
class AgentRouterTest {

    private AgentRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
        registry.register(AgentProfile.builder().agentId("email").build());
        registry.register(AgentProfile.builder().agentId("code").build());
        registry.register(AgentProfile.builder().agentId("general").build());
        registry.setDefault("general");
    }

    @Test
    @DisplayName("metadata 中指定 agentId 时，路由到指定 Agent")
    void routeByMetadata() {
        MetadataAgentRouter router = new MetadataAgentRouter(registry);
        GatewayRequest request = GatewayRequest.builder()
                .message("分类这封邮件")
                .metadata("agentId", "email")
                .build();

        assertEquals("email", router.route(request));
    }

    @Test
    @DisplayName("metadata 无 agentId 时，fallback 到 default Agent")
    void fallbackToDefault() {
        MetadataAgentRouter router = new MetadataAgentRouter(registry);
        GatewayRequest request = GatewayRequest.builder()
                .message("你好")
                .build();

        assertEquals("general", router.route(request));
    }

    @Test
    @DisplayName("metadata 中 agentId 不存在时，fallback 到 default")
    void unknownAgentFallsBack() {
        MetadataAgentRouter router = new MetadataAgentRouter(registry);
        GatewayRequest request = GatewayRequest.builder()
                .message("test")
                .metadata("agentId", "nonexistent")
                .build();

        assertEquals("general", router.route(request));
    }
}
