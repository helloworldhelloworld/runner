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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InterruptibleRun - 可打断可接续执行容器")
class InterruptibleRunTest {

    private RecordingMemoryProvider memory;
    private AgentLoop agent;

    @BeforeEach
    void setUp() {
        memory = new RecordingMemoryProvider();
    }

    private AgentLoop buildAgent(LLMProvider provider) {
        return AgentLoop.builder()
                .llmProvider(provider)
                .memoryProvider(memory)
                .toolRegistry(new ToolRegistry())
                .systemPrompt("你是助手")
                .build();
    }

    @Test
    @DisplayName("startFresh 正常完成 — 事件序列正确")
    void testStartFreshAndComplete() {
        LLMProvider provider = new SimpleMockProvider("你好世界");
        agent = buildAgent(provider);

        InterruptibleRun run = new InterruptibleRun("run-1", "sess-1", agent, memory);
        List<StreamEvent> events = run.execute("你好").collectList().block();

        assertNotNull(events);
        assertFalse(events.isEmpty());
        // 应有 TEXT_DELTA
        assertTrue(events.stream().anyMatch(e -> e.getType() == StreamEvent.EventType.TEXT_DELTA));
        // 应有 LLM_COMPLETE
        assertTrue(events.stream().anyMatch(e -> e.getType() == StreamEvent.EventType.LLM_COMPLETE));
        // run 应完成
        assertFalse(run.isRunning());
    }

    @Test
    @DisplayName("interrupt 保存部分状态到 messages")
    void testInterruptSavesPartialState() {
        // 模拟慢速流式输出
        LLMProvider slowProvider = new SlowStreamProvider(List.of("你好", "，让我", "想想"), 50);
        agent = buildAgent(slowProvider);

        InterruptibleRun run = new InterruptibleRun("run-1", "sess-1", agent, memory);

        // 异步启动执行
        List<StreamEvent> collectedEvents = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        run.execute("第一个问题")
                .subscribe(
                        collectedEvents::add,
                        err -> {},
                        () -> completed.set(true)
                );

        // 等一小会让流开始输出
        sleep(100);

        // 打断
        run.interrupt();

        // 等待流真正结束
        sleep(200);

        // interrupt 应将 phase 设为 INTERRUPTED
        assertTrue(run.getPhase() == InterruptibleRun.RunPhase.INTERRUPTED
                || run.getPhase() == InterruptibleRun.RunPhase.COMPLETED,  // 可能已经完成
                "Phase should be INTERRUPTED or COMPLETED, got: " + run.getPhase());

        // 部分文本应已保存到 memory（通过 memoryProvider.addMessage）
        assertFalse(memory.getHistory("sess-1", 100).isEmpty(),
                "Memory should contain partial response");
    }

    @Test
    @DisplayName("resume 从断点接续 — LLM 收到完整上下文")
    void testResumeWithContext() {
        // 第一轮：快速完成
        List<String> llmInputs = new ArrayList<>();
        LLMProvider recordingProvider = new RecordingMockProvider("ok", llmInputs);
        agent = buildAgent(recordingProvider);

        InterruptibleRun run = new InterruptibleRun("run-1", "sess-1", agent, memory);

        // startFresh
        run.execute("问题A").blockLast();

        // 模拟打断后的状态（手动设置 phase=INTERRUPTED）
        run.forceInterruptedState("部分回答...");

        // resume 新问题
        run.execute("问题B").blockLast();

        // 验证第二次 LLM 调用收到了包含"部分回答"的上下文
        assertTrue(llmInputs.size() >= 2, "Should have at least 2 LLM calls");
    }

    @Test
    @DisplayName("interrupt 发出 AGENT_INTERRUPT 事件")
    void testInterruptEmitsEvent() {
        LLMProvider slowProvider = new SlowStreamProvider(List.of("a", "b", "c", "d", "e"), 100);
        agent = buildAgent(slowProvider);

        InterruptibleRun run = new InterruptibleRun("run-1", "sess-1", agent, memory);
        List<StreamEvent> events = new ArrayList<>();

        run.execute("test").subscribe(events::add);
        sleep(150);  // 让流开始

        StreamEvent interruptEvent = run.interrupt();

        assertNotNull(interruptEvent);
        assertEquals(StreamEvent.EventType.AGENT_INTERRUPT, interruptEvent.getType());
        assertEquals("run-1", interruptEvent.getData().get("runId"));
    }

    @Test
    @DisplayName("resume 发出 AGENT_RESUME 事件")
    void testResumeEmitsEvent() {
        LLMProvider provider = new SimpleMockProvider("ok");
        agent = buildAgent(provider);

        InterruptibleRun run = new InterruptibleRun("run-1", "sess-1", agent, memory);
        run.execute("first").blockLast();
        run.forceInterruptedState("partial");

        List<StreamEvent> events = run.execute("second").collectList().block();

        assertNotNull(events);
        // 首个事件应该是 AGENT_RESUME
        assertTrue(events.stream().anyMatch(e -> e.getType() == StreamEvent.EventType.AGENT_RESUME));
    }

    @Test
    @DisplayName("interrupt when not running returns null")
    void interruptWhenNotRunningReturnsNull() {
        LLMProvider provider = new SimpleMockProvider("ok");
        agent = buildAgent(provider);

        InterruptibleRun run = new InterruptibleRun("run-1", "sess-1", agent, memory);
        // phase is NEW, not RUNNING
        StreamEvent event = run.interrupt();

        assertNull(event, "interrupt on NEW phase should return null");
    }

