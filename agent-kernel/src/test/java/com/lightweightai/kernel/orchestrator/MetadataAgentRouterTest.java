package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.gateway.GatewayRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MetadataAgentRouter - 基于 metadata 的请求路由")
class MetadataAgentRouterTest {

    private AgentRegistry registry;
    private MetadataAgentRouter router;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();

        AgentProfile defaultAgent = AgentProfile.builder()
                .agentId("default-agent")
                .systemPrompt("default system prompt")
                .build();

        AgentProfile specialAgent = AgentProfile.builder()
                .agentId("special-agent")
                .systemPrompt("special system prompt")
                .build();

        registry.register(defaultAgent);
        registry.register(specialAgent);
        registry.setDefault("default-agent");

        router = new MetadataAgentRouter(registry);
    }

    @Nested
    @DisplayName("正常路由")
    class NormalRouting {

        @Test
        @DisplayName("metadata 中包含已注册的 agentId 时路由到该 agent")
        void routesToSpecifiedAgent() {
            GatewayRequest request = GatewayRequest.builder()
                    .message("hello")
                    .metadata("agentId", "special-agent")
                    .build();

            String result = router.route(request);

            assertEquals("special-agent", result);
        }

        @Test
        @DisplayName("metadata 中包含 default agent 的 agentId 也正常路由")
        void routesToDefaultAgentByExplicitId() {
            GatewayRequest request = GatewayRequest.builder()
                    .message("hello")
                    .metadata("agentId", "default-agent")
                    .build();

            String result = router.route(request);

            assertEquals("default-agent", result);
        }
    }

    @Nested
    @DisplayName("Fallback 到 default agent")
    class FallbackRouting {

        @Test
        @DisplayName("metadata 中无 agentId 时 fallback 到 default agent")
        void fallsBackWhenNoAgentId() {
            GatewayRequest request = GatewayRequest.builder()
                    .message("hello")
                    .build();

            String result = router.route(request);

            assertEquals("default-agent", result);
        }

        @Test
        @DisplayName("metadata 中 agentId 指向不存在的 agent 时 fallback")
        void fallsBackWhenAgentNotRegistered() {
            GatewayRequest request = GatewayRequest.builder()
                    .message("hello")
                    .metadata("agentId", "non-existent-agent")
                    .build();

            String result = router.route(request);

            assertEquals("default-agent", result);
        }

        @Test
        @DisplayName("metadata 中 agentId 为 null 值时 fallback")
        void fallsBackWhenAgentIdIsNull() {
            GatewayRequest request = GatewayRequest.builder()
                    .message("hello")
                    .metadata("agentId", null)
                    .build();

            String result = router.route(request);

            assertEquals("default-agent", result);
        }
    }

    @Nested
    @DisplayName("路由结果是 agentId 字符串")
    class RouteResultPayload {

        @Test
        @DisplayName("路由结果是可用于 registry.get() 的有效 agentId")
        void routeResultIsUsableAgentId() {
            GatewayRequest request = GatewayRequest.builder()
                    .message("hello")
                    .metadata("agentId", "special-agent")
                    .build();

            String routedId = router.route(request);

            assertTrue(registry.get(routedId).isPresent(),
                    "Routed agentId should exist in registry");
            assertEquals("special-agent", registry.get(routedId).get().getAgentId());
        }

        @Test
        @DisplayName("fallback 结果也是有效的 agentId")
        void fallbackResultIsValidAgentId() {
            GatewayRequest request = GatewayRequest.builder()
                    .message("hello")
                    .build();

            String routedId = router.route(request);

            assertTrue(registry.get(routedId).isPresent(),
                    "Fallback agentId should exist in registry");
        }
    }
}
