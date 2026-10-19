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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InterruptibleRun - 边界情况")
class InterruptibleRunEdgeCaseTest {

    private RecordingMemory memory;

    @BeforeEach
    void setUp() {
        memory = new RecordingMemory();
    }

    private AgentLoop buildAgent(LLMProvider provider) {
        return AgentLoop.builder()
                .llmProvider(provider)
                .memoryProvider(memory)
                .toolRegistry(new ToolRegistry())
                .systemPrompt("test")
                .build();
    }

    @Test
    @DisplayName("interrupt 非 RUNNING 状态返回 null")
    void interruptWhenNotRunningReturnsNull() {
        AgentLoop agent = buildAgent(new FastProvider("ok"));
        InterruptibleRun run = new InterruptibleRun("r1", "s1", agent, memory);

        // NEW 状态下 interrupt
        StreamEvent event = run.interrupt();
        assertNull(event, "Interrupt on NEW phase should return null");
        assertEquals(InterruptibleRun.RunPhase.NEW, run.getPhase());
    }

    @Test
    @DisplayName("COMPLETED 后可以重新 execute（新一轮）")
    void completedRunCanBeReexecuted() {
        AgentLoop agent = buildAgent(new FastProvider("first"));
        InterruptibleRun run = new InterruptibleRun("r1", "s1", agent, memory);

        // 第一次执行完成
        run.execute("hello").blockLast();
        assertEquals(InterruptibleRun.RunPhase.COMPLETED, run.getPhase());

        // 第二次执行（应走 startFresh 路径）
        List<StreamEvent> events = run.execute("hello again").collectList().block();
        assertNotNull(events);
        assertTrue(events.stream().anyMatch(e -> e.getType() == StreamEvent.EventType.TEXT_DELTA));
        assertEquals(InterruptibleRun.RunPhase.COMPLETED, run.getPhase());
    }

    @Test
    @DisplayName("execute 在 RUNNING 状态下返回错误 Flux")
    void executeWhileRunningReturnsError() throws InterruptedException {
        LLMProvider slowProvider = new SlowProvider(List.of("a", "b", "c", "d"), 200);
        AgentLoop agent = buildAgent(slowProvider);
        InterruptibleRun run = new InterruptibleRun("r1", "s1", agent, memory);

        // 开始执行
        run.execute("first").subscribe();
        Thread.sleep(100);

        assertTrue(run.isRunning(), "Should be running");

        // 再次 execute 应返回 error Flux
        AtomicReference<Throwable> error = new AtomicReference<>();
        run.execute("second").subscribe(
                e -> {},
                error::set,
                () -> {}
        );

        Thread.sleep(100);
        assertNotNull(error.get(), "Should get IllegalStateException");
        assertTrue(error.get() instanceof IllegalStateException);

        // 清理
        run.interrupt();
    }

    @Test
    @DisplayName("interrupt 后 accumulatedText 保存到 memory 且包含 [被用户打断] 标记")
    void interruptSavesPartialTextWithMarker() throws InterruptedException {
        LLMProvider slowProvider = new SlowProvider(List.of("你", "好", "世", "界"), 80);
        AgentLoop agent = buildAgent(slowProvider);
        InterruptibleRun run = new InterruptibleRun("r1", "s1", agent, memory);

        run.execute("问题").subscribe();
        Thread.sleep(250);

        StreamEvent event = run.interrupt();
        Thread.sleep(100);

        assertNotNull(event);
        assertEquals(StreamEvent.EventType.AGENT_INTERRUPT, event.getType());

        // memory 中应有含 [被用户打断] 的 assistant 消息
        List<Message> history = memory.getHistory("s1", 100);
        boolean hasInterruptMarker = history.stream()
                .anyMatch(m -> m.getContent() != null && m.getContent().contains("[被用户打断]"));
        assertTrue(hasInterruptMarker, "Memory should contain interrupted marker. History: " + history);
    }

    @Test
    @DisplayName("interrupt 返回的事件包含 runId 和 textLength")
    void interruptEventContainsPayload() throws InterruptedException {
        LLMProvider slowProvider = new SlowProvider(List.of("ab", "cd"), 80);
        AgentLoop agent = buildAgent(slowProvider);
        InterruptibleRun run = new InterruptibleRun("test-run", "s1", agent, memory);

        run.execute("q").subscribe();
        Thread.sleep(150);

        StreamEvent event = run.interrupt();
        assertNotNull(event);
        assertEquals("test-run", event.getData().get("runId"));
        assertNotNull(event.getData().get("textLength"));
        assertNotNull(event.getData().get("phase"));
    }

