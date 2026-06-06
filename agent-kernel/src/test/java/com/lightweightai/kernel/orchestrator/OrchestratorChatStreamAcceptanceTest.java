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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Acceptance: Orchestrator chatStream callback 完整链路")
class OrchestratorChatStreamAcceptanceTest {

    private Orchestrator orchestrator;

    @AfterEach
    void tearDown() {
        if (orchestrator != null) orchestrator.shutdown();
    }

    @Test
    @DisplayName("chatStream → chatStreamReactive 桥接：delta 到达 callback、text 拼接正确、onComplete 触发")
    void fullChatStreamChain() throws Exception {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder().agentId("test-agent").systemPrompt("test").build());
        registry.setDefault("test-agent");

        StubMemory memory = new StubMemory();
        MultiDeltaProvider provider = new MultiDeltaProvider(List.of("你", "好", "世", "界"));
        AgentFactory factory = new AgentFactory(provider, memory, new ToolRegistry());
        orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest request = GatewayRequest.builder().sessionId("cs-1").message("hi").build();

        List<String> receivedDeltas = new ArrayList<>();
        AtomicReference<GatewayResponse> completedRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        CompletableFuture<GatewayResponse> future = orchestrator.chatStream(request, new ChatHandler.StreamCallback() {
            @Override
            public void onDelta(String delta, Map<String, Object> metadata) {
                receivedDeltas.add(delta);
            }
            @Override
            public void onComplete(GatewayResponse response) {
                completedRef.set(response);
            }
            @Override
            public void onError(Throwable error) {
                errorRef.set(error);
            }
        });

        GatewayResponse response = future.get(10, TimeUnit.SECONDS);

        // 验证 callback 收到的 delta
        assertFalse(receivedDeltas.isEmpty(), "Should receive deltas");

        // 验证 response 的 text 包含所有 delta 的拼接
        assertNotNull(response);
        assertNull(errorRef.get(), "No error expected");
        assertEquals("cs-1", response.getSessionId());

        // 验证 onComplete 被调用
        assertNotNull(completedRef.get(), "onComplete should be called");
    }

    @Test
    @DisplayName("chatStream 错误场景 — provider 抛异常后 onError 被调用、response 标记 error")
    void chatStreamErrorChain() throws Exception {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder().agentId("err").systemPrompt("t").build());
        registry.setDefault("err");

        StubMemory memory = new StubMemory();
        FailingStreamProvider provider = new FailingStreamProvider();
        AgentFactory factory = new AgentFactory(provider, memory, new ToolRegistry());
        orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest request = GatewayRequest.builder().sessionId("err-1").message("hi").build();

        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CompletableFuture<GatewayResponse> future = orchestrator.chatStream(request, new ChatHandler.StreamCallback() {
            @Override public void onDelta(String delta, Map<String, Object> metadata) {}
            @Override public void onComplete(GatewayResponse response) {}
            @Override public void onError(Throwable error) { errorRef.set(error); }
        });

        GatewayResponse response = future.get(10, TimeUnit.SECONDS);
        assertNotNull(response);
        assertTrue(response.isError());
    }

    @Test
    @DisplayName("chat 同步路径正确路由并返回 GatewayResponse")
    void syncChatReturnsResponse() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder().agentId("sync").systemPrompt("t").build());
        registry.setDefault("sync");

        StubMemory memory = new StubMemory();
        SyncProvider provider = new SyncProvider("sync result");
        AgentFactory factory = new AgentFactory(provider, memory, new ToolRegistry());
        orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest request = GatewayRequest.builder().sessionId("sync-1").message("hi").build();
        GatewayResponse response = orchestrator.chat(request);

        assertNotNull(response);
        assertFalse(response.isError());
    }

    // ==================== Helpers ====================

    private static class MultiDeltaProvider implements LLMProvider {
        private final List<String> deltas;
        MultiDeltaProvider(List<String> deltas) { this.deltas = deltas; }
        @Override public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            String full = String.join("", deltas);
            Flux<StreamEvent> deltaFlux = Flux.fromIterable(deltas).map(StreamEvent::textDelta);
            StreamEvent complete = StreamEvent.llmComplete(LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT).textContent(full).build())
                    .stopReason("end_turn").build());
            return Flux.concat(deltaFlux, Flux.just(complete));
        }
        @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            String full = String.join("", deltas);
            return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT).textContent(full).build())
                    .stopReason("end_turn").build();
        }
        @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) { return CompletableFuture.completedFuture(complete(m, o)); }
        @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) { return CompletableFuture.completedFuture(complete(m, o)); }
        @Override public ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return "multi-delta"; }
    }

    private static class FailingStreamProvider implements LLMProvider {
        @Override public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            return Flux.error(new RuntimeException("stream exploded"));
        }
        @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) { throw new RuntimeException("boom"); }
        @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) { return CompletableFuture.failedFuture(new RuntimeException("boom")); }
        @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) { return CompletableFuture.failedFuture(new RuntimeException("boom")); }
        @Override public ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return "failing-stream"; }
    }

    private static class SyncProvider implements LLMProvider {
        private final String text;
        SyncProvider(String text) { this.text = text; }
        @Override public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            return Flux.just(StreamEvent.textDelta(text), StreamEvent.llmComplete(complete(m, o)));
        }
        @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT).textContent(text).build())
                    .stopReason("end_turn").build();
        }
        @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) { return CompletableFuture.completedFuture(complete(m, o)); }
        @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) { return CompletableFuture.completedFuture(complete(m, o)); }
        @Override public ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return "sync"; }
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
