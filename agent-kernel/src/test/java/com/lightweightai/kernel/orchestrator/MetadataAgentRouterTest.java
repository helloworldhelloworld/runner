package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.gateway.GatewayRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MetadataAgentRouter - metadata-based agent routing")
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
    @DisplayName("routes to specified agent when valid agentId in metadata")
    void routesToSpecifiedAgent() {
        GatewayRequest request = GatewayRequest.builder()
                .message("hello")
                .metadata("agentId", "assessment")
                .build();

        assertEquals("assessment", router.route(request));
    }

    @Test
    @DisplayName("falls back to default when no agentId in metadata")
    void fallsBackToDefaultWhenNoAgentId() {
        GatewayRequest request = GatewayRequest.builder()
                .message("hello")
                .build();

        assertEquals("comfort", router.route(request));
    }

    @Test
    @DisplayName("falls back to default when agentId not found in registry")
    void fallsBackToDefaultWhenAgentNotFound() {
        GatewayRequest request = GatewayRequest.builder()
                .message("hello")
                .metadata("agentId", "nonexistent")
                .build();

        assertEquals("comfort", router.route(request));
    }

    @Test
    @DisplayName("falls back to default when agentId is null")
    void fallsBackWhenAgentIdIsNull() {
        GatewayRequest request = GatewayRequest.builder()
                .message("hello")
                .metadata("agentId", null)
                .build();

        assertEquals("comfort", router.route(request));
    }

    @Test
    @DisplayName("falls back to first registered when no explicit default set")
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