    @Test
    @DisplayName("resume 后事件流包含 AGENT_RESUME 且其 payload 正确")
    void resumeEventPayloadIsCorrect() {
        AgentLoop agent = buildAgent(new FastProvider("ok"));
        InterruptibleRun run = new InterruptibleRun("r1", "s1", agent, memory);

        run.execute("first").blockLast();
        run.forceInterruptedState("partial text");

        List<StreamEvent> events = run.execute("new question").collectList().block();
        assertNotNull(events);

        StreamEvent resumeEvent = events.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.AGENT_RESUME)
                .findFirst().orElseThrow(() -> new AssertionError("Missing AGENT_RESUME"));

        assertEquals("r1", resumeEvent.getData().get("runId"));
        assertEquals("new question", resumeEvent.getData().get("newInput"));
        assertTrue((int) resumeEvent.getData().get("contextSize") >= 0);
    }

    @Test
    @DisplayName("getCreatedAt 返回构造时间戳")
    void getCreatedAtReturnsTimestamp() {
        long before = System.currentTimeMillis();
        AgentLoop agent = buildAgent(new FastProvider("ok"));
        InterruptibleRun run = new InterruptibleRun("r1", "s1", agent, memory);
        long after = System.currentTimeMillis();

        assertTrue(run.getCreatedAt() >= before && run.getCreatedAt() <= after);
    }

    @Test
    @DisplayName("getRunId 返回构造时传入的 runId")
    void getRunIdReturnsCorrectId() {
        AgentLoop agent = buildAgent(new FastProvider("ok"));
        InterruptibleRun run = new InterruptibleRun("my-run-id", "s1", agent, memory);
        assertEquals("my-run-id", run.getRunId());
    }

    // ==================== Helpers ====================

    private static class FastProvider implements LLMProvider {
        private final String text;
        FastProvider(String text) { this.text = text; }
        @Override public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            return Flux.just(
                    StreamEvent.textDelta(text),
                    StreamEvent.llmComplete(LLMResponse.builder()
                            .message(ConversationMessage.builder()
                                    .role(ConversationMessage.MessageRole.ASSISTANT).textContent(text).build())
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
        @Override public String getProviderName() { return "fast"; }
    }

    private static class SlowProvider implements LLMProvider {
        private final List<String> deltas;
        private final long delayMs;
        SlowProvider(List<String> deltas, long delayMs) { this.deltas = deltas; this.delayMs = delayMs; }
        @Override public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            String full = String.join("", deltas);
            return Flux.fromIterable(deltas)
                    .delayElements(Duration.ofMillis(delayMs))
                    .map(StreamEvent::textDelta)
                    .concatWith(Flux.just(StreamEvent.llmComplete(LLMResponse.builder()
                            .message(ConversationMessage.builder()
                                    .role(ConversationMessage.MessageRole.ASSISTANT).textContent(full).build())
                            .stopReason("end_turn").build())));
        }
        @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) { return LLMResponse.builder().build(); }
        @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) { return CompletableFuture.completedFuture(complete(m, o)); }
        @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) { return CompletableFuture.completedFuture(complete(m, o)); }
        @Override public ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return "slow"; }
    }

    private static class RecordingMemory implements MemoryProvider {
        private final List<Message> messages = java.util.Collections.synchronizedList(new ArrayList<>());
        @Override public void addMessage(String sessionId, Message message) { messages.add(message); }
        @Override public List<Message> getHistory(String sessionId, int limit) {
            synchronized (messages) {
                int start = Math.max(0, messages.size() - limit);
                return new ArrayList<>(messages.subList(start, messages.size()));
            }
        }
        @Override public void clearSession(String sessionId) { messages.clear(); }
        @Override public void writeEphemeral(String content) {}
        @Override public void writeDurable(String section, String content) {}
        @Override public List<MemorySearchResult> search(String query) { return List.of(); }
    }

    private static class AssertionError extends RuntimeException {
        AssertionError(String msg) { super(msg); }
    }
}
