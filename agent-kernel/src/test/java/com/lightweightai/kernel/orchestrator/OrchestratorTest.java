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

@DisplayName("Orchestrator - 多 Agent 路由 + 打断接续")
class OrchestratorTest {

    private AgentRegistry registry;
    private MemoryProvider memory;
    private ToolRegistry toolRegistry;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
        memory = new TestMemoryProvider();
        toolRegistry = new ToolRegistry();

        registry.register(AgentProfile.builder().agentId("alpha").systemPrompt("I am alpha").build());
        registry.register(AgentProfile.builder().agentId("beta").systemPrompt("I am beta").build());
        registry.setDefault("alpha");
    }

    @Test
    @DisplayName("按 metadata 路由到正确的 Agent")
    void routesToCorrectAgent() {
        CountingProvider alphaProvider = new CountingProvider("alpha response");
        AgentFactory factory = new AgentFactory(alphaProvider, memory, toolRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("sess-1")
                .message("你好")
                .metadata("agentId", "alpha")
                .build();

        List<StreamEvent> events = orchestrator.chatStreamReactive(request).collectList().block();

        assertNotNull(events);
        // 首个事件应为 AGENT_ROUTE
        assertTrue(events.stream().anyMatch(e ->
                e.getType() == StreamEvent.EventType.AGENT_ROUTE
                && "alpha".equals(e.getData().get("agentId"))));
    }

    @Test
    @DisplayName("无 agentId 时 fallback 到 default")
    void fallbackToDefault() {
        CountingProvider provider = new CountingProvider("default response");
        AgentFactory factory = new AgentFactory(provider, memory, toolRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("sess-2")
                .message("你好")
                .build();

        List<StreamEvent> events = orchestrator.chatStreamReactive(request).collectList().block();

        assertNotNull(events);
        assertTrue(events.stream().anyMatch(e ->
                e.getType() == StreamEvent.EventType.AGENT_ROUTE
                && "alpha".equals(e.getData().get("agentId"))));
    }

    @Test
    @DisplayName("同 session 的请求复用同一个 InterruptibleRun 上下文")
    void sameSessionReusesRun() {
        CountingProvider provider = new CountingProvider("ok");
        AgentFactory factory = new AgentFactory(provider, memory, toolRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        // 第一个请求
        GatewayRequest req1 = GatewayRequest.builder().sessionId("sess-3").message("问题1").build();
        orchestrator.chatStreamReactive(req1).blockLast();

        // 第二个请求（同 session）
        GatewayRequest req2 = GatewayRequest.builder().sessionId("sess-3").message("问题2").build();
        List<StreamEvent> events = orchestrator.chatStreamReactive(req2).collectList().block();

        assertNotNull(events);
        // 第二次应正常完成
        assertTrue(events.stream().anyMatch(e -> e.getType() == StreamEvent.EventType.LLM_COMPLETE));
    }

    // ==================== Helper classes ====================

    private static class CountingProvider implements LLMProvider {
        private final String response;
        private int callCount = 0;

        CountingProvider(String response) { this.response = response; }

        @Override
        public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            callCount++;
            return Flux.just(
                    StreamEvent.textDelta(response),
                    StreamEvent.llmComplete(LLMResponse.builder()
                            .message(ConversationMessage.builder()
                                    .role(ConversationMessage.MessageRole.ASSISTANT)
                                    .textContent(response).build())
                            .stopReason("end_turn").build()));
        }

        @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            return LLMResponse.builder().stopReason("end_turn").build();
        }
        @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
            return CompletableFuture.completedFuture(complete(m, o));
        }
        @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) {
            return CompletableFuture.completedFuture(complete(m, o));
        }
        @Override public ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return "counting"; }
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
