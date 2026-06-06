package com.lightweightai.web.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.orchestrator.MetadataAgentRouter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 传输链测试：入站 WS payload 的 {@code agentId} → GatewayRequest.metadata → MetadataAgentRouter 路由。
 *
 * 守护 voice → minion persona 的接线：缺这一段，经 WS 的请求永远落到 default agent
 * （见 todo/2026-06-01.md 的跨仓 TODO）。断言载荷一路传到被 router 消费的 metadata key。
 */
@DisplayName("WsChatRequests: 入站 payload → GatewayRequest（含 agentId 路由）")
class WsChatRequestsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode payload(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("agentId 出现在 metadata，并被 MetadataAgentRouter 路由到该 agent")
    void agentIdReachesRouter() {
        GatewayRequest req = WsChatRequests
                .baseBuilder(payload("{\"sessionId\":\"s1\",\"message\":\"挥个手\",\"agentId\":\"minion\"}"), "sock")
                .build();

        assertEquals("minion", req.getMetadata().get("agentId"), "agentId 应进 metadata");

        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder().agentId("default").build());
        registry.register(AgentProfile.builder().agentId("minion").systemPrompt("小黄人").build());
        registry.setDefault("default");

        assertEquals("minion", new MetadataAgentRouter(registry).route(req),
                "router 应据 metadata.agentId 路由到 minion");
    }

    @Test
    @DisplayName("无 agentId → metadata 不含该键（router fallback 到 default）")
    void absentAgentIdOmitted() {
        GatewayRequest req = WsChatRequests
                .baseBuilder(payload("{\"sessionId\":\"s1\",\"message\":\"hi\"}"), "sock")
                .build();
        assertFalse(req.getMetadata().containsKey("agentId"), "不应注入空 agentId");
    }

    @Test
    @DisplayName("sessionId 缺失时用默认值；protocol 恒为 websocket")
    void sessionIdDefaultAndProtocol() {
        GatewayRequest req = WsChatRequests
                .baseBuilder(payload("{\"message\":\"hi\"}"), "sock-123")
                .build();
        assertEquals("sock-123", req.getSessionId());
        assertEquals("websocket", req.getMetadata().get("protocol"));
    }

    @Test
    @DisplayName("model 出现在 metadata（既有行为不回归）")
    void modelReachesMetadata() {
        GatewayRequest req = WsChatRequests
                .baseBuilder(payload("{\"message\":\"hi\",\"model\":\"claude-x\"}"), "sock")
                .build();
        assertEquals("claude-x", req.getMetadata().get("model"));
    }
}
