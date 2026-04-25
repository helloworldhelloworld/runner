package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.gateway.GatewayRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MetadataAgentRouter - routes by agentId in request metadata")
class MetadataAgentRouterTest {

    private AgentRegistry registry;
    private MetadataAgentRouter router;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("general")
                .systemPrompt("general agent")
                .build());
        registry.register(AgentProfile.builder()
                .agentId("coder")
                .systemPrompt("coding agent")
                .build());
        registry.register(AgentProfile.builder()
                .agentId("analyst")
                .systemPrompt("analysis agent")
                .build());
        registry.setDefault("general");

        router = new MetadataAgentRouter(registry);
    }

    @Test
    @DisplayName("routes to agentId specified in metadata")
    void testRoutesToSpecifiedAgent() {
        GatewayRequest request = GatewayRequest.builder()
                .message("write code")
                .metadata("agentId", "coder")
                .build();

        assertEquals("coder", router.route(request));
    }

    @Test
    @DisplayName("routes to different agent based on metadata")
    void testRoutesDifferentAgent() {
        GatewayRequest request = GatewayRequest.builder()
                .message("analyze data")
                .metadata("agentId", "analyst")
                .build();

        assertEquals("analyst", router.route(request));
    }

    @Test
    @DisplayName("falls back to default when no agentId in metadata")
    void testFallbackToDefault() {
        GatewayRequest request = GatewayRequest.builder()
                .message("hello")
                .build();

        assertEquals("general", router.route(request));
    }

    @Test
    @DisplayName("falls back to default when agentId not found in registry")
    void testFallbackWhenAgentNotFound() {
        GatewayRequest request = GatewayRequest.builder()
                .message("hello")
                .metadata("agentId", "nonexistent")
                .build();

        assertEquals("general", router.route(request));
    }

    @Test
    @DisplayName("falls back to default when agentId is null in metadata")
    void testNullAgentId() {
        GatewayRequest request = GatewayRequest.builder()
                .message("hello")
                .metadata("agentId", null)
                .build();

        String routedId = router.route(request);
        assertEquals("general", routedId);
    }
}
