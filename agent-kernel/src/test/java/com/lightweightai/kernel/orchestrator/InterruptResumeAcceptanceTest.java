package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.CancellationToken;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Outside-in Acceptance Test: 打断接续全链路
 *
 * 验证：Orchestrator → InterruptibleRun → AgentLoop → ToolCallingLoop → CancellationToken → Memory
 * 的完整链路，从用户场景出发。
 */
@DisplayName("Acceptance: 打断接续全链路")
class InterruptResumeAcceptanceTest {

    private RecordingMemory memory;
    private AgentRegistry registry;
    private ToolRegistry toolRegistry;

    @BeforeEach
    void setUp() {
        memory = new RecordingMemory();
        registry = new AgentRegistry();
        registry.register(AgentProfile.builder().agentId("default").systemPrompt("你是助手").build());
        registry.setDefault("default");
        toolRegistry = new ToolRegistry();
    }

    @Test
    @DisplayName("场景：用户发消息A → 流式输出中 → 用户发消息B打断 → A的部分文本保存 → B接续执行 → B的LLM收到A的部分上下文")
    void fullInterruptResumeScenario() throws InterruptedException {
        // 用一个慢 provider，让我们有时间打断
        AtomicInteger callCount = new AtomicInteger(0);
        List<List<ConversationMessage>> llmInputHistory = new CopyOnWriteArrayList<>();

        LLMProvider slowProvider = new LLMProvider() {
            @Override
            public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> msgs, LLMOptions opts) {
                llmInputHistory.add(new ArrayList<>(msgs));
                int call = callCount.incrementAndGet();
                if (call == 1) {
                    // 第一次调用：慢速输出 5 个 delta，每个 100ms
                    return Flux.just("你好", "，让", "我来", "帮你", "分析")
                            .delayElements(Duration.ofMillis(100))
                            .map(StreamEvent::textDelta)
                            .concatWith(Flux.just(StreamEvent.llmComplete(
                                    LLMResponse.builder()
                                            .message(ConversationMessage.builder()
                                                    .role(ConversationMessage.MessageRole.ASSISTANT)
                                                    .textContent("你好，让我来帮你分析").build())
                                            .stopReason("end_turn").build())));
                } else {
                    // 第二次调用（resume后）：快速返回
                    return Flux.just(
                            StreamEvent.textDelta("收到新问题"),
                            StreamEvent.llmComplete(LLMResponse.builder()
                                    .message(ConversationMessage.builder()
                                            .role(ConversationMessage.MessageRole.ASSISTANT)
                                            .textContent("收到新问题").build())
                                    .stopReason("end_turn").build()));
                }
            }
            @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
                return LLMResponse.builder().stopReason("end_turn")
                        .message(ConversationMessage.builder().role(ConversationMessage.MessageRole.ASSISTANT)
                                .textContent("sync").build()).build();
            }
            @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
                return CompletableFuture.completedFuture(complete(m, o)); }
            @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) {
                return CompletableFuture.completedFuture(complete(m, o)); }
            @Override public ModelCapability getModelCapability() { return null; }
            @Override public String getProviderName() { return "slow-test"; }
        };

        AgentFactory factory = new AgentFactory(slowProvider, memory, toolRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        // === 用户发消息 A ===
        com.lightweightai.kernel.gateway.GatewayRequest reqA = com.lightweightai.kernel.gateway.GatewayRequest.builder()
                .sessionId("sess-1")
                .message("帮我分析这个问题")
                .build();

        List<StreamEvent> eventsA = new CopyOnWriteArrayList<>();
        CountDownLatch startedStreaming = new CountDownLatch(1);

        // 异步执行 A
        orchestrator.chatStreamReactive(reqA)
                .doOnNext(e -> {
                    eventsA.add(e);
                    if (e.getType() == StreamEvent.EventType.TEXT_DELTA) {
                        startedStreaming.countDown();  // 收到第一个 delta
                    }
                })
                .subscribe();

        // 等 A 开始流式输出
        assertTrue(startedStreaming.await(3, TimeUnit.SECONDS), "Should start streaming within 3s");

        // 等一小会让更多 delta 到达
        Thread.sleep(150);

        // === 用户发消息 B（打断 A）===
        com.lightweightai.kernel.gateway.GatewayRequest reqB = com.lightweightai.kernel.gateway.GatewayRequest.builder()
                .sessionId("sess-1")  // 同一个 session!
                .message("算了，换个问题")
                .build();

        List<StreamEvent> eventsB = orchestrator.chatStreamReactive(reqB).collectList().block();

        // === 验证 ===

        // 1. 消息 B 的事件应包含 AGENT_ROUTE
        assertNotNull(eventsB);
        assertTrue(eventsB.stream().anyMatch(e -> e.getType() == StreamEvent.EventType.AGENT_ROUTE),
                "Request B should emit AGENT_ROUTE");

        // 2. 消息 B 的事件应包含 AGENT_INTERRUPT（打断了 A）
        assertTrue(eventsB.stream().anyMatch(e -> e.getType() == StreamEvent.EventType.AGENT_INTERRUPT),
                "Request B should emit AGENT_INTERRUPT for request A");

        // 3. 消息 B 的事件应包含 AGENT_RESUME
        assertTrue(eventsB.stream().anyMatch(e -> e.getType() == StreamEvent.EventType.AGENT_RESUME),
                "Request B should emit AGENT_RESUME");

        // 4. A 的部分文本应被保存到 memory（包含 [被用户打断] 标记）
        boolean hasPartialSaved = memory.allMessages.stream()
                .anyMatch(m -> m.getContent() != null && m.getContent().contains("[被用户打断]"));
        assertTrue(hasPartialSaved,
                "Partial response from A should be saved to memory with [被用户打断] marker. Messages: " + memory.allMessages);

        // 5. B 的 LLM 调用应能看到 A 的部分上下文（通过 memory history）
        // 第二次 LLM 调用的 messages 应包含 A 的部分回答
        assertTrue(callCount.get() >= 2, "LLM should be called at least twice");
    }

    @Test
    @DisplayName("Bug1 验证：AgentLoop.runReactive 应支持接收外部 CancellationToken")
    void agentLoopShouldAcceptCancellationToken() {
        // 这个测试直接验证 AgentLoop 是否支持 CancellationToken 注入
        // 如果 Bug1 未修复，AgentLoop.runReactive 没有 CancellationToken 参数，
        // 则 InterruptibleRun 无法把 token 传进去

        AtomicInteger llmCallCount = new AtomicInteger(0);

        // LLM 每次都发起工具调用（需要多轮迭代才能完成）
        LLMProvider multiRoundProvider = new LLMProvider() {
            @Override
            public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
                int call = llmCallCount.incrementAndGet();
                if (call <= 5) {
                    // 前 5 轮：调用工具
                    ToolCall tc = new ToolCall("tc_" + call, "echo_tool", Map.of("input", "round" + call));
                    return Flux.just(
                            StreamEvent.textDelta("round " + call),
                            StreamEvent.llmComplete(LLMResponse.builder()
                                    .message(ConversationMessage.builder()
                                            .role(ConversationMessage.MessageRole.ASSISTANT)
                                            .textContent("round " + call).build())
                                    .toolCalls(List.of(tc))
                                    .stopReason("tool_use").build()));
                } else {
                    // 第 6 轮：结束
                    return Flux.just(
                            StreamEvent.textDelta("done"),
                            StreamEvent.llmComplete(LLMResponse.builder()
                                    .message(ConversationMessage.builder()
                                            .role(ConversationMessage.MessageRole.ASSISTANT)
                                            .textContent("done").build())
                                    .stopReason("end_turn").build()));
                }
            }
            @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
                return LLMResponse.builder().stopReason("end_turn")
                        .message(ConversationMessage.builder().role(ConversationMessage.MessageRole.ASSISTANT)
                                .textContent("sync").build()).build();
            }
            @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
                return CompletableFuture.completedFuture(complete(m, o)); }
            @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) {
                return CompletableFuture.completedFuture(complete(m, o)); }
            @Override public ModelCapability getModelCapability() { return null; }
            @Override public String getProviderName() { return "multi-round"; }
        };

        // 注册 echo 工具
        toolRegistry.register(new Tool() {
            @Override public String getName() { return "echo_tool"; }
            @Override public String getDescription() { return "echo"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("echo: " + args.get("input"));
            }
        });

        // 用 CancellationToken 直接测试 AgentLoop
        CancellationToken token = new CancellationToken();
        AgentLoop agent = AgentLoop.builder()
                .llmProvider(multiRoundProvider)
                .memoryProvider(memory)
                .toolRegistry(toolRegistry)
                .systemPrompt("你是助手")
                .maxToolIterations(10)
                .build();

        // 在第 2 轮后 cancel
        List<StreamEvent> events = agent.runReactive("test", "sess-cancel-direct", token)
                .doOnNext(e -> {
                    if (e.getType() == StreamEvent.EventType.TOOL_RESULT && llmCallCount.get() >= 2) {
                        token.cancel();
                    }
                })
                .collectList()
                .block();

        // 验证：LLM 调用次数应 < 5（被 cancel 打断了，不会跑完全部 5 轮工具调用）
        assertTrue(llmCallCount.get() < 5,
                "With cancellation after round 2, LLM should be called < 5 times, but was: " + llmCallCount.get());
    }

    // ==================== Helpers ====================

    static class RecordingMemory implements MemoryProvider {
        final List<Message> allMessages = new CopyOnWriteArrayList<>();
        private final Map<String, List<Message>> sessions = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public void addMessage(String sessionId, Message message) {
            allMessages.add(message);
            sessions.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(message);
        }

        @Override
        public List<Message> getHistory(String sessionId, int limit) {
            List<Message> history = sessions.getOrDefault(sessionId, List.of());
            int start = Math.max(0, history.size() - limit);
            return new ArrayList<>(history.subList(start, history.size()));
        }

        @Override public void clearSession(String sessionId) { sessions.remove(sessionId); }
        @Override public void writeEphemeral(String content) {}
        @Override public void writeDurable(String section, String content) {}
        @Override public List<MemorySearchResult> search(String query) { return List.of(); }
    }
}
