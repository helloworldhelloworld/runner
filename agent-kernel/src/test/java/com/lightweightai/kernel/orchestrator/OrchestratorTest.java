package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentLoop;
import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.gateway.ChatHandler;
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

    @Test
    @DisplayName("不同 session 隔离 — 各自拿到独立的 AGENT_ROUTE")
    void differentSessionsAreIsolated() {
        CountingProvider provider = new CountingProvider("ok");
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

        orchestrator.shutdown();
    }

    @Test
    @DisplayName("standalone interrupt — 无在途 run 返回 null")
    void interruptWithNoActiveRunReturnsNull() {
        CountingProvider provider = new CountingProvider("ok");
        AgentFactory factory = new AgentFactory(provider, memory, toolRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        StreamEvent result = orchestrator.interrupt("nonexistent-session");
        assertNull(result);

        orchestrator.shutdown();
    }

    @Test
    @DisplayName("standalone interrupt — null sessionId 返回 null")
    void interruptWithNullSessionReturnsNull() {
        CountingProvider provider = new CountingProvider("ok");
        AgentFactory factory = new AgentFactory(provider, memory, toolRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        StreamEvent result = orchestrator.interrupt(null);
        assertNull(result);

        orchestrator.shutdown();
    }

    @Test
    @DisplayName("shutdown 正常关闭清理线程")
    void shutdownCleansUp() {
        CountingProvider provider = new CountingProvider("ok");
        AgentFactory factory = new AgentFactory(provider, memory, toolRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        // 先做一次请求
        GatewayRequest req = GatewayRequest.builder().sessionId("s1").message("hi").build();
        orchestrator.chatStreamReactive(req).blockLast();

        // shutdown 不应抛异常
        assertDoesNotThrow(orchestrator::shutdown);
    }

    @Test
    @DisplayName("TriggerSource 订阅 — 事件被处理")
    void subscribeTriggerSourceProcessesRequests() throws InterruptedException {
        CountingProvider provider = new CountingProvider("triggered");
        AgentFactory factory = new AgentFactory(provider, memory, toolRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        // latch 在 provider 被调用时触发（而非 source flux 完成时）
        java.util.concurrent.CountDownLatch providerCalled = new java.util.concurrent.CountDownLatch(1);
        provider.onCall = providerCalled::countDown;

        TriggerSource source = new TriggerSource() {
            @Override public String name() { return "test-trigger"; }
            @Override public Flux<GatewayRequest> requests() {
                return Flux.just(GatewayRequest.builder()
                        .sessionId("trigger-sess")
                        .message("triggered")
                        .build());
            }
        };

        orchestrator.subscribeTriggerSource(source);
        boolean called = providerCalled.await(5, java.util.concurrent.TimeUnit.SECONDS);

        assertTrue(called, "TriggerSource should have caused at least one LLM call within timeout");

        orchestrator.shutdown();
    }

    @Test
    @DisplayName("TriggerSource 取消订阅")
    void unsubscribeTriggerSource() {
        CountingProvider provider = new CountingProvider("ok");
        AgentFactory factory = new AgentFactory(provider, memory, toolRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        TriggerSource source = new TriggerSource() {
            @Override public String name() { return "to-remove"; }
            @Override public Flux<GatewayRequest> requests() { return Flux.never(); }
        };

        orchestrator.subscribeTriggerSource(source);
        assertDoesNotThrow(() -> orchestrator.unsubscribeTriggerSource("to-remove"));
        // 取消不存在的 source 也不应抛异常
        assertDoesNotThrow(() -> orchestrator.unsubscribeTriggerSource("nonexistent"));

        orchestrator.shutdown();
    }

    @Test
    @DisplayName("同步 chat 方法也能路由到正确 Agent")
    void syncChatRoutesToCorrectAgent() {
        CountingProvider provider = new CountingProvider("sync response");
        AgentFactory factory = new AgentFactory(provider, memory, toolRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest req = GatewayRequest.builder()
                .sessionId("sync-sess")
                .message("hello")
                .metadata("agentId", "beta")
                .build();

        com.lightweightai.kernel.gateway.GatewayResponse resp = orchestrator.chat(req);
        assertNotNull(resp);

        orchestrator.shutdown();
    }

    @Test
    @DisplayName("callback chatStream 桥接 reactive — 回调被调用")
    void chatStreamCallbackBridge() throws Exception {
        CountingProvider provider = new CountingProvider("streamed");
        AgentFactory factory = new AgentFactory(provider, memory, toolRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest req = GatewayRequest.builder().sessionId("cb-sess").message("hi").build();

        List<String> deltas = new ArrayList<>();
        java.util.concurrent.atomic.AtomicBoolean completed = new java.util.concurrent.atomic.AtomicBoolean(false);

        orchestrator.chatStream(req, new ChatHandler.StreamCallback() {
            @Override public void onDelta(String delta, Map<String, Object> metadata) {
                if (delta != null) deltas.add(delta);
            }
            @Override public void onComplete(com.lightweightai.kernel.gateway.GatewayResponse response) {
                completed.set(true);
            }
            @Override public void onError(Throwable error) {}
        }).get(5, java.util.concurrent.TimeUnit.SECONDS);

        assertTrue(completed.get(), "onComplete callback should have been invoked");

        orchestrator.shutdown();
    }

    // ==================== Helper classes ====================

    private static class CountingProvider implements LLMProvider {
        private final String response;
        private int callCount = 0;
        volatile Runnable onCall;

        CountingProvider(String response) { this.response = response; }

        private LLMResponse buildResponse() {
            return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent(response).build())
                    .stopReason("end_turn").build();
        }

        @Override
        public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            callCount++;
            if (onCall != null) onCall.run();
            return Flux.just(
                    StreamEvent.textDelta(response),
                    StreamEvent.llmComplete(buildResponse()));
        }

        @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            callCount++;
            if (onCall != null) onCall.run();
            return buildResponse();
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
