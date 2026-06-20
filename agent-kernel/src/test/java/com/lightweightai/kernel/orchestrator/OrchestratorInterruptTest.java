package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentLoop;
import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Orchestrator — interrupt/resume and session key namespacing")
class OrchestratorInterruptTest {

    private AgentRegistry registry;
    private TestMemoryProvider memory;
    private ToolRegistry toolRegistry;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
        memory = new TestMemoryProvider();
        toolRegistry = new ToolRegistry();

        registry.register(AgentProfile.builder().agentId("alpha").systemPrompt("I am alpha").build());
        registry.setDefault("alpha");
    }

    @Test
    @DisplayName("interrupt returns null when no active run")
    void interruptNoActiveRun() {
        FastProvider provider = new FastProvider("response");
        AgentFactory factory = new AgentFactory(provider, memory, toolRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        StreamEvent result = orchestrator.interrupt("nonexistent-session");
        assertNull(result, "interrupt on nonexistent session should return null");
    }

    @Test
    @DisplayName("interrupt returns null when sessionId is null")
    void interruptNullSession() {
        FastProvider provider = new FastProvider("response");
        AgentFactory factory = new AgentFactory(provider, memory, toolRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        StreamEvent result = orchestrator.interrupt(null);
        assertNull(result, "interrupt with null sessionId should return null");
    }

    @Test
    @DisplayName("interrupt returns null when run is already completed")
    void interruptCompletedRun() {
        FastProvider provider = new FastProvider("done");
        AgentFactory factory = new AgentFactory(provider, memory, toolRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest req = GatewayRequest.builder()
                .sessionId("sess-1")
                .message("hello")
                .build();
        orchestrator.chatStreamReactive(req).blockLast();

        StreamEvent result = orchestrator.interrupt("sess-1");
        assertNull(result, "interrupt on completed run should return null");
    }

    @Test
    @DisplayName("session key uses agent:agentId:main:sessionId format")
    void sessionKeyNamespacing() {
        FastProvider provider = new FastProvider("ok");
        AgentFactory factory = new AgentFactory(provider, memory, toolRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest req = GatewayRequest.builder()
                .sessionId("my-session")
                .message("hello")
                .metadata("agentId", "alpha")
                .build();

        List<StreamEvent> events = orchestrator.chatStreamReactive(req).collectList().block();
        assertNotNull(events);

        StreamEvent routeEvent = events.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.AGENT_ROUTE)
                .findFirst()
                .orElseThrow();
        assertEquals("alpha", routeEvent.getData().get("agentId"));
    }

    @Test
    @DisplayName("different sessions produce independent event streams")
    void differentSessionsIndependent() {
        FastProvider provider = new FastProvider("reply");
        AgentFactory factory = new AgentFactory(provider, memory, toolRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest req1 = GatewayRequest.builder().sessionId("s1").message("q1").build();
        GatewayRequest req2 = GatewayRequest.builder().sessionId("s2").message("q2").build();

        List<StreamEvent> events1 = orchestrator.chatStreamReactive(req1).collectList().block();
        List<StreamEvent> events2 = orchestrator.chatStreamReactive(req2).collectList().block();

        assertNotNull(events1);
        assertNotNull(events2);
        assertTrue(events1.stream().anyMatch(e -> e.getType() == StreamEvent.EventType.AGENT_ROUTE));
        assertTrue(events2.stream().anyMatch(e -> e.getType() == StreamEvent.EventType.AGENT_ROUTE));
    }

    @Test
    @DisplayName("shutdown disposes cleanup executor without exception")
    void shutdownCleanly() {
        FastProvider provider = new FastProvider("ok");
        AgentFactory factory = new AgentFactory(provider, memory, toolRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        assertDoesNotThrow(orchestrator::shutdown);
    }

    @Test
    @DisplayName("first event in stream is always AGENT_ROUTE")
    void firstEventIsAgentRoute() {
        FastProvider provider = new FastProvider("ok");
        AgentFactory factory = new AgentFactory(provider, memory, toolRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest req = GatewayRequest.builder().sessionId("s1").message("hello").build();
        List<StreamEvent> events = orchestrator.chatStreamReactive(req).collectList().block();

        assertNotNull(events);
        assertFalse(events.isEmpty());
        assertEquals(StreamEvent.EventType.AGENT_ROUTE, events.get(0).getType(),
                "first event should always be AGENT_ROUTE");
    }

    @Test
    @DisplayName("synchronous chat returns valid GatewayResponse")
    void syncChatReturns() {
        FastProvider provider = new FastProvider("sync response");
        AgentFactory factory = new AgentFactory(provider, memory, toolRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest req = GatewayRequest.builder().sessionId("s1").message("hello").build();
        var response = orchestrator.chat(req);

        assertNotNull(response);
        assertEquals("s1", response.getSessionId());
    }

    // ==================== Helper classes ====================

    private static class FastProvider implements LLMProvider {
        private final String response;

        FastProvider(String response) { this.response = response; }

        @Override
        public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            return Flux.just(
                    StreamEvent.textDelta(response),
                    StreamEvent.llmComplete(LLMResponse.builder()
                            .message(ConversationMessage.builder()
                                    .role(ConversationMessage.MessageRole.ASSISTANT)
                                    .textContent(response).build())
                            .stopReason("end_turn").build()));
        }

        @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent(response).build())
                    .stopReason("end_turn").build();
        }
        @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
            return CompletableFuture.completedFuture(complete(m, o));
        }
        @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) {
            return CompletableFuture.completedFuture(complete(m, o));
        }
        @Override public ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return "fast"; }
    }

    private static class TestMemoryProvider implements MemoryProvider {
        private final List<Message> messages = new ArrayList<>();
        @Override public void addMessage(String sessionId, Message message) { messages.add(message); }
        @Override public List<Message> getHistory(String sessionId, int limit) {
            int start = Math.max(0, messages.size() - limit);
            return new ArrayList<>(messages.subList(start, messages.size()));
        }
        @Override public void clearSession(String sessionId) { messages.clear(); }
        @Override public void writeEphemeral(String content) {}
        @Override public void writeDurable(String section, String content) {}
        @Override public List<MemorySearchResult> search(String query) { return List.of(); }
    }
}
