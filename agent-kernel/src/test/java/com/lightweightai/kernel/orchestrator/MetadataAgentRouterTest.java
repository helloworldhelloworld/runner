package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.gateway.GatewayRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MetadataAgentRouter — routes by agentId in request metadata")
class MetadataAgentRouterTest {

    private AgentRegistry registry;
    private MetadataAgentRouter router;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
            .agentId("counselor")
            .systemPrompt("I'm a counselor")
            .build());
        registry.register(AgentProfile.builder()
            .agentId("analyst")
            .systemPrompt("I'm an analyst")
            .build());
        router = new MetadataAgentRouter(registry);
    }

    @Test
    @DisplayName("routes to matching agentId from metadata")
    void routesToMatchingAgent() {
        GatewayRequest request = GatewayRequest.builder()
            .message("hello")
            .metadata("agentId", "analyst")
            .build();

        String result = router.route(request);
        assertEquals("analyst", result);
    }

    @Test
    @DisplayName("falls back to default when no agentId in metadata")
    void fallsBackToDefault_noAgentId() {
        GatewayRequest request = GatewayRequest.builder()
            .message("hello")
            .build();

        String result = router.route(request);
        assertEquals(registry.getDefault().getAgentId(), result);
    }

    @Test
    @DisplayName("falls back to default when agentId not found in registry")
    void fallsBackToDefault_unknownAgentId() {
        GatewayRequest request = GatewayRequest.builder()
            .message("hello")
            .metadata("agentId", "nonexistent")
            .build();

        String result = router.route(request);
        assertEquals(registry.getDefault().getAgentId(), result);
    }

    @Test
    @DisplayName("routes correctly with multiple agents registered")
    void routesCorrectlyWithMultipleAgents() {
        GatewayRequest req1 = GatewayRequest.builder()
            .message("hi")
            .metadata("agentId", "counselor")
            .build();
        GatewayRequest req2 = GatewayRequest.builder()
            .message("hi")
            .metadata("agentId", "analyst")
            .build();

        assertEquals("counselor", router.route(req1));
        assertEquals("analyst", router.route(req2));
    }
}
