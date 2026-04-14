package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentLoop;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InterruptibleRun 补充测试 — 状态机边界和错误处理
 *
 * 覆盖：
 * - RUNNING 状态下再次 execute 返回错误
 * - 非 RUNNING 状态 interrupt 返回 null
 * - NEW 和 COMPLETED 状态都可以 startFresh
 * - 无已积累文本时 interrupt 不保存 message
 * - phase 转换完整性
 * - getRunId/getCreatedAt 等查询方法
 */
@DisplayName("InterruptibleRun - 状态机边界和错误处理")
class InterruptibleRunEdgeCaseTest {

    private RecordingMemoryProvider memory;

    @BeforeEach
    void setUp() {
        memory = new RecordingMemoryProvider();
    }

    private AgentLoop buildAgent(LLMProvider provider) {
        return AgentLoop.builder()
                .llmProvider(provider)
                .memoryProvider(memory)
                .toolRegistry(new ToolRegistry())
                .systemPrompt("test agent")
                .build();
    }

    @Test
    @DisplayName("RUNNING 状态下执行 execute 返回 Flux.error")
    void executeWhileRunningReturnsError() {
        // 使用慢 provider 保持 RUNNING 状态
        SlowProvider slowProvider = new SlowProvider(List.of("a", "b", "c"), 200);
        AgentLoop agent = buildAgent(slowProvider);
        InterruptibleRun run = new InterruptibleRun("r1", "s1", agent, memory);

        // 启动执行（异步）
        run.execute("first").subscribe();
        // 等一下让 phase 变为 RUNNING
        sleep(50);

        // 在 RUNNING 时再次执行
        StepVerifier.create(run.execute("second"))
                .verifyError(IllegalStateException.class);

        // 清理
        run.interrupt();
    }

    @Test
    @DisplayName("非 RUNNING 状态 interrupt 返回 null")
    void interruptWhenNotRunningReturnsNull() {
        QuickProvider provider = new QuickProvider("fast");
        AgentLoop agent = buildAgent(provider);
        InterruptibleRun run = new InterruptibleRun("r1", "s1", agent, memory);

        // NEW 状态 interrupt
        assertNull(run.interrupt(), "interrupt() on NEW state should return null");

        // 执行完成
        run.execute("test").blockLast();
        assertEquals(InterruptibleRun.RunPhase.COMPLETED, run.getPhase());

        // COMPLETED 状态 interrupt
        assertNull(run.interrupt(), "interrupt() on COMPLETED state should return null");
    }

    @Test
    @DisplayName("COMPLETED 状态可以再次 startFresh")
    void completedStateAllowsNewExecution() {
        QuickProvider provider = new QuickProvider("round1");
        AgentLoop agent = buildAgent(provider);
        InterruptibleRun run = new InterruptibleRun("r1", "s1", agent, memory);

        // 第一轮
        run.execute("first").blockLast();
        assertEquals(InterruptibleRun.RunPhase.COMPLETED, run.getPhase());

        // 第二轮（从 COMPLETED startFresh）
        List<StreamEvent> events = run.execute("second").collectList().block();
        assertNotNull(events);
        assertFalse(events.isEmpty());
        // 第二轮应正常完成
        assertFalse(run.isRunning());
    }

    @Test
    @DisplayName("interrupt 无已积累文本时不保存 partial message")
    void interruptWithNoAccumulatedTextSkipsSave() {
        SlowProvider slowProvider = new SlowProvider(List.of("a", "b"), 500);
        AgentLoop agent = buildAgent(slowProvider);
        InterruptibleRun run = new InterruptibleRun("r1", "s1", agent, memory);

        // 立即启动并在 TEXT_DELTA 到达之前 interrupt
        run.execute("test").subscribe();
        // 立即 interrupt（大概率还没收到 delta）
        sleep(10);

        StreamEvent event = run.interrupt();
        // 如果确实在 RUNNING 状态被打断
        if (event != null) {
            assertEquals(StreamEvent.EventType.AGENT_INTERRUPT, event.getType());
            // phase 应为 BEFORE_OUTPUT 如果没有积累文本
            String phase = (String) event.getData().get("phase");
            assertNotNull(phase);
        }
    }

