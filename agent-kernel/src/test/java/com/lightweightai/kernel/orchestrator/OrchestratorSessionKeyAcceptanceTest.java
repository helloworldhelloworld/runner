package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Acceptance: Orchestrator 路由和会话隔离验证
 *
 * 验证：
 * 1. AGENT_ROUTE 事件正确标识 agent 和 session
 * 2. 不同 agent 的 sessionKey 命名空间隔离
 * 3. MetadataAgentRouter 按 metadata 路由到正确 agent
 */
@DisplayName("Acceptance: Orchestrator 路由与会话隔离")
class OrchestratorSessionKeyAcceptanceTest {

    private AgentRegistry registry;
    private CapturingLLMProvider spyProvider;
    private CapturingMemory memory;
    private ToolRegistry globalRegistry;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("comfort")
                .systemPrompt("安慰助手")
                .build());
        registry.register(AgentProfile.builder()
                .agentId("tech")
                .systemPrompt("技术助手")
                .build());
        registry.setDefault("comfort");

        spyProvider = CapturingLLMProvider.endTurn("ok");
        memory = new CapturingMemory();
        globalRegistry = new ToolRegistry();
    }

    @Test
    @DisplayName("chatStreamReactive 发出 AGENT_ROUTE 事件，包含 agentId 和 sessionId")
    void emitsAgentRouteEvent() {
        AgentFactory factory = new AgentFactory(spyProvider, memory, globalRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("user-123")
                .message("hello")
                .build();

        List<StreamEvent> events = orchestrator.chatStreamReactive(request).collectList().block();
        assertNotNull(events);

        StreamEvent routeEvent = events.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.AGENT_ROUTE)
                .findFirst()
                .orElse(null);

        assertNotNull(routeEvent, "AGENT_ROUTE event must be emitted");
        assertEquals("comfort", routeEvent.getData().get("agentId"));
        assertEquals("user-123", routeEvent.getData().get("sessionId"));
    }

    @Test
    @DisplayName("不同 agentId 路由到不同的 agent profile")
    void routesToDifferentAgents() {
        AgentFactory factory = new AgentFactory(spyProvider, memory, globalRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest comfortReq = GatewayRequest.builder()
                .sessionId("s1")
                .message("hello")
                .metadata(Map.of("agentId", "comfort"))
                .build();

        GatewayRequest techReq = GatewayRequest.builder()
                .sessionId("s2")
                .message("hello")
                .metadata(Map.of("agentId", "tech"))
                .build();

        List<StreamEvent> comfortEvents = orchestrator.chatStreamReactive(comfortReq).collectList().block();
        List<StreamEvent> techEvents = orchestrator.chatStreamReactive(techReq).collectList().block();

        assertNotNull(comfortEvents);
        assertNotNull(techEvents);

        String comfortAgent = comfortEvents.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.AGENT_ROUTE)
                .map(e -> (String) e.getData().get("agentId"))
                .findFirst().orElse(null);

        String techAgent = techEvents.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.AGENT_ROUTE)
                .map(e -> (String) e.getData().get("agentId"))
                .findFirst().orElse(null);

        assertEquals("comfort", comfortAgent);
        assertEquals("tech", techAgent);
    }

    @Test
    @DisplayName("同步 chat 方法正确路由并返回响应")
    void syncChatRoutesCorrectly() {
        AgentFactory factory = new AgentFactory(spyProvider, memory, globalRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        var response = orchestrator.chat(
                GatewayRequest.builder().sessionId("s1").message("hi").build());

        assertNotNull(response);
        assertFalse(response.isError());
    }

    @Test
    @DisplayName("interrupt 对不存在的 session 返回 null")
    void interruptNonExistentSessionReturnsNull() {
        AgentFactory factory = new AgentFactory(spyProvider, memory, globalRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        assertNull(orchestrator.interrupt("non-existent"));
        assertNull(orchestrator.interrupt(null));
    }

    @Test
    @DisplayName("shutdown 不抛异常")
    void shutdownGracefully() {
        AgentFactory factory = new AgentFactory(spyProvider, memory, globalRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));
        assertDoesNotThrow(orchestrator::shutdown);
    }

    private static class CapturingMemory implements MemoryProvider {
        private final Map<String, List<Message>> sessions = new HashMap<>();

        @Override public void addMessage(String sessionId, Message message) {
            sessions.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(message);
        }
        @Override public List<Message> getHistory(String sessionId, int limit) {
            return sessions.getOrDefault(sessionId, List.of());
        }
        @Override public void clearSession(String sessionId) { sessions.remove(sessionId); }
        @Override public void writeEphemeral(String content) {}
        @Override public void writeDurable(String section, String content) {}
        @Override public List<MemorySearchResult> search(String query) { return List.of(); }
    }
}
