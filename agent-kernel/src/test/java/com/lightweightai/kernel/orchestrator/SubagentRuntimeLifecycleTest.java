package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.core.StreamEvent;
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SubagentRuntime - lifecycle: stopAll / shutdown / completedCache / getRunOrCompleted")
class SubagentRuntimeLifecycleTest {

    private AgentRegistry registry;
    private StubMemory memory;
    private SubagentRuntime runtime;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
        registry.register(AgentProfile.builder().agentId("worker").systemPrompt("w").maxSpawnDepth(1).build());
        registry.setDefault("worker");
        memory = new StubMemory();
    }

    @AfterEach
    void tearDown() {
        if (runtime != null) runtime.shutdown();
    }

    @Test
    @DisplayName("stopAll 取消所有活跃 run 后 activeCount 归零")
    void stopAllCancelsAllRuns() throws InterruptedException {
        LLMProvider slow = new SlowProvider(3000);
        AgentFactory factory = new AgentFactory(slow, memory, new ToolRegistry());
        runtime = new SubagentRuntime(factory, registry, 10);

        runtime.spawn(SpawnRequest.builder().parentSessionKey("agent:worker:main:s1")
                .task("t1").agentId("worker").build(), e -> {});
        runtime.spawn(SpawnRequest.builder().parentSessionKey("agent:worker:main:s1")
                .task("t2").agentId("worker").build(), e -> {});
        runtime.spawn(SpawnRequest.builder().parentSessionKey("agent:worker:main:s1")
                .task("t3").agentId("worker").build(), e -> {});

        Thread.sleep(50);
        assertEquals(3, runtime.getActiveCount());

        runtime.stopAll();
        assertEquals(0, runtime.getActiveCount());
    }

    @Test
    @DisplayName("shutdown 先 stopAll 再关闭线程池")
    void shutdownStopsAllAndClosesExecutor() throws InterruptedException {
        LLMProvider slow = new SlowProvider(3000);
        AgentFactory factory = new AgentFactory(slow, memory, new ToolRegistry());
        runtime = new SubagentRuntime(factory, registry, 10);

        runtime.spawn(SpawnRequest.builder().parentSessionKey("agent:worker:main:s1")
                .task("t1").agentId("worker").build(), e -> {});

        Thread.sleep(50);

        runtime.shutdown();
        assertEquals(0, runtime.getActiveCount());
    }

    @Test
    @DisplayName("getRunOrCompleted — 活跃 run 可查")
    void getRunOrCompletedFindsActive() throws InterruptedException {
        LLMProvider slow = new SlowProvider(2000);
        AgentFactory factory = new AgentFactory(slow, memory, new ToolRegistry());
        runtime = new SubagentRuntime(factory, registry, 10);

        String runId = runtime.spawn(SpawnRequest.builder().parentSessionKey("agent:worker:main:s1")
                .task("t1").agentId("worker").build(), e -> {});

        Thread.sleep(50);

        SubagentRun run = runtime.getRunOrCompleted(runId);
        assertNotNull(run, "Active run should be findable via getRunOrCompleted");
        assertEquals(runId, run.getRunId());
    }

    @Test
    @DisplayName("getRunOrCompleted — 已完成的 run 在 completedCache 中可查")
    void getRunOrCompletedFindsCompleted() throws InterruptedException {
        LLMProvider fast = new FastProvider();
        AgentFactory factory = new AgentFactory(fast, memory, new ToolRegistry());
        runtime = new SubagentRuntime(factory, registry, 10);

        String runId = runtime.spawn(SpawnRequest.builder().parentSessionKey("agent:worker:main:s1")
                .task("t1").agentId("worker").build(), e -> {});

        assertTrue(runtime.waitForCompletion(runId, 5, TimeUnit.SECONDS));
        Thread.sleep(100);

        // 活跃列表中应已移除
        assertNull(runtime.getRun(runId), "Active run should be removed after completion");

        // 但 getRunOrCompleted 应能从 completedCache 找到
        SubagentRun run = runtime.getRunOrCompleted(runId);
        assertNotNull(run, "Completed run should be in cache");
        assertEquals(SubagentRun.Status.COMPLETED, run.getStatus());
    }

    @Test
    @DisplayName("getRunOrCompleted — 不存在的 runId 返回 null")
    void getRunOrCompletedReturnsNullForUnknown() {
        LLMProvider fast = new FastProvider();
        AgentFactory factory = new AgentFactory(fast, memory, new ToolRegistry());
        runtime = new SubagentRuntime(factory, registry, 10);

        assertNull(runtime.getRunOrCompleted("nonexistent-id"));
    }

    @Test
    @DisplayName("getActiveRuns 返回所有活跃 run 的快照")
    void getActiveRunsReturnsSnapshot() throws InterruptedException {
        LLMProvider slow = new SlowProvider(3000);
        AgentFactory factory = new AgentFactory(slow, memory, new ToolRegistry());
        runtime = new SubagentRuntime(factory, registry, 10);

        runtime.spawn(SpawnRequest.builder().parentSessionKey("agent:worker:main:s1")
                .task("t1").agentId("worker").build(), e -> {});
        runtime.spawn(SpawnRequest.builder().parentSessionKey("agent:worker:main:s1")
                .task("t2").agentId("worker").build(), e -> {});

        Thread.sleep(50);

        List<SubagentRun> runs = runtime.getActiveRuns();
        assertEquals(2, runs.size());
    }

    @Test
    @DisplayName("stop 不存在的 runId — 静默不抛异常")
    void stopNonexistentRunIsSilent() {
        LLMProvider fast = new FastProvider();
        AgentFactory factory = new AgentFactory(fast, memory, new ToolRegistry());
        runtime = new SubagentRuntime(factory, registry, 10);

        assertDoesNotThrow(() -> runtime.stop("nonexistent"));
    }

    @Test
    @DisplayName("stop 不存在的 runId（带 announcer）— 静默不抛异常")
    void stopNonexistentRunWithAnnouncerIsSilent() {
        LLMProvider fast = new FastProvider();
        AgentFactory factory = new AgentFactory(fast, memory, new ToolRegistry());
        runtime = new SubagentRuntime(factory, registry, 10);

        List<StreamEvent> events = new ArrayList<>();
        assertDoesNotThrow(() -> runtime.stop("nonexistent", events::add));
        assertTrue(events.isEmpty(), "No events should be emitted for nonexistent run");
    }

    @Test
    @DisplayName("spawn 成功后 SubagentRun.isRunning 为 true")
    void spawnedRunIsRunning() throws InterruptedException {
        LLMProvider slow = new SlowProvider(3000);
        AgentFactory factory = new AgentFactory(slow, memory, new ToolRegistry());
        runtime = new SubagentRuntime(factory, registry, 10);

        String runId = runtime.spawn(SpawnRequest.builder().parentSessionKey("agent:worker:main:s1")
                .task("t1").agentId("worker").build(), e -> {});

        Thread.sleep(50);

        SubagentRun run = runtime.getRun(runId);
        assertNotNull(run);
        assertTrue(run.isRunning());
    }

    @Test
    @DisplayName("spawn 不存在的 agentId 抛 IllegalArgumentException")
    void spawnUnknownAgentThrows() {
        LLMProvider fast = new FastProvider();
        AgentFactory factory = new AgentFactory(fast, memory, new ToolRegistry());
        runtime = new SubagentRuntime(factory, registry, 10);

        assertThrows(IllegalArgumentException.class, () ->
                runtime.spawn(SpawnRequest.builder()
                        .parentSessionKey("agent:unknown:main:s1")
                        .task("t1")
                        .agentId("nonexistent-agent")
                        .build(), e -> {}));
    }

    @Test
    @DisplayName("spawn 失败后发出 SUBAGENT_ERROR 事件")
    void spawnErrorEmitsEvent() throws InterruptedException {
        LLMProvider errorProvider = new ErrorProvider(new RuntimeException("LLM exploded"));
        AgentFactory factory = new AgentFactory(errorProvider, memory, new ToolRegistry());
        runtime = new SubagentRuntime(factory, registry, 10);

        List<StreamEvent> events = Collections.synchronizedList(new ArrayList<>());
        String runId = runtime.spawn(SpawnRequest.builder().parentSessionKey("agent:worker:main:s1")
                .task("failing task").agentId("worker").build(), events::add);

        assertTrue(runtime.waitForCompletion(runId, 5, TimeUnit.SECONDS));
        Thread.sleep(100);

        assertTrue(events.stream().anyMatch(e ->
                        e.getType() == StreamEvent.EventType.SUBAGENT_ERROR
                                && runId.equals(e.getData().get("runId"))),
                "Should emit SUBAGENT_ERROR event, got: " + events);
    }

    // ==================== Helpers ====================

    private static class FastProvider implements LLMProvider {
        @Override public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            return Flux.just(StreamEvent.textDelta("done"),
                    StreamEvent.llmComplete(LLMResponse.builder()
                            .message(ConversationMessage.builder().role(ConversationMessage.MessageRole.ASSISTANT).textContent("done").build())
                            .stopReason("end_turn").build()));
        }
        @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            return LLMResponse.builder()
                    .message(ConversationMessage.builder().role(ConversationMessage.MessageRole.ASSISTANT).textContent("done").build())
                    .stopReason("end_turn").build();
        }
        @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) { return CompletableFuture.completedFuture(complete(m, o)); }
        @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) { return CompletableFuture.completedFuture(complete(m, o)); }
        @Override public ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return "fast"; }
    }

    private static class SlowProvider implements LLMProvider {
        private final long delayMs;
        SlowProvider(long delayMs) { this.delayMs = delayMs; }
        @Override public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            return Flux.just(StreamEvent.textDelta("working..."))
                    .delayElements(Duration.ofMillis(delayMs))
                    .concatWith(Flux.just(StreamEvent.llmComplete(LLMResponse.builder()
                            .message(ConversationMessage.builder().role(ConversationMessage.MessageRole.ASSISTANT).textContent("done").build())
                            .stopReason("end_turn").build())));
        }
        @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
            return LLMResponse.builder()
                    .message(ConversationMessage.builder().role(ConversationMessage.MessageRole.ASSISTANT).textContent("done").build())
                    .stopReason("end_turn").build();
        }
        @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) { return CompletableFuture.completedFuture(complete(m, o)); }
        @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) { return CompletableFuture.completedFuture(complete(m, o)); }
        @Override public ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return "slow"; }
    }

    private static class ErrorProvider implements LLMProvider {
        private final RuntimeException error;
        ErrorProvider(RuntimeException error) { this.error = error; }
        @Override public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) { return Flux.error(error); }
        @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) { throw error; }
        @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) { return CompletableFuture.failedFuture(error); }
        @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) { return CompletableFuture.failedFuture(error); }
        @Override public ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return "error"; }
    }

    private static class StubMemory implements MemoryProvider {
        private final List<Message> messages = Collections.synchronizedList(new ArrayList<>());
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
}