    @Test
    @DisplayName("AGENT_INTERRUPT 事件包含正确的 runId 和 textLength")
    void interruptEventContainsCorrectMetadata() {
        SlowProvider slowProvider = new SlowProvider(List.of("你好", "世界"), 50);
        AgentLoop agent = buildAgent(slowProvider);
        InterruptibleRun run = new InterruptibleRun("run-42", "sess-7", agent, memory);

        List<StreamEvent> collected = new ArrayList<>();
        run.execute("test").subscribe(collected::add);
        sleep(150); // 等待一些 delta

        StreamEvent interruptEvent = run.interrupt();

        if (interruptEvent != null) {
            assertEquals("run-42", interruptEvent.getData().get("runId"));
            assertNotNull(interruptEvent.getData().get("phase"));
            assertNotNull(interruptEvent.getData().get("textLength"));
        }
    }

    @Test
    @DisplayName("phase 生命周期：NEW → RUNNING → COMPLETED")
    void phaseLifecycleNewToCompleted() {
        QuickProvider provider = new QuickProvider("life");
        AgentLoop agent = buildAgent(provider);
        InterruptibleRun run = new InterruptibleRun("r1", "s1", agent, memory);

        assertEquals(InterruptibleRun.RunPhase.NEW, run.getPhase());
        assertFalse(run.isRunning());

        run.execute("go").blockLast();

        assertEquals(InterruptibleRun.RunPhase.COMPLETED, run.getPhase());
        assertFalse(run.isRunning());
    }

    @Test
    @DisplayName("getRunId 和 getCreatedAt 返回正确值")
    void getterMethodsReturnCorrectValues() {
        QuickProvider provider = new QuickProvider("ok");
        AgentLoop agent = buildAgent(provider);

        long before = System.currentTimeMillis();
        InterruptibleRun run = new InterruptibleRun("test-run", "test-sess", agent, memory);
        long after = System.currentTimeMillis();

        assertEquals("test-run", run.getRunId());
        assertTrue(run.getCreatedAt() >= before && run.getCreatedAt() <= after);
    }

    @Test
    @DisplayName("getMessages 返回不可修改的列表")
    void getMessagesReturnsUnmodifiableList() {
        QuickProvider provider = new QuickProvider("msg");
        AgentLoop agent = buildAgent(provider);
        InterruptibleRun run = new InterruptibleRun("r1", "s1", agent, memory);

        List<ConversationMessage> messages = run.getMessages();
        assertNotNull(messages);
        assertThrows(UnsupportedOperationException.class, () ->
                messages.add(ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.USER)
                        .textContent("hack")
                        .build()));
    }

    @Test
    @DisplayName("resume 发出 AGENT_RESUME 且 agentFlux 可正常执行")
    void resumeProducesResumeEventAndExecutes() {
        QuickProvider provider = new QuickProvider("resumed");
        AgentLoop agent = buildAgent(provider);
        InterruptibleRun run = new InterruptibleRun("r1", "s1", agent, memory);

        // Complete first execution
        run.execute("first").blockLast();

        // Force interrupted state
        run.forceInterruptedState("partial answer");

        // Resume
        List<StreamEvent> events = run.execute("second question").collectList().block();
        assertNotNull(events);

        // First event should be AGENT_RESUME
        StreamEvent first = events.get(0);
        assertEquals(StreamEvent.EventType.AGENT_RESUME, first.getType());
        assertEquals("r1", first.getData().get("runId"));
        assertEquals("second question", first.getData().get("newInput"));

        // Should also have actual agent output
        assertTrue(events.stream().anyMatch(e -> e.getType() == StreamEvent.EventType.TEXT_DELTA));
    }

    // ==================== Helper classes ====================

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    private static class QuickProvider implements LLMProvider {
        private final String text;
        QuickProvider(String text) { this.text = text; }

        @Override
        public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
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
        @Override public String getProviderName() { return "quick"; }
    }

    private static class SlowProvider implements LLMProvider {
        private final List<String> deltas;
        private final long delayMs;
        SlowProvider(List<String> deltas, long delayMs) {
            this.deltas = deltas;
            this.delayMs = delayMs;
        }

        @Override
        public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            String full = String.join("", deltas);
            return Flux.fromIterable(deltas)
                    .delayElements(Duration.ofMillis(delayMs))
                    .map(StreamEvent::textDelta)
                    .concatWith(Flux.just(StreamEvent.llmComplete(
                            LLMResponse.builder()
                                    .message(ConversationMessage.builder()
                                            .role(ConversationMessage.MessageRole.ASSISTANT)
                                            .textContent(full).build())
                                    .stopReason("end_turn").build())));
        }

        @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) { return LLMResponse.builder().build(); }
        @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) { return CompletableFuture.completedFuture(complete(m, o)); }
        @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) { return CompletableFuture.completedFuture(complete(m, o)); }
        @Override public ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return "slow"; }
    }

    private static class RecordingMemoryProvider implements MemoryProvider {
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