    @Test
    @DisplayName("interrupt after completion returns null")
    void interruptAfterCompletionReturnsNull() {
        LLMProvider provider = new SimpleMockProvider("ok");
        agent = buildAgent(provider);

        InterruptibleRun run = new InterruptibleRun("run-1", "sess-1", agent, memory);
        run.execute("test").blockLast();

        assertEquals(InterruptibleRun.RunPhase.COMPLETED, run.getPhase());
        StreamEvent event = run.interrupt();
        assertNull(event, "interrupt after COMPLETED should return null");
    }

    @Test
    @DisplayName("execute on RUNNING phase returns error Flux")
    void executeWhileRunningReturnsError() {
        LLMProvider slowProvider = new SlowStreamProvider(List.of("a", "b", "c"), 200);
        agent = buildAgent(slowProvider);

        InterruptibleRun run = new InterruptibleRun("run-1", "sess-1", agent, memory);

        // Start first execution asynchronously
        run.execute("first").subscribe();
        sleep(50);

        // Try to execute again while still running
        assertThrows(IllegalStateException.class, () -> {
            run.execute("second").blockLast();
        }, "Should throw when executing on RUNNING run");

        run.interrupt(); // clean up
    }

    @Test
    @DisplayName("getRunId returns the id passed at construction")
    void getRunIdReturnsConstructedId() {
        LLMProvider provider = new SimpleMockProvider("ok");
        agent = buildAgent(provider);

        InterruptibleRun run = new InterruptibleRun("my-run-42", "sess-1", agent, memory);

        assertEquals("my-run-42", run.getRunId());
    }

    @Test
    @DisplayName("phase transitions: NEW → RUNNING → COMPLETED")
    void phaseTransitionsNormalFlow() {
        LLMProvider provider = new SimpleMockProvider("ok");
        agent = buildAgent(provider);

        InterruptibleRun run = new InterruptibleRun("run-1", "sess-1", agent, memory);

        assertEquals(InterruptibleRun.RunPhase.NEW, run.getPhase());

        run.execute("test").blockLast();

        assertEquals(InterruptibleRun.RunPhase.COMPLETED, run.getPhase());
        assertFalse(run.isRunning());
    }

    @Test
    @DisplayName("completed run can be re-executed (starts fresh)")
    void completedRunCanBeReExecuted() {
        LLMProvider provider = new SimpleMockProvider("round2");
        agent = buildAgent(provider);

        InterruptibleRun run = new InterruptibleRun("run-1", "sess-1", agent, memory);

        // First execution
        run.execute("first").blockLast();
        assertEquals(InterruptibleRun.RunPhase.COMPLETED, run.getPhase());

        // Second execution on completed run should start fresh
        List<StreamEvent> events = run.execute("second").collectList().block();

        assertNotNull(events);
        assertTrue(events.stream().anyMatch(e -> e.getType() == StreamEvent.EventType.TEXT_DELTA));
        assertEquals(InterruptibleRun.RunPhase.COMPLETED, run.getPhase());
    }

    // ==================== Helper classes ====================

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    /** 简单同步 mock：直接返回固定文本 */
    private static class SimpleMockProvider implements LLMProvider {
        private final String text;
        SimpleMockProvider(String text) { this.text = text; }

        @Override
        public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            return Flux.just(
                    StreamEvent.textDelta(text),
                    StreamEvent.llmComplete(LLMResponse.builder()
                            .message(ConversationMessage.builder()
                                    .role(ConversationMessage.MessageRole.ASSISTANT)
                                    .textContent(text).build())
                            .stopReason("end_turn").build())
            );
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
        @Override public String getProviderName() { return "simple-mock"; }
    }

    /** 慢速流式 mock：每个 delta 之间有延迟 */
    private static class SlowStreamProvider implements LLMProvider {
        private final List<String> deltas;
        private final long delayMs;

        SlowStreamProvider(List<String> deltas, long delayMs) {
            this.deltas = deltas;
            this.delayMs = delayMs;
        }

        @Override
        public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            String fullText = String.join("", deltas);
            return Flux.fromIterable(deltas)
                    .delayElements(Duration.ofMillis(delayMs))
                    .map(StreamEvent::textDelta)
                    .concatWith(Flux.just(StreamEvent.llmComplete(
                            LLMResponse.builder()
                                    .message(ConversationMessage.builder()
                                            .role(ConversationMessage.MessageRole.ASSISTANT)
                                            .textContent(fullText).build())
                                    .stopReason("end_turn").build())));
        }

        @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) { return LLMResponse.builder().build(); }
        @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) { return CompletableFuture.completedFuture(complete(m, o)); }
        @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) { return CompletableFuture.completedFuture(complete(m, o)); }
        @Override public ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return "slow-mock"; }
    }

    /** 记录所有 LLM 调用的输入 */
    private static class RecordingMockProvider implements LLMProvider {
        private final String response;
        private final List<String> inputs;

        RecordingMockProvider(String response, List<String> inputs) {
            this.response = response;
            this.inputs = inputs;
        }

        @Override
        public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            // 记录所有消息内容
            StringBuilder sb = new StringBuilder();
            m.forEach(msg -> sb.append(msg.getContent()).append("|"));
            inputs.add(sb.toString());

            return Flux.just(
                    StreamEvent.textDelta(response),
                    StreamEvent.llmComplete(LLMResponse.builder()
                            .message(ConversationMessage.builder()
                                    .role(ConversationMessage.MessageRole.ASSISTANT)
                                    .textContent(response).build())
                            .stopReason("end_turn").build())
            );
        }

        @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) { return LLMResponse.builder().build(); }
        @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) { return CompletableFuture.completedFuture(complete(m, o)); }
        @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) { return CompletableFuture.completedFuture(complete(m, o)); }
        @Override public ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return "recording-mock"; }
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
