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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Transmission chain test: Orchestrator interrupt/resume event payloads
 *
 * Verifies that when a running session is interrupted and resumed:
 * 1. AGENT_INTERRUPT event carries correct runId, phase, textLength
 * 2. AGENT_RESUME event carries runId, newInput, contextSize
 * 3. The second call to chatStreamReactive triggers interrupt + resume flow
 *
 * This is the kind of wiring bug that unit tests on InterruptibleRun alone
 * would miss — we need to prove the Orchestrator actually invokes interrupt()
 * and passes the event into the output stream.
 */
@DisplayName("Acceptance: Orchestrator interrupt/resume event transmission")
class OrchestratorInterruptResumeTransmissionTest {

    private AgentRegistry registry;
    private ToolRegistry toolRegistry;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("default")
                .systemPrompt("Test agent")
                .maxSpawnDepth(0)
                .build());
        registry.setDefault("default");

        toolRegistry = new ToolRegistry();
    }

    @Test
    @DisplayName("第二次请求触发 AGENT_INTERRUPT 事件并携带 phase 和 textLength")
    void secondRequestTriggersInterruptWithPayload() throws Exception {
        // Use a slow provider that blocks to keep first run alive
        SlowLLMProvider slowProvider = new SlowLLMProvider();
        TestMemory memory = new TestMemory();
        AgentFactory factory = new AgentFactory(slowProvider, memory, toolRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest firstRequest = GatewayRequest.builder()
                .sessionId("interrupt-test")
                .message("first message")
                .build();

        // Start first request (subscribe to start the run, but don't block for completion)
        CountDownLatch firstStarted = new CountDownLatch(1);
        List<StreamEvent> firstEvents = Collections.synchronizedList(new ArrayList<>());
        var subscription = orchestrator.chatStreamReactive(firstRequest)
                .doOnNext(event -> {
                    firstEvents.add(event);
                    if (event.getType() == StreamEvent.EventType.AGENT_ROUTE) {
                        firstStarted.countDown();
                    }
                })
                .subscribe();

        // Wait for first request to start running
        assertTrue(firstStarted.await(2, TimeUnit.SECONDS), "first request should start");

        // Now send second request on same session — should trigger interrupt
        GatewayRequest secondRequest = GatewayRequest.builder()
                .sessionId("interrupt-test")
                .message("second message (interrupting)")
                .build();

        List<StreamEvent> secondEvents = orchestrator.chatStreamReactive(secondRequest)
                .collectList().block();

        assertNotNull(secondEvents);
        subscription.dispose();

        // Verify AGENT_ROUTE is present
        boolean hasRoute = secondEvents.stream()
                .anyMatch(e -> e.getType() == StreamEvent.EventType.AGENT_ROUTE);
        assertTrue(hasRoute, "second request should emit AGENT_ROUTE");

        // Verify AGENT_INTERRUPT is present with correct payload
        Optional<StreamEvent> interruptEvent = secondEvents.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.AGENT_INTERRUPT)
                .findFirst();
        assertTrue(interruptEvent.isPresent(), "should emit AGENT_INTERRUPT when interrupting");

        Map<String, Object> data = interruptEvent.get().getData();
        assertNotNull(data, "AGENT_INTERRUPT must carry data payload");
        assertNotNull(data.get("runId"), "must carry runId");
        assertNotNull(data.get("phase"), "must carry phase");
        assertTrue(data.containsKey("textLength"), "must carry textLength");

        orchestrator.shutdown();
    }

    @Test
    @DisplayName("AGENT_ROUTE 事件包含正确的 agentId 和 sessionId")
    void agentRouteEventCarriesCorrectPayload() {
        CapturingLLMProvider provider = CapturingLLMProvider.endTurn("hi");
        TestMemory memory = new TestMemory();
        AgentFactory factory = new AgentFactory(provider, memory, toolRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("route-test")
                .message("hello")
                .build();

        List<StreamEvent> events = orchestrator.chatStreamReactive(request).collectList().block();
        assertNotNull(events);

        Optional<StreamEvent> routeEvent = events.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.AGENT_ROUTE)
                .findFirst();
        assertTrue(routeEvent.isPresent(), "must emit AGENT_ROUTE event");

        Map<String, Object> data = routeEvent.get().getData();
        assertEquals("default", data.get("agentId"), "should route to default agent");
        assertEquals("route-test", data.get("sessionId"), "should carry session id");

        orchestrator.shutdown();
    }

    @Test
    @DisplayName("InterruptibleRun resume 后发出 AGENT_RESUME 事件携带 newInput 和 contextSize")
    void resumeAfterInterruptEmitsResumeEvent() {
        TestMemory memory = new TestMemory();
        CapturingLLMProvider provider = CapturingLLMProvider.endTurn("resumed");
        AgentLoop agent = AgentLoop.builder()
                .llmProvider(provider)
                .memoryProvider(memory)
                .systemPrompt("test")
                .build();

        InterruptibleRun run = new InterruptibleRun("run-1", "session-1", agent, memory);
        run.forceInterruptedState("partial output");

        List<StreamEvent> events = run.execute("new input").collectList().block();
        assertNotNull(events);

        Optional<StreamEvent> resumeEvent = events.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.AGENT_RESUME)
                .findFirst();
        assertTrue(resumeEvent.isPresent(), "resume must emit AGENT_RESUME");

        Map<String, Object> data = resumeEvent.get().getData();
        assertEquals("run-1", data.get("runId"));
        assertEquals("new input", data.get("newInput"));
        assertTrue(data.containsKey("contextSize"), "must carry contextSize");

        int contextSize = (int) data.get("contextSize");
        assertTrue(contextSize >= 0, "contextSize should be non-negative");
    }

    // ==================== Test Helpers ====================

    /**
     * LLM Provider that blocks until cancelled, simulating a long-running response.
     */
    private static class SlowLLMProvider implements LLMProvider {
        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            try { Thread.sleep(10000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent("slow")
                            .build())
                    .stopReason("end_turn")
                    .build();
        }

        @Override
        public java.util.concurrent.CompletableFuture<LLMResponse> completeAsync(
                List<ConversationMessage> messages, LLMOptions options) {
            return java.util.concurrent.CompletableFuture.supplyAsync(() -> complete(messages, options));
        }

        @Override
        public java.util.concurrent.CompletableFuture<LLMResponse> completeStream(
                List<ConversationMessage> messages, LLMOptions options, StreamEventHandler handler) {
            return completeAsync(messages, options);
        }

        @Override
        public reactor.core.publisher.Flux<StreamEvent> completeStreamReactive(
                List<ConversationMessage> messages, LLMOptions options) {
            return reactor.core.publisher.Flux.create(sink -> {
                sink.next(StreamEvent.textDelta("s"));
                sink.next(StreamEvent.textDelta("l"));
                sink.next(StreamEvent.textDelta("o"));
                sink.next(StreamEvent.textDelta("w"));
                // Never complete — simulates a long-running response
                // Will be cancelled by CancellationToken or Flux disposal
            });
        }

        @Override
        public ModelCapability getModelCapability() { return null; }

        @Override
        public String getProviderName() { return "slow-test"; }
    }

    private static class TestMemory implements MemoryProvider {
        private final List<Message> messages = Collections.synchronizedList(new ArrayList<>());

        @Override public void addMessage(String s, Message m) { messages.add(m); }
        @Override public List<Message> getHistory(String s, int l) { return List.copyOf(messages); }
        @Override public void clearSession(String s) { messages.clear(); }
        @Override public void writeEphemeral(String c) {}
        @Override public void writeDurable(String s, String c) {}
        @Override public List<MemorySearchResult> search(String q) { return List.of(); }
    }
}
