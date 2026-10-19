package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Acceptance: Orchestrator 按 session 隔离 Agent 实例和记忆。
 *
 * 验证:
 * 1. 不同 sessionId 的请求创建独立的 AgentLoop 实例
 * 2. 记忆写入使用 namespaced sessionKey (agent:<agentId>:main:<sessionId>)
 * 3. 一个 session 的对话历史不泄漏到另一个 session
 */
@DisplayName("Acceptance: Orchestrator session 隔离")
class OrchestratorSessionIsolationAcceptanceTest {

    @Test
    @DisplayName("不同 session 的记忆写入使用不同的 namespaced key")
    void differentSessionsUseNamespacedKeys() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("iso-agent")
                .systemPrompt("isolation test")
                .build());
        registry.setDefault("iso-agent");

        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("hello");
        TrackingMemory memory = new TrackingMemory();
        ToolRegistry tools = new ToolRegistry();

        AgentFactory factory = new AgentFactory(spy, memory, tools);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        // Session A
        GatewayRequest reqA = GatewayRequest.builder()
                .sessionId("session-A")
                .message("hello from A")
                .build();
        orchestrator.chatStreamReactive(reqA).collectList().block();

        // Session B
        GatewayRequest reqB = GatewayRequest.builder()
                .sessionId("session-B")
                .message("hello from B")
                .build();
        orchestrator.chatStreamReactive(reqB).collectList().block();

        Set<String> sessionKeys = memory.getSessionKeys();
        assertTrue(sessionKeys.contains("agent:iso-agent:main:session-A"),
                "Session A messages should use namespaced key; keys=" + sessionKeys);
        assertTrue(sessionKeys.contains("agent:iso-agent:main:session-B"),
                "Session B messages should use namespaced key; keys=" + sessionKeys);

        List<Message> histA = memory.getMessagesForSession("agent:iso-agent:main:session-A");
        List<Message> histB = memory.getMessagesForSession("agent:iso-agent:main:session-B");

        boolean aContainsFromA = histA.stream().anyMatch(m -> "hello from A".equals(m.getContent()));
        boolean bContainsFromB = histB.stream().anyMatch(m -> "hello from B".equals(m.getContent()));
        boolean aContainsFromB = histA.stream().anyMatch(m -> "hello from B".equals(m.getContent()));

        assertTrue(aContainsFromA, "Session A should contain 'hello from A'");
        assertTrue(bContainsFromB, "Session B should contain 'hello from B'");
        assertFalse(aContainsFromB, "Session A must NOT contain messages from session B");
    }

    @Test
    @DisplayName("每次 chatStreamReactive 调用都经过 AGENT_ROUTE 且 LLM_COMPLETE 事件")
    void eachCallProducesRouteAndComplete() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("evt-agent")
                .systemPrompt("test")
                .build());
        registry.setDefault("evt-agent");

        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");
        TrackingMemory memory = new TrackingMemory();
        ToolRegistry tools = new ToolRegistry();

        AgentFactory factory = new AgentFactory(spy, memory, tools);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("evt-sess")
                .message("ping")
                .build();

        List<StreamEvent> events = orchestrator.chatStreamReactive(request).collectList().block();
        assertNotNull(events);
        assertFalse(events.isEmpty());

        long routeCount = events.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.AGENT_ROUTE)
                .count();
        long completeCount = events.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.LLM_COMPLETE)
                .count();

        assertEquals(1, routeCount, "Exactly one AGENT_ROUTE event per request");
        assertTrue(completeCount >= 1, "At least one LLM_COMPLETE event per request");
    }

    private static class TrackingMemory implements MemoryProvider {
        private final ConcurrentHashMap<String, List<Message>> store = new ConcurrentHashMap<>();

        @Override
        public void addMessage(String sessionId, Message message) {
            store.computeIfAbsent(sessionId, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(message);
        }

        @Override
        public List<Message> getHistory(String sessionId, int limit) {
            List<Message> msgs = store.getOrDefault(sessionId, List.of());
            int start = Math.max(0, msgs.size() - limit);
            return new ArrayList<>(msgs.subList(start, msgs.size()));
        }

        @Override public void clearSession(String sessionId) { store.remove(sessionId); }
        @Override public void writeEphemeral(String content) {}
        @Override public void writeDurable(String section, String content) {}
        @Override public List<MemorySearchResult> search(String query) { return List.of(); }

        Set<String> getSessionKeys() { return store.keySet(); }

        List<Message> getMessagesForSession(String key) {
            return store.getOrDefault(key, List.of());
        }
    }
}
