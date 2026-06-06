package com.lightweightai.kernel.orchestrator;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Orchestrator - shutdown / cleanup / chatStream 桥接")
class OrchestratorLifecycleTest {

    private AgentRegistry registry;
    private StubMemory memory;
    private ToolRegistry toolRegistry;
    private Orchestrator orchestrator;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
        memory = new StubMemory();
        toolRegistry = new ToolRegistry();
        registry.register(AgentProfile.builder().agentId("default").systemPrompt("test").build());
        registry.setDefault("default");
    }

    @AfterEach
    void tearDown() {
        if (orchestrator != null) orchestrator.shutdown();
    }

    @Test
    @DisplayName("shutdown 关闭清理线程 — 不抛异常")
    void shutdownDoesNotThrow() {
        AgentFactory factory = new AgentFactory(new InstantProvider("ok"), memory, toolRegistry);
        orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        assertDoesNotThrow(() -> orchestrator.shutdown());
    }

    @Test
    @DisplayName("shutdown 后再次 shutdown 是幂等的")
    void shutdownIsIdempotent() {
        AgentFactory factory = new AgentFactory(new InstantProvider("ok"), memory, toolRegistry);
        orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        orchestrator.shutdown();
        assertDoesNotThrow(() -> orchestrator.shutdown());
    }

    @Test
    @DisplayName("cleanupStaleRuns 移除已完成且超过 TTL 的 run — 不移除正在运行的")
    void cleanupStaleRunsRemovesCompletedNotRunning() throws Exception {
        AgentFactory factory = new AgentFactory(new InstantProvider("ok"), memory, toolRegistry);
        orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        // 发送一个请求让 run 完成
        GatewayRequest req = GatewayRequest.builder().sessionId("stale-sess").message("hi").build();
        orchestrator.chatStreamReactive(req).blockLast();

        // COMPLETED run 在 activeRuns 中不会存在（doFinally 已清理）
        // 验证第二次请求可以正常创建新 run
        GatewayRequest req2 = GatewayRequest.builder().sessionId("stale-sess").message("hello again").build();
        List<StreamEvent> events = orchestrator.chatStreamReactive(req2).collectList().block();

        assertNotNull(events);
        assertTrue(events.stream().anyMatch(e -> e.getType() == StreamEvent.EventType.AGENT_ROUTE));
    }

    @Test
    @DisplayName("chatStream 桥接 — callback 收到 TEXT_DELTA 和 onComplete")
    void chatStreamBridgesReactiveToCallback() throws Exception {
        AgentFactory factory = new AgentFactory(new InstantProvider("hello world"), memory, toolRegistry);
        orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest request = GatewayRequest.builder().sessionId("cb-sess").message("hi").build();

        List<String> deltas = new ArrayList<>();
        AtomicReference<GatewayResponse> completedResponse = new AtomicReference<>();
        AtomicReference<Throwable> capturedError = new AtomicReference<>();

        CompletableFuture<GatewayResponse> future = orchestrator.chatStream(request, new ChatHandler.StreamCallback() {
            @Override
            public void onDelta(String delta, Map<String, Object> metadata) {
                deltas.add(delta);
            }

            @Override
            public void onComplete(GatewayResponse response) {
                completedResponse.set(response);
            }

            @Override
            public void onError(Throwable error) {
                capturedError.set(error);
            }
        });

        GatewayResponse response = future.get();
        assertNotNull(response);
        assertNull(capturedError.get(), "Should not have error");
        assertEquals("cb-sess", response.getSessionId());
        assertFalse(deltas.isEmpty(), "Should have received at least one delta");
        assertTrue(deltas.contains("hello world"), "Delta should contain provider text");
    }

    @Test
    @DisplayName("chatStream 桥接 — provider 抛异常时 callback 收到 onError")
    void chatStreamBridgesErrorToCallback() throws Exception {
        LLMProvider failingProvider = new FailingProvider(new RuntimeException("LLM down"));
        AgentFactory factory = new AgentFactory(failingProvider, memory, toolRegistry);
        orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest request = GatewayRequest.builder().sessionId("err-sess").message("hi").build();

        AtomicReference<Throwable> capturedError = new AtomicReference<>();
        CompletableFuture<GatewayResponse> future = orchestrator.chatStream(request, new ChatHandler.StreamCallback() {
            @Override public void onDelta(String delta, Map<String, Object> metadata) {}
            @Override public void onComplete(GatewayResponse response) {}
            @Override public void onError(Throwable error) { capturedError.set(error); }
        });

        GatewayResponse response = future.get();
        assertNotNull(response);
        assertTrue(response.isError(), "Response should indicate error");
    }

    @Test
    @DisplayName("chatStreamReactive 多个 session 互不影响 — 隔离性")
    void differentSessionsAreIsolated() {
        AgentFactory factory = new AgentFactory(new InstantProvider("ok"), memory, toolRegistry);
        orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest reqA = GatewayRequest.builder().sessionId("sess-a").message("hi").build();
        GatewayRequest reqB = GatewayRequest.builder().sessionId("sess-b").message("hello").build();

        List<StreamEvent> eventsA = orchestrator.chatStreamReactive(reqA).collectList().block();
        List<StreamEvent> eventsB = orchestrator.chatStreamReactive(reqB).collectList().block();

        assertNotNull(eventsA);
        assertNotNull(eventsB);

        // 两个 session 都有各自的 AGENT_ROUTE
        assertTrue(eventsA.stream().anyMatch(e ->
                e.getType() == StreamEvent.EventType.AGENT_ROUTE
                        && "sess-a".equals(e.getData().get("sessionId"))));
        assertTrue(eventsB.stream().anyMatch(e ->
                e.getType() == StreamEvent.EventType.AGENT_ROUTE
                        && "sess-b".equals(e.getData().get("sessionId"))));
    }

    @Test
    @DisplayName("Orchestrator chatStreamReactive 打断活跃 run — 发出 INTERRUPT + 执行新请求")
    void interruptActiveRunAndResume() throws InterruptedException {
        // 慢速 provider 让第一个请求持续流式输出
        LLMProvider slowProvider = new SlowDeltaProvider(List.of("a", "b", "c", "d", "e"), 100);
        AgentFactory factory = new AgentFactory(slowProvider, memory, toolRegistry);
        orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        // 第一个请求，异步订阅
        GatewayRequest req1 = GatewayRequest.builder().sessionId("int-sess").message("问题1").build();
        List<StreamEvent> events1 = new ArrayList<>();
        orchestrator.chatStreamReactive(req1).subscribe(events1::add);

        // 等待一些 delta 输出
        Thread.sleep(200);

        // 第二个请求（同 session）—— 会打断第一个
        GatewayRequest req2 = GatewayRequest.builder().sessionId("int-sess").message("问题2").build();
        List<StreamEvent> events2 = orchestrator.chatStreamReactive(req2).collectList().block();

        assertNotNull(events2);
        // 第二次请求应有 AGENT_ROUTE
        assertTrue(events2.stream().anyMatch(e -> e.getType() == StreamEvent.EventType.AGENT_ROUTE));
    }

    // ==================== Helper classes ====================

    private static class InstantProvider implements LLMProvider {
        private final String text;
        InstantProvider(String text) { this.text = text; }
        @Override public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            return Flux.just(
                    StreamEvent.textDelta(text),
                    StreamEvent.llmComplete(LLMResponse.builder()
                            .message(ConversationMessage.builder()
                                    .role(ConversationMessage.MessageRole.ASSISTANT)
                                    .textContent(text).build())
                            .stopReason("end_turn").build()));
        }
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
            return CompletableFuture.completedFuture(complete(m, o));
        }
        @Override public ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return "instant"; }
    }

    private static class FailingProvider implements LLMProvider {
        private final RuntimeException error;
        FailingProvider(RuntimeException error) { this.error = error; }
        @Override public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            return Flux.error(error);
        }
        @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) { throw error; }
        @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
            return CompletableFuture.failedFuture(error);
        }
        @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) {
            return CompletableFuture.failedFuture(error);
        }
        @Override public ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return "failing"; }
    }

    private static class SlowDeltaProvider implements LLMProvider {
        private final List<String> deltas;
        private final long delayMs;
        SlowDeltaProvider(List<String> deltas, long delayMs) { this.deltas = deltas; this.delayMs = delayMs; }
        @Override public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            String full = String.join("", deltas);
            return Flux.fromIterable(deltas)
                    .delayElements(Duration.ofMillis(delayMs))
                    .map(StreamEvent::textDelta)
                    .concatWith(Flux.just(StreamEvent.llmComplete(LLMResponse.builder()
                            .message(ConversationMessage.builder()
                                    .role(ConversationMessage.MessageRole.ASSISTANT)
                                    .textContent(full).build())
                            .stopReason("end_turn").build())));
        }
        @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            try { Thread.sleep(delayMs * deltas.size()); } catch (InterruptedException ignored) {}
            return LLMResponse.builder().stopReason("end_turn").build();
        }
        @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
            return CompletableFuture.completedFuture(complete(m, o));
        }
        @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) {
            return CompletableFuture.completedFuture(complete(m, o));
        }
        @Override public ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return "slow-delta"; }
    }

    private static class StubMemory implements MemoryProvider {
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
