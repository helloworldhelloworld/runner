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
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Orchestrator - shutdown、interrupt、sync chat 关键路径")
class OrchestratorShutdownAndInterruptTest {

    private AgentRegistry registry;
    private ToolRegistry toolRegistry;
    private MemoryProvider memory;
    private Orchestrator orchestrator;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
        toolRegistry = new ToolRegistry();
        memory = new StubMemory();

        registry.register(AgentProfile.builder().agentId("main").systemPrompt("I am main").build());
        registry.setDefault("main");
    }

    @AfterEach
    void tearDown() {
        if (orchestrator != null) {
            orchestrator.shutdown();
        }
    }

    @Test
    @DisplayName("shutdown 关闭清理线程并取消所有 TriggerSource 订阅")
    @Timeout(5)
    void shutdownCleansUpEverything() {
        CapturingLLMProvider provider = CapturingLLMProvider.endTurn("done");
        AgentFactory factory = new AgentFactory(provider, memory, toolRegistry);
        orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        Sinks.Many<GatewayRequest> sink = Sinks.many().multicast().onBackpressureBuffer();
        TriggerSource source = new TriggerSource() {
            @Override public String name() { return "test-trigger"; }
            @Override public Flux<GatewayRequest> requests() { return sink.asFlux(); }
        };
        orchestrator.subscribeTriggerSource(source);

        orchestrator.shutdown();

        sink.tryEmitNext(GatewayRequest.builder().sessionId("s1").message("after shutdown").build());
        assertEquals(0, provider.callCount(),
                "shutdown 后 trigger source 的事件不应触发 agent 运行");
    }

    @Test
    @DisplayName("shutdown 可安全调用多次不抛异常")
    void shutdownIdempotent() {
        CapturingLLMProvider provider = CapturingLLMProvider.endTurn("ok");
        AgentFactory factory = new AgentFactory(provider, memory, toolRegistry);
        orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        assertDoesNotThrow(() -> {
            orchestrator.shutdown();
            orchestrator.shutdown();
        });
    }

    @Test
    @DisplayName("interrupt: 无活跃 run 返回 null")
    void interruptNoActiveRunReturnsNull() {
        CapturingLLMProvider provider = CapturingLLMProvider.endTurn("ok");
        AgentFactory factory = new AgentFactory(provider, memory, toolRegistry);
        orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        assertNull(orchestrator.interrupt("non-existent-session"));
    }

    @Test
    @DisplayName("interrupt: null sessionId 返回 null 不抛异常")
    void interruptNullSessionReturnsNull() {
        CapturingLLMProvider provider = CapturingLLMProvider.endTurn("ok");
        AgentFactory factory = new AgentFactory(provider, memory, toolRegistry);
        orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        assertNull(orchestrator.interrupt(null));
    }

    @Test
    @DisplayName("interrupt: 已完成的 run 返回 null")
    @Timeout(5)
    void interruptCompletedRunReturnsNull() {
        CapturingLLMProvider provider = CapturingLLMProvider.endTurn("done");
        AgentFactory factory = new AgentFactory(provider, memory, toolRegistry);
        orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("sess-done")
                .message("hello")
                .build();
        orchestrator.chatStreamReactive(request).blockLast();

        assertNull(orchestrator.interrupt("sess-done"),
                "已完成的 run 应返回 null");
    }

    @Test
    @DisplayName("同步 chat 路由到正确 agent 并返回有效 GatewayResponse")
    @Timeout(5)
    void syncChatRoutesAndReturns() {
        LLMProvider provider = new SimpleLLMProvider("sync response");
        AgentFactory factory = new AgentFactory(provider, memory, toolRegistry);
        orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("sync-sess")
                .requestId("req-1")
                .message("hello sync")
                .metadata("agentId", "main")
                .build();

        GatewayResponse response = orchestrator.chat(request);

        assertNotNull(response);
        assertEquals("req-1", response.getRequestId());
        assertEquals("sync-sess", response.getSessionId());
        assertNotNull(response.getText());
    }

    @Test
    @DisplayName("subscribeTriggerSource 同名重复订阅替换旧订阅")
    @Timeout(5)
    void duplicateSubscriptionReplacesOld() throws InterruptedException {
        AtomicBoolean firstSourceDisposed = new AtomicBoolean(false);
        CapturingLLMProvider provider = CapturingLLMProvider.endTurn("ok");
        AgentFactory factory = new AgentFactory(provider, memory, toolRegistry);
        orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        Sinks.Many<GatewayRequest> sink1 = Sinks.many().multicast().onBackpressureBuffer();
        TriggerSource source1 = new TriggerSource() {
            @Override public String name() { return "dup-source"; }
            @Override public Flux<GatewayRequest> requests() {
                return sink1.asFlux().doOnCancel(() -> firstSourceDisposed.set(true));
            }
        };

        Sinks.Many<GatewayRequest> sink2 = Sinks.many().multicast().onBackpressureBuffer();
        TriggerSource source2 = new TriggerSource() {
            @Override public String name() { return "dup-source"; }
            @Override public Flux<GatewayRequest> requests() { return sink2.asFlux(); }
        };

        orchestrator.subscribeTriggerSource(source1);
        orchestrator.subscribeTriggerSource(source2);

        Thread.sleep(100);
        assertTrue(firstSourceDisposed.get(),
                "同名重复订阅应 dispose 旧 source");
    }

    @Test
    @DisplayName("chatStream callback 收到 TEXT_DELTA 并最终 onComplete")
    @Timeout(5)
    void chatStreamCallbackReceivesEvents() throws Exception {
        CapturingLLMProvider provider = CapturingLLMProvider.endTurn("streamed text");
        AgentFactory factory = new AgentFactory(provider, memory, toolRegistry);
        orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("cb-sess")
                .requestId("cb-req")
                .message("stream me")
                .build();

        AtomicReference<GatewayResponse> completed = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        CompletableFuture<GatewayResponse> future = orchestrator.chatStream(request,
                new com.lightweightai.kernel.gateway.ChatHandler.StreamCallback() {
                    @Override public void onDelta(String delta, Map<String, Object> metadata) {}
                    @Override public void onComplete(GatewayResponse response) { completed.set(response); }
                    @Override public void onError(Throwable e) { error.set(e); }
                });

        GatewayResponse response = future.get(5, TimeUnit.SECONDS);

        assertNotNull(response);
        assertNull(error.get(), "不应有错误回调");
        assertNotNull(completed.get(), "应触发 onComplete 回调");
        assertEquals("cb-sess", response.getSessionId());
    }

    // ==================== Helper classes ====================

    private static class SimpleLLMProvider implements LLMProvider {
        private final String text;
        SimpleLLMProvider(String text) { this.text = text; }

        @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent(text).build())
                    .stopReason("end_turn").build();
        }
        @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
            return CompletableFuture.completedFuture(complete(m, o));
        }
        @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) {
            LLMResponse r = complete(m, o);
            h.onComplete(r);
            return CompletableFuture.completedFuture(r);
        }
        @Override public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            return Flux.just(
                    StreamEvent.textDelta(text),
                    StreamEvent.llmComplete(complete(m, o)));
        }
        @Override public ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return "simple"; }
    }

    private static class StubMemory implements MemoryProvider {
        private final List<Message> messages = new ArrayList<>();
        @Override public void addMessage(String sid, Message msg) { messages.add(msg); }
        @Override public List<Message> getHistory(String sid, int limit) {
            int start = Math.max(0, messages.size() - limit);
            return new ArrayList<>(messages.subList(start, messages.size()));
        }
        @Override public void clearSession(String sid) { messages.clear(); }
        @Override public void writeEphemeral(String content) {}
        @Override public void writeDurable(String section, String content) {}
        @Override public List<MemorySearchResult> search(String query) { return List.of(); }
    }
}
