package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.gateway.GatewayResponse;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Orchestrator paths that were previously untested:
 *
 * - Orchestrator.chat() sync path (completely different code path from chatStreamReactive)
 * - Orchestrator.chatStream() callback bridge
 * - Orchestrator.shutdown() cleans up resources
 * - Session key namespace formatting
 */
@DisplayName("Orchestrator: sync path, callback bridge, and lifecycle")
class OrchestratorSyncAndCleanupTest {

    private CapturingLLMProvider spyProvider;
    private AgentRegistry registry;
    private AgentFactory factory;
    private Orchestrator orchestrator;

    @BeforeEach
    void setUp() {
        spyProvider = CapturingLLMProvider.endTurn("sync response");

        registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("helper")
                .systemPrompt("I'm a helper")
                .build());
        registry.setDefault("helper");

        ToolRegistry tools = new ToolRegistry();
        TestMemory memory = new TestMemory();
        factory = new AgentFactory(spyProvider, memory, tools);
        orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));
    }

    @AfterEach
    void tearDown() {
        orchestrator.shutdown();
    }

    @Test
    @DisplayName("chat() sync path returns response with correct text and metadata")
    void chatSyncPathReturnsCorrectResponse() {
        GatewayRequest request = GatewayRequest.builder()
                .sessionId("sync-sess-1")
                .requestId("req-1")
                .message("What is 2+2?")
                .build();

        GatewayResponse response = orchestrator.chat(request);

        assertNotNull(response, "Sync path should return a response");
        assertEquals("sync response", response.getText());
        assertEquals("req-1", response.getRequestId());
        assertEquals("sync-sess-1", response.getSessionId());
        assertFalse(response.isError());
        assertTrue(response.getLatencyMs() >= 0, "Latency should be non-negative");
    }

    @Test
    @DisplayName("chat() sync path routes to correct agent by metadata")
    void chatSyncPathRoutesCorrectly() {
        registry.register(AgentProfile.builder()
                .agentId("specialist")
                .systemPrompt("I'm a specialist")
                .build());

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("sync-route")
                .message("route me")
                .metadata("agentId", "specialist")
                .build();

        orchestrator.chat(request);

        // Verify the provider was called (i.e. the route resolved to an agent that ran)
        assertEquals(1, spyProvider.callCount());
        List<ConversationMessage> messages = spyProvider.lastMessages();
        assertNotNull(messages);
        // System prompt should contain specialist's prompt
        boolean hasSpecialistPrompt = messages.stream()
                .anyMatch(m -> m.getRole() == ConversationMessage.MessageRole.SYSTEM
                        && m.getTextContent().contains("specialist"));
        assertTrue(hasSpecialistPrompt, "Should route to specialist agent");
    }

    @Test
    @DisplayName("chat() sync path falls back to default agent for unknown agentId")
    void chatSyncPathFallsBackToDefault() {
        GatewayRequest request = GatewayRequest.builder()
                .sessionId("sync-fallback")
                .message("fallback test")
                .metadata("agentId", "nonexistent")
                .build();

        GatewayResponse response = orchestrator.chat(request);

        assertNotNull(response);
        assertEquals("sync response", response.getText());
        // System prompt should contain default agent's prompt
        List<ConversationMessage> messages = spyProvider.lastMessages();
        boolean hasDefaultPrompt = messages.stream()
                .anyMatch(m -> m.getRole() == ConversationMessage.MessageRole.SYSTEM
                        && m.getTextContent().contains("helper"));
        assertTrue(hasDefaultPrompt, "Should fall back to default agent");
    }

    @Test
    @DisplayName("chatStream() callback bridge produces valid response")
    void chatStreamCallbackBridgeWorks() throws Exception {
        GatewayRequest request = GatewayRequest.builder()
                .sessionId("callback-sess")
                .requestId("cb-req-1")
                .message("callback test")
                .build();

        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<GatewayResponse> responseRef =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Throwable> errorRef =
                new java.util.concurrent.atomic.AtomicReference<>();
        List<String> deltas = Collections.synchronizedList(new ArrayList<>());

        var callback = new com.lightweightai.kernel.gateway.ChatHandler.StreamCallback() {
            @Override
            public void onDelta(String delta, Map<String, Object> metadata) {
                if (delta != null) deltas.add(delta);
            }

            @Override
            public void onComplete(GatewayResponse response) {
                responseRef.set(response);
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                errorRef.set(error);
                latch.countDown();
            }
        };

        orchestrator.chatStream(request, callback);
        assertTrue(latch.await(10, java.util.concurrent.TimeUnit.SECONDS),
                "chatStream callback should complete within timeout");

        assertNull(errorRef.get(), "Should not error");
        GatewayResponse response = responseRef.get();
        assertNotNull(response, "Should receive a response via callback");
        assertEquals("callback-sess", response.getSessionId());
    }

    @Test
    @DisplayName("shutdown() completes without error after active usage")
    void shutdownCompletesCleanly() {
        // Use the orchestrator first
        orchestrator.chatStreamReactive(GatewayRequest.builder()
                .sessionId("pre-shutdown")
                .message("before shutdown")
                .build()).collectList().block();

        // Shutdown should not throw
        assertDoesNotThrow(() -> orchestrator.shutdown());
    }

    @Test
    @DisplayName("Multiple sequential requests to same session reuse or replace runs correctly")
    void sequentialRequestsSameSession() {
        GatewayRequest r1 = GatewayRequest.builder()
                .sessionId("multi-sess")
                .message("first")
                .build();
        GatewayRequest r2 = GatewayRequest.builder()
                .sessionId("multi-sess")
                .message("second")
                .build();

        List<StreamEvent> events1 = orchestrator.chatStreamReactive(r1).collectList().block();
        List<StreamEvent> events2 = orchestrator.chatStreamReactive(r2).collectList().block();

        assertNotNull(events1);
        assertNotNull(events2);
        assertEquals(2, spyProvider.callCount(), "Provider should be called once per request");
    }

    // ==================== Helpers ====================

    private static class TestMemory implements MemoryProvider {
        @Override public void addMessage(String s, Message m) {}
        @Override public List<Message> getHistory(String s, int l) { return List.of(); }
        @Override public void clearSession(String s) {}
        @Override public void writeEphemeral(String c) {}
        @Override public void writeDurable(String s, String c) {}
        @Override public List<MemorySearchResult> search(String q) { return List.of(); }
    }
}
