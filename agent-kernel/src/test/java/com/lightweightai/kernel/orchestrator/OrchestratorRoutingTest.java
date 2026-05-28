package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.gateway.GatewayResponse;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Orchestrator: routing, session namespace, lifecycle events")
class OrchestratorRoutingTest {

    private AgentRegistry registry;
    private CapturingLLMProvider spyProvider;
    private Orchestrator orchestrator;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("default")
                .systemPrompt("Default agent")
                .build());
        registry.register(AgentProfile.builder()
                .agentId("specialist")
                .systemPrompt("Specialist agent")
                .build());
        registry.setDefault("default");

        spyProvider = CapturingLLMProvider.endTurn("routed response");
        ToolRegistry tools = new ToolRegistry();
        AgentFactory factory = new AgentFactory(spyProvider, new StubMemory(), tools);
        orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));
    }

    @AfterEach
    void tearDown() {
        orchestrator.shutdown();
    }

    @Test
    @DisplayName("routes to default agent when no agentId in metadata")
    void routesToDefault() {
        GatewayRequest request = GatewayRequest.builder()
                .sessionId("s1")
                .message("hello")
                .build();

        List<StreamEvent> events = orchestrator.chatStreamReactive(request).collectList().block();
        assertNotNull(events);

        StreamEvent routeEvent = events.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.AGENT_ROUTE)
                .findFirst().orElse(null);
        assertNotNull(routeEvent, "AGENT_ROUTE event must be emitted");
        assertEquals("default", routeEvent.getData().get("agentId"));
    }

    @Test
    @DisplayName("routes to specified agentId from metadata")
    void routesToSpecifiedAgent() {
        GatewayRequest request = GatewayRequest.builder()
                .sessionId("s2")
                .message("specialist task")
                .metadata(Map.of("agentId", "specialist"))
                .build();

        List<StreamEvent> events = orchestrator.chatStreamReactive(request).collectList().block();
        assertNotNull(events);

        StreamEvent routeEvent = events.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.AGENT_ROUTE)
                .findFirst().orElse(null);
        assertNotNull(routeEvent);
        assertEquals("specialist", routeEvent.getData().get("agentId"));
    }

    @Test
    @DisplayName("falls back to default for unknown agentId")
    void fallsBackToDefaultForUnknownAgent() {
        GatewayRequest request = GatewayRequest.builder()
                .sessionId("s3")
                .message("test")
                .metadata(Map.of("agentId", "nonexistent"))
                .build();

        List<StreamEvent> events = orchestrator.chatStreamReactive(request).collectList().block();
        assertNotNull(events);

        StreamEvent routeEvent = events.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.AGENT_ROUTE)
                .findFirst().orElse(null);
        assertNotNull(routeEvent);
        assertEquals("default", routeEvent.getData().get("agentId"));
    }

    @Test
    @DisplayName("sync chat returns valid GatewayResponse")
    void syncChatWorks() {
        GatewayRequest request = GatewayRequest.builder()
                .sessionId("s4")
                .message("sync test")
                .build();

        GatewayResponse response = orchestrator.chat(request);
        assertNotNull(response);
        assertEquals("routed response", response.getText());
        assertEquals("s4", response.getSessionId());
        assertTrue(response.getLatencyMs() >= 0);
    }

    @Test
    @DisplayName("system prompt from profile reaches LLM provider")
    void systemPromptFromProfileReachesProvider() {
        GatewayRequest request = GatewayRequest.builder()
                .sessionId("s5")
                .message("hi")
                .metadata(Map.of("agentId", "specialist"))
                .build();

        orchestrator.chatStreamReactive(request).collectList().block();

        var messages = spyProvider.lastMessages();
        assertNotNull(messages);
        assertTrue(messages.stream().anyMatch(
                m -> m.getTextContent() != null && m.getTextContent().contains("Specialist agent")),
                "system prompt from AgentProfile must reach LLM");
    }

    private static class StubMemory implements MemoryProvider {
        @Override public void addMessage(String s, Message m) {}
        @Override public List<Message> getHistory(String s, int l) { return List.of(); }
        @Override public void clearSession(String s) {}
        @Override public void writeEphemeral(String c) {}
        @Override public void writeDurable(String s, String c) {}
        @Override public List<MemorySearchResult> search(String q) { return List.of(); }
    }
}
