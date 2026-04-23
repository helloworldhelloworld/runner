package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.gateway.GatewayResponse;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Orchestrator session key 命名空间隔离")
class OrchestratorSessionKeyTest {

    private AgentRegistry registry;
    private SessionTrackingMemory memory;
    private Orchestrator orchestrator;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
        registry.register(AgentProfile.builder().agentId("agent-a").systemPrompt("A").build());
        registry.register(AgentProfile.builder().agentId("agent-b").systemPrompt("B").build());
        registry.setDefault("agent-a");

        memory = new SessionTrackingMemory();
        LLMProvider provider = new SimpleProvider();
        AgentFactory factory = new AgentFactory(provider, memory, new ToolRegistry());
        orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));
    }

    @AfterEach
    void tearDown() {
        orchestrator.shutdown();
    }

    @Test
    @DisplayName("同步路径 chat() 使用 agent:<agentId>:main:<sessionId> 命名空间")
    void syncChatUsesNamespacedSessionKey() {
        GatewayRequest request = GatewayRequest.builder()
                .sessionId("user-session-1")
                .message("hello")
                .metadata("agentId", "agent-a")
                .build();

        GatewayResponse resp = orchestrator.chat(request);
        assertNotNull(resp);

        assertTrue(memory.sessionKeysUsed.stream()
                        .anyMatch(k -> k.equals("agent:agent-a:main:user-session-1")),
                "memory should be written with namespaced key; keys used: " + memory.sessionKeysUsed);
    }

    @Test
    @DisplayName("不同 agent 路由后使用不同的 session key 命名空间")
    void differentAgentsGetDifferentNamespaces() {
        GatewayRequest reqA = GatewayRequest.builder()
                .sessionId("s1").message("hi").metadata("agentId", "agent-a").build();
        GatewayRequest reqB = GatewayRequest.builder()
                .sessionId("s1").message("hi").metadata("agentId", "agent-b").build();

        orchestrator.chatStreamReactive(reqA).blockLast();
        orchestrator.chatStreamReactive(reqB).blockLast();

        boolean hasA = memory.sessionKeysUsed.stream().anyMatch(k -> k.contains("agent-a"));
        boolean hasB = memory.sessionKeysUsed.stream().anyMatch(k -> k.contains("agent-b"));
        assertTrue(hasA, "agent-a namespace should exist");
        assertTrue(hasB, "agent-b namespace should exist");
    }

    @Test
    @DisplayName("reactive 路径首个事件是 AGENT_ROUTE 且 agentId 正确")
    void reactivePathEmitsRouteEvent() {
        GatewayRequest request = GatewayRequest.builder()
                .sessionId("s2").message("hi").metadata("agentId", "agent-b").build();

        List<StreamEvent> events = orchestrator.chatStreamReactive(request).collectList().block();
        assertNotNull(events);
        assertFalse(events.isEmpty());

        StreamEvent first = events.get(0);
        assertEquals(StreamEvent.EventType.AGENT_ROUTE, first.getType());
        assertEquals("agent-b", first.getData().get("agentId"));
    }

    @Test
    @DisplayName("chat() 返回的 GatewayResponse 包含正确的 requestId 和 sessionId")
    void chatResponseContainsCorrectIds() {
        GatewayRequest request = GatewayRequest.builder()
                .requestId("req-42")
                .sessionId("sess-42")
                .message("test")
                .build();

        GatewayResponse resp = orchestrator.chat(request);
        assertEquals("req-42", resp.getRequestId());
        assertEquals("sess-42", resp.getSessionId());
    }

    // ==================== Helpers ====================

    private static class SessionTrackingMemory implements MemoryProvider {
        final List<String> sessionKeysUsed = java.util.Collections.synchronizedList(new ArrayList<>());
        private final Map<String, List<Message>> store = new ConcurrentHashMap<>();

        @Override
        public void addMessage(String sessionId, Message message) {
            sessionKeysUsed.add(sessionId);
            store.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(message);
        }

        @Override
        public List<Message> getHistory(String sessionId, int limit) {
            List<Message> msgs = store.getOrDefault(sessionId, List.of());
            int start = Math.max(0, msgs.size() - limit);
            return new ArrayList<>(msgs.subList(start, msgs.size()));
        }

        @Override public void clearSession(String sessionId) {}
        @Override public void writeEphemeral(String content) {}
        @Override public void writeDurable(String section, String content) {}
        @Override public List<MemorySearchResult> search(String query) { return List.of(); }
    }

    private static class SimpleProvider implements LLMProvider {
        @Override
        public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent("ok").build())
                    .stopReason("end_turn").build();
        }

        @Override
        public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            LLMResponse resp = complete(m, o);
            return Flux.just(
                    StreamEvent.textDelta("ok"),
                    StreamEvent.llmComplete(resp));
        }

        @Override
        public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
            return CompletableFuture.completedFuture(complete(m, o));
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) {
            return CompletableFuture.completedFuture(complete(m, o));
        }

        @Override public ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return "simple"; }
    }
}
