package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.gateway.GatewayRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MetadataAgentRouter - 按 metadata 路由请求到 Agent")
class MetadataAgentRouterTest {

    private AgentRegistry registry;
    private MetadataAgentRouter router;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("counselor")
                .systemPrompt("I am a counselor")
                .maxSpawnDepth(2)
                .build());
        registry.register(AgentProfile.builder()
                .agentId("coder")
                .systemPrompt("I am a coder")
                .build());
        registry.setDefault("counselor");
        router = new MetadataAgentRouter(registry);
    }

    @Test
    @DisplayName("metadata 中指定 agentId 时路由到对应 agent")
    void routesToSpecifiedAgent() {
        GatewayRequest request = GatewayRequest.builder()
                .message("help me code")
                .metadata("agentId", "coder")
                .build();

        assertEquals("coder", router.route(request));
    }

    @Test
    @DisplayName("metadata 中无 agentId 时回退到 default agent")
    void fallsBackToDefaultWhenNoAgentId() {
        GatewayRequest request = GatewayRequest.builder()
                .message("hello")
                .build();

        assertEquals("counselor", router.route(request));
    }

    @Test
    @DisplayName("metadata 中 agentId 不存在于注册表时回退到 default agent")
    void fallsBackToDefaultWhenAgentNotFound() {
        GatewayRequest request = GatewayRequest.builder()
                .message("hello")
                .metadata("agentId", "nonexistent")
                .build();

        assertEquals("counselor", router.route(request));
    }

    @Test
    @DisplayName("metadata 中 agentId 为 null 时回退到 default agent")
    void fallsBackToDefaultWhenAgentIdIsNull() {
        GatewayRequest request = GatewayRequest.builder()
                .message("hello")
                .metadata("agentId", null)
                .build();

        assertEquals("counselor", router.route(request));
    }

    @Test
    @DisplayName("metadata 中 agentId 存在但与注册 id 精确匹配（大小写敏感）")
    void routingIsCaseSensitive() {
        GatewayRequest request = GatewayRequest.builder()
                .message("hello")
                .metadata("agentId", "Coder") // capital C
                .build();

        // "Coder" doesn't match "coder" — should fall back to default
        assertEquals("counselor", router.route(request));
    }

    @Test
    @DisplayName("agentId 为非 String 类型时 toString() 后匹配")
    void handlesNonStringAgentId() {
        // Unlikely but defensive: agentId could be set as a non-String object
        GatewayRequest request = GatewayRequest.builder()
                .message("hello")
                .metadata("agentId", new StringBuilder("coder"))
                .build();

        assertEquals("coder", router.route(request));
    }
}
