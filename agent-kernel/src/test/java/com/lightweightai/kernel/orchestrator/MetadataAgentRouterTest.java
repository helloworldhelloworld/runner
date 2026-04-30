package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.gateway.GatewayRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MetadataAgentRouter — metadata 路由策略")
class MetadataAgentRouterTest {

    private AgentRegistry registry;
    private MetadataAgentRouter router;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("assistant")
                .systemPrompt("I am the default assistant")
                .build());
        registry.register(AgentProfile.builder()
                .agentId("coder")
                .systemPrompt("I write code")
                .build());
        registry.setDefault("assistant");
        router = new MetadataAgentRouter(registry);
    }

    @Test
    @DisplayName("metadata 中指定存在的 agentId 时路由到该 agent")
    void routesToSpecifiedAgent() {
        GatewayRequest request = GatewayRequest.builder()
                .message("write a function")
                .metadata("agentId", "coder")
                .build();

        assertEquals("coder", router.route(request));
    }

    @Test
    @DisplayName("metadata 中无 agentId 时 fallback 到 default agent")
    void fallsBackToDefaultWhenNoAgentId() {
        GatewayRequest request = GatewayRequest.builder()
                .message("hello")
                .build();

        assertEquals("assistant", router.route(request));
    }

    @Test
    @DisplayName("metadata 中指定不存在的 agentId 时 fallback 到 default agent")
    void fallsBackToDefaultWhenAgentIdNotFound() {
        GatewayRequest request = GatewayRequest.builder()
                .message("hello")
                .metadata("agentId", "nonexistent")
                .build();

        assertEquals("assistant", router.route(request));
    }

    @Test
    @DisplayName("metadata 中 agentId 为 null 时 fallback 到 default agent")
    void fallsBackToDefaultWhenAgentIdNull() {
        GatewayRequest request = GatewayRequest.builder()
                .message("hello")
                .metadata("agentId", null)
                .build();

        assertEquals("assistant", router.route(request));
    }

    @Test
    @DisplayName("路由结果为实际的 agentId 字符串，不是 profile 对象")
    void routeReturnsStringAgentId() {
        GatewayRequest request = GatewayRequest.builder()
                .message("hello")
                .metadata("agentId", "coder")
                .build();

        String routedId = router.route(request);
        assertTrue(registry.get(routedId).isPresent());
        assertEquals("coder", registry.get(routedId).get().getAgentId());
    }
}
