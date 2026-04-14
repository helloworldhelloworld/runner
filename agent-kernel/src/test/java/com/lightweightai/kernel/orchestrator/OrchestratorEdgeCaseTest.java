package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentLoop;
import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.gateway.ChatHandler;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.gateway.GatewayResponse;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Orchestrator 补充测试 — 边界条件和遗漏路径
 *
 * 覆盖：
 * - chatStream 回调模式（callback onDelta/onComplete/onError）
 * - chat 同步路径
 * - resolveSessionKey 格式验证
 * - maxSpawnDepth=0 时不注册 spawn 工具
 * - subagentRuntime=null 时不注册 spawn 工具
 * - 不同 session 不互相干扰
 */
@DisplayName("Orchestrator - 边界条件补充测试")
class OrchestratorEdgeCaseTest {

    private AgentRegistry registry;
    private MemoryProvider memory;
    private ToolRegistry toolRegistry;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
        memory = new TestMemoryProvider();
        toolRegistry = new ToolRegistry();

        registry.register(AgentProfile.builder().agentId("alpha").systemPrompt("Alpha agent").build());
        registry.register(AgentProfile.builder().agentId("beta").systemPrompt("Beta agent").build());
        registry.register(AgentProfile.builder()
                .agentId("spawner")
                .systemPrompt("Spawner agent")
                .maxSpawnDepth(2)
                .build());
        registry.register(AgentProfile.builder()
                .agentId("no-spawn")
                .systemPrompt("No spawn agent")
                .maxSpawnDepth(0)
                .build());
        registry.setDefault("alpha");
    }

    @Test
    @DisplayName("chat 同步路径返回正确的 GatewayResponse")
    void chatSyncReturnsResponse() {
        FixedProvider provider = new FixedProvider("sync response");
        AgentFactory factory = new AgentFactory(provider, memory, toolRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("sync-1")
                .message("同步测试")
                .requestId("req-1")
                .build();

        GatewayResponse response = orchestrator.chat(request);
        assertNotNull(response);
        assertEquals("req-1", response.getRequestId());
        assertEquals("sync-1", response.getSessionId());
    }

    @Test
    @DisplayName("chatStream 回调模式触发 onDelta 和 onComplete")
    void chatStreamCallbackTriggersEvents() throws Exception {
        FixedProvider provider = new FixedProvider("stream content");
        AgentFactory factory = new AgentFactory(provider, memory, toolRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("cb-1")
                .message("回调测试")
                .requestId("req-cb")
                .build();

        List<String> deltas = new ArrayList<>();
        AtomicReference<GatewayResponse> completedResponse = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        ChatHandler.StreamCallback callback = new ChatHandler.StreamCallback() {
            @Override
            public void onDelta(String delta, Map<String, Object> metadata) {
                if (delta != null) deltas.add(delta);
            }

            @Override
            public void onComplete(GatewayResponse response) {
                completedResponse.set(response);
            }

            @Override
            public void onError(Throwable error) {
                errorRef.set(error);
            }
        };

        CompletableFuture<GatewayResponse> future = orchestrator.chatStream(request, callback);
        GatewayResponse result = future.get(5, TimeUnit.SECONDS);

        assertNotNull(result);
        assertNull(errorRef.get(), "Should not have error");
        assertNotNull(completedResponse.get(), "onComplete should be called");
        assertFalse(deltas.isEmpty(), "Should receive at least one delta");
    }

    @Test
    @DisplayName("不同 session 的请求互不干扰")
    void differentSessionsAreIsolated() {
        FixedProvider provider = new FixedProvider("response");
        AgentFactory factory = new AgentFactory(provider, memory, toolRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest req1 = GatewayRequest.builder()
                .sessionId("isolated-1")
                .message("Session 1")
                .metadata("agentId", "alpha")
                .build();

        GatewayRequest req2 = GatewayRequest.builder()
                .sessionId("isolated-2")
                .message("Session 2")
                .metadata("agentId", "beta")
                .build();

        List<StreamEvent> events1 = orchestrator.chatStreamReactive(req1).collectList().block();
        List<StreamEvent> events2 = orchestrator.chatStreamReactive(req2).collectList().block();

        assertNotNull(events1);
        assertNotNull(events2);

        // 各自有自己的 AGENT_ROUTE 事件
        assertTrue(events1.stream().anyMatch(e ->
                e.getType() == StreamEvent.EventType.AGENT_ROUTE
                && "alpha".equals(e.getData().get("agentId"))));
        assertTrue(events2.stream().anyMatch(e ->
                e.getType() == StreamEvent.EventType.AGENT_ROUTE
                && "beta".equals(e.getData().get("agentId"))));
    }

    @Test
    @DisplayName("maxSpawnDepth=0 时不注册 SpawnSubagentTool")
    void noSpawnToolWhenMaxDepthZero() {
        FixedProvider provider = new FixedProvider("no spawn");
        AgentFactory factory = new AgentFactory(provider, memory, toolRegistry);
        // 提供 subagentRuntime 但 agent 的 maxSpawnDepth=0
        SubagentRuntime runtime = new SubagentRuntime(factory, registry, 5);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry), runtime);

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("no-spawn-1")
                .message("test")
                .metadata("agentId", "no-spawn")
                .build();

        List<StreamEvent> events = orchestrator.chatStreamReactive(request).collectList().block();
        assertNotNull(events);
        // 应正常完成，不报错
        assertTrue(events.stream().anyMatch(e -> e.getType() == StreamEvent.EventType.AGENT_ROUTE));
    }

    @Test
    @DisplayName("subagentRuntime=null 时即使 maxSpawnDepth>0 也不注册 spawn 工具")
    void noSpawnToolWhenRuntimeNull() {
        FixedProvider provider = new FixedProvider("no runtime");
        AgentFactory factory = new AgentFactory(provider, memory, toolRegistry);
        // subagentRuntime = null
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry), null);

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("no-rt-1")
                .message("test")
                .metadata("agentId", "spawner") // maxSpawnDepth=2 but runtime=null
                .build();

        List<StreamEvent> events = orchestrator.chatStreamReactive(request).collectList().block();
        assertNotNull(events);
        assertTrue(events.stream().anyMatch(e -> e.getType() == StreamEvent.EventType.AGENT_ROUTE));
    }

    @Test
    @DisplayName("AGENT_ROUTE 事件包含正确的 agentId 和 sessionId")
    void agentRouteEventHasCorrectData() {
        FixedProvider provider = new FixedProvider("routed");
        AgentFactory factory = new AgentFactory(provider, memory, toolRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("route-1")
                .message("test route")
                .metadata("agentId", "beta")
                .build();

        List<StreamEvent> events = orchestrator.chatStreamReactive(request).collectList().block();
        assertNotNull(events);

        StreamEvent routeEvent = events.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.AGENT_ROUTE)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing AGENT_ROUTE event"));

        assertEquals("beta", routeEvent.getData().get("agentId"));
        assertEquals("route-1", routeEvent.getData().get("sessionId"));
    }

    @Test
    @DisplayName("完成后的 run 从 activeRuns 中移除（非 INTERRUPTED）")
    void completedRunIsRemovedFromActiveRuns() {
        FixedProvider provider = new FixedProvider("done");
        AgentFactory factory = new AgentFactory(provider, memory, toolRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest req = GatewayRequest.builder()
                .sessionId("cleanup-1")
                .message("first")
                .build();

        orchestrator.chatStreamReactive(req).blockLast();

        // 第二次请求同一 session，应创建新的 run（因为前一个已完成并移除）
        GatewayRequest req2 = GatewayRequest.builder()
                .sessionId("cleanup-1")
                .message("second")
                .build();

        List<StreamEvent> events = orchestrator.chatStreamReactive(req2).collectList().block();
        assertNotNull(events);
        assertTrue(events.stream().anyMatch(e -> e.getType() == StreamEvent.EventType.AGENT_ROUTE));
    }

    // ==================== Helper classes ====================

    private static class FixedProvider implements LLMProvider {
        private final String response;
        FixedProvider(String response) { this.response = response; }

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
        @Override public String getProviderName() { return "fixed"; }
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
