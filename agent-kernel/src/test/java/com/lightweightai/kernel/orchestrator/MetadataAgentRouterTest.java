package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.gateway.GatewayRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetadataAgentRouterTest {

    private AgentRegistry registry;
    private MetadataAgentRouter router;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
        registry.register(AgentProfile.builder().agentId("default-agent").build());
        registry.register(AgentProfile.builder().agentId("comfort-agent").build());
        registry.setDefault("default-agent");
        router = new MetadataAgentRouter(registry);
    }

    @Test
    void routesToAgentIdFromMetadata() {
        GatewayRequest req = GatewayRequest.builder()
                .message("hello")
                .metadata("agentId", "comfort-agent")
                .build();

        assertEquals("comfort-agent", router.route(req));
    }

    @Test
    void fallsBackToDefaultWhenAgentIdNotInRegistry() {
        GatewayRequest req = GatewayRequest.builder()
                .message("hello")
                .metadata("agentId", "nonexistent-agent")
                .build();

        assertEquals("default-agent", router.route(req));
    }

    @Test
    void fallsBackToDefaultWhenNoAgentIdInMetadata() {
        GatewayRequest req = GatewayRequest.builder()
                .message("hello")
                .build();

        assertEquals("default-agent", router.route(req));
    }

    @Test
    void agentIdConvertedViaToString() {
        GatewayRequest req = GatewayRequest.builder()
                .message("hello")
                .metadata("agentId", new Object() {
                    @Override
                    public String toString() {
                        return "comfort-agent";
                    }
                })
                .build();

        assertEquals("comfort-agent", router.route(req));
    }

    @Test
    void routingWithMultipleRegisteredAgents() {
        registry.register(AgentProfile.builder().agentId("analysis-agent").build());

        GatewayRequest req1 = GatewayRequest.builder()
                .message("hi")
                .metadata("agentId", "analysis-agent")
                .build();
        assertEquals("analysis-agent", router.route(req1));

        GatewayRequest req2 = GatewayRequest.builder()
                .message("hi")
                .metadata("agentId", "comfort-agent")
                .build();
        assertEquals("comfort-agent", router.route(req2));
    }
}
