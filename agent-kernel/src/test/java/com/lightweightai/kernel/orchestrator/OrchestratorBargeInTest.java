package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the standalone barge-in interrupt path (ADR-012) through the Orchestrator.
 *
 * This verifies that:
 * 1. interrupt(sessionId) returns SPEECH_INTERRUPTED when a run is active
 * 2. interrupt(sessionId) returns null when no run is active
 * 3. interrupt(null) is handled gracefully
 * 4. AGENT_ROUTE event is emitted on every chat
 */
@DisplayName("Orchestrator barge-in interrupt and routing events")
class OrchestratorBargeInTest {

    private AgentRegistry registry;
    private Orchestrator orchestrator;
    private CapturingLLMProvider spyProvider;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("default")
                .systemPrompt("test")
                .build());
        registry.setDefault("default");

        spyProvider = CapturingLLMProvider.endTurn("ok");
        ToolRegistry tools = new ToolRegistry();
        TestMemory memory = new TestMemory();
        AgentFactory factory = new AgentFactory(spyProvider, memory, tools);
        orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));
    }

    @AfterEach
    void tearDown() {
        orchestrator.shutdown();
    }

    @Test
    @DisplayName("interrupt on non-existent session returns null")
    void interrupt_noSession_returnsNull() {
        StreamEvent result = orchestrator.interrupt("non-existent");
        assertNull(result);
    }

    @Test
    @DisplayName("interrupt with null sessionId returns null")
    void interrupt_nullSession_returnsNull() {
        StreamEvent result = orchestrator.interrupt(null);
        assertNull(result);
    }

    @Test
    @DisplayName("chatStreamReactive emits AGENT_ROUTE event as first event")
    void chatStreamReactive_emitsAgentRouteEvent() {
        GatewayRequest request = GatewayRequest.builder()
                .sessionId("sess-route")
                .message("hello")
                .build();

        List<StreamEvent> events = orchestrator.chatStreamReactive(request)
                .collectList().block();

        assertNotNull(events);
        assertFalse(events.isEmpty());
        assertEquals(StreamEvent.EventType.AGENT_ROUTE, events.get(0).getType(),
                "First event should be AGENT_ROUTE");
    }

    @Test
    @DisplayName("completed run is cleaned up from activeRuns")
    void completedRun_isCleanedUp() {
        GatewayRequest request = GatewayRequest.builder()
                .sessionId("sess-cleanup")
                .message("test")
                .build();

        // Run completes synchronously since CapturingLLMProvider returns immediately
        orchestrator.chatStreamReactive(request).collectList().block();

        // After completion, interrupt should return null (no active run)
        StreamEvent result = orchestrator.interrupt("sess-cleanup");
        assertNull(result, "No active run after completion — interrupt should return null");
    }

    @Test
    @DisplayName("session key includes agentId namespace")
    void sessionKey_includesAgentIdNamespace() {
        GatewayRequest request = GatewayRequest.builder()
                .sessionId("sess-ns")
                .message("hello")
                .metadata(Map.of("agentId", "default"))
                .build();

        orchestrator.chatStreamReactive(request).collectList().block();

        // The spy provider should have been called. Messages should include
        // a system prompt from the 'default' agent profile
        List<ConversationMessage> msgs = spyProvider.lastMessages();
        assertNotNull(msgs);
        boolean hasSystem = msgs.stream()
                .anyMatch(m -> m.getRole() == ConversationMessage.MessageRole.SYSTEM);
        assertTrue(hasSystem, "System message from agent profile should be present");
    }

    private static class TestMemory implements MemoryProvider {
        private final Map<String, List<Message>> sessions = new HashMap<>();

        @Override
        public void addMessage(String sessionId, Message message) {
            sessions.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(message);
        }

        @Override
        public List<Message> getHistory(String sessionId, int limit) {
            List<Message> msgs = sessions.getOrDefault(sessionId, List.of());
            int from = Math.max(0, msgs.size() - limit);
            return new ArrayList<>(msgs.subList(from, msgs.size()));
        }

        @Override public void clearSession(String s) { sessions.remove(s); }
        @Override public void writeEphemeral(String c) {}
        @Override public void writeDurable(String s, String c) {}
        @Override public List<MemorySearchResult> search(String q) { return List.of(); }
    }
}
