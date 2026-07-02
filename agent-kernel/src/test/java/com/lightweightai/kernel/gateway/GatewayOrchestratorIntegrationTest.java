package com.lightweightai.kernel.gateway;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.orchestrator.AgentFactory;
import com.lightweightai.kernel.orchestrator.MetadataAgentRouter;
import com.lightweightai.kernel.orchestrator.Orchestrator;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: Gateway + Orchestrator wired together.
 *
 * Covers the critical gap where Gateway.builder().chatHandler(orchestrator)
 * has never been tested end-to-end. Verifies:
 * - Sync path: Gateway.handle() → Orchestrator.chat()
 * - Reactive path: Gateway.handleStreamReactive() → Orchestrator.chatStreamReactive()
 * - Stream path: Gateway.handleStream() dispatches Orchestrator events to handler callbacks
 * - AGENT_ROUTE event is visible through Gateway's reactive stream
 * - PostProcessor pipeline applies to Orchestrator events
 */
@DisplayName("Integration: Gateway + Orchestrator wiring")
class GatewayOrchestratorIntegrationTest {

    private Orchestrator orchestrator;
    private CapturingLLMProvider spyProvider;

    @BeforeEach
    void setUp() {
        spyProvider = CapturingLLMProvider.endTurn("hello from agent");

        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("default")
                .systemPrompt("Test assistant")
                .build());
        registry.setDefault("default");

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
    @DisplayName("Gateway.handle() sync path reaches Orchestrator.chat() and returns response text")
    void syncPathReachesOrchestrator() {
        Gateway gateway = Gateway.builder()
                .chatHandler(orchestrator)
                .build();

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("sess-sync")
                .message("hi")
                .build();

        GatewayResponse response = gateway.handle(request);

        assertNotNull(response);
        assertEquals("hello from agent", response.getText());
        assertFalse(response.isError());
        assertTrue(response.getLatencyMs() >= 0);
        assertEquals(1, spyProvider.callCount(), "LLM provider should be called exactly once");
    }

    @Test
    @DisplayName("Gateway.handleStreamReactive() includes AGENT_ROUTE event from Orchestrator")
    void reactivePathIncludesAgentRouteEvent() {
        Gateway gateway = Gateway.builder()
                .chatHandler(orchestrator)
                .build();

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("sess-reactive")
                .message("hello")
                .build();

        List<StreamEvent> events = gateway.handleStreamReactive(request)
                .collectList().block();

        assertNotNull(events);
        assertFalse(events.isEmpty());

        // First event should be TRACE (user.message) from Gateway
        StreamEvent firstEvent = events.get(0);
        assertEquals(StreamEvent.EventType.TRACE, firstEvent.getType());

        // AGENT_ROUTE event from Orchestrator should be present
        boolean hasAgentRoute = events.stream()
                .anyMatch(e -> e.getType() == StreamEvent.EventType.AGENT_ROUTE);
        assertTrue(hasAgentRoute, "AGENT_ROUTE event from Orchestrator should flow through Gateway");

        // LLM_COMPLETE event should also be present
        boolean hasLlmComplete = events.stream()
                .anyMatch(e -> e.getType() == StreamEvent.EventType.LLM_COMPLETE);
        assertTrue(hasLlmComplete, "LLM_COMPLETE event should flow through Gateway");
    }

    @Test
    @DisplayName("Gateway.handleStream() dispatches Orchestrator events to GatewayStreamHandler")
    void streamPathDispatchesOrchestratorEvents() throws Exception {
        Gateway gateway = Gateway.builder()
                .chatHandler(orchestrator)
                .build();

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("sess-stream")
                .message("test")
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<GatewayResponse> responseRef = new AtomicReference<>();
        List<String> deltas = Collections.synchronizedList(new ArrayList<>());

        GatewayStreamHandler handler = new GatewayStreamHandler() {
            @Override
            public void onDelta(String delta) {
                deltas.add(delta);
            }

            @Override
            public void onComplete(GatewayResponse response) {
                responseRef.set(response);
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                latch.countDown();
            }
        };

        gateway.handleStream(request, handler);
        assertTrue(latch.await(10, TimeUnit.SECONDS), "handleStream should complete within timeout");

        GatewayResponse response = responseRef.get();
        assertNotNull(response, "Should receive a completed response");
        assertEquals("sess-stream", response.getSessionId());
    }

    @Test
    @DisplayName("Orchestrator.chat() sync path routes correctly and returns valid GatewayResponse")
    void orchestratorChatSyncPath() {
        GatewayRequest request = GatewayRequest.builder()
                .sessionId("sess-sync-direct")
                .message("direct sync test")
                .build();

        GatewayResponse response = orchestrator.chat(request);

        assertNotNull(response);
        assertEquals("hello from agent", response.getText());
        assertFalse(response.isError());
        assertEquals(1, spyProvider.callCount());
    }

    @Test
    @DisplayName("Orchestrator.chatStream() callback bridge works correctly")
    void orchestratorChatStreamCallbackBridge() throws Exception {
        GatewayRequest request = GatewayRequest.builder()
                .sessionId("sess-callback")
                .message("callback test")
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<GatewayResponse> responseRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        ChatHandler.StreamCallback callback = new ChatHandler.StreamCallback() {
            @Override
            public void onDelta(String delta, Map<String, Object> metadata) {}

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
        assertTrue(latch.await(10, TimeUnit.SECONDS));

        assertNull(errorRef.get(), "Should not produce an error");
        GatewayResponse response = responseRef.get();
        assertNotNull(response, "Callback should receive completed response");
        assertEquals("sess-callback", response.getSessionId());
    }

    @Test
    @DisplayName("Orchestrator.chatStreamReactive() with PostProcessor pipeline via Gateway")
    void reactivePathWithPostProcessor() {
        Gateway gateway = Gateway.builder()
                .chatHandler(orchestrator)
                .addPostProcessor(flux -> flux.map(event -> {
                    if (event.getType() == StreamEvent.EventType.LLM_COMPLETE) {
                        return StreamEvent.postProcessData("test_card", Map.of("injected", true));
                    }
                    return event;
                }))
                .build();

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("sess-pp")
                .message("post process test")
                .build();

        List<StreamEvent> events = gateway.handleStreamReactive(request)
                .collectList().block();

        assertNotNull(events);
        boolean hasPostProcessData = events.stream()
                .anyMatch(e -> e.getType() == StreamEvent.EventType.POST_PROCESS_DATA);
        assertTrue(hasPostProcessData,
                "PostProcessor should have transformed LLM_COMPLETE into POST_PROCESS_DATA");
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
