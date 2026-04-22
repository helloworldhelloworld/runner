package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.gateway.GatewayRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MetadataAgentRouter - Routes requests by agentId in metadata")
class MetadataAgentRouterTest {

    private AgentRegistry registry;
    private MetadataAgentRouter router;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("main")
                .systemPrompt("Main agent")
                .build());
        registry.register(AgentProfile.builder()
                .agentId("worker")
                .systemPrompt("Worker agent")
                .build());
        registry.setDefault("main");

        router = new MetadataAgentRouter(registry);
    }

    @Test
    @DisplayName("Routes to specified agentId when it exists in registry")
    void routesToSpecifiedAgentIdWhenExists() {
        GatewayRequest request = GatewayRequest.builder()
                .message("hello")
                .metadata("agentId", "worker")
                .build();

        String routed = router.route(request);

        assertEquals("worker", routed);
    }

    @Test
    @DisplayName("Falls back to default agent when agentId not in registry")
    void fallsBackToDefaultWhenAgentIdNotInRegistry() {
        GatewayRequest request = GatewayRequest.builder()
                .message("hello")
                .metadata("agentId", "nonexistent")
                .build();

        String routed = router.route(request);

        assertEquals("main", routed);
    }

    @Test
    @DisplayName("Falls back to default agent when no agentId in metadata")
    void fallsBackToDefaultWhenNoAgentIdInMetadata() {
        GatewayRequest request = GatewayRequest.builder()
                .message("hello")
                .build();

        String routed = router.route(request);

        assertEquals("main", routed);
    }

    @Test
    @DisplayName("Falls back to default agent when agentId metadata value is null")
    void fallsBackToDefaultWhenAgentIdIsNull() {
        GatewayRequest request = GatewayRequest.builder()
                .message("hello")
                .metadata("agentId", null)
                .build();

        String routed = router.route(request);

        assertEquals("main", routed);
    }

    @Test
    @DisplayName("Routes correctly with non-string agentId value via toString")
    void routesCorrectlyWithNonStringAgentId() {
        registry.register(AgentProfile.builder()
                .agentId("42")
                .systemPrompt("Numeric agent")
                .build());

        GatewayRequest request = GatewayRequest.builder()
                .message("hello")
                .metadata("agentId", 42)
                .build();

        String routed = router.route(request);

        assertEquals("42", routed);
    }
}
