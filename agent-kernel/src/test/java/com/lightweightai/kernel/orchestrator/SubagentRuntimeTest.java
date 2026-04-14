package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.StreamEvent;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SubagentRuntime - 子 Agent spawn/announce/lifecycle")
class SubagentRuntimeTest {

    private AgentRegistry registry;
    private AgentFactory factory;
    private SubagentRuntime runtime;
    private TestMemory memory;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("worker")
                .systemPrompt("I am worker")
                .maxSpawnDepth(1)
                .build());
        registry.register(AgentProfile.builder()
                .agentId("orchestrator-agent")
                .systemPrompt("I orchestrate")
                .maxSpawnDepth(2)
                .build());
        registry.register(AgentProfile.builder()
                .agentId("no-spawn")
                .systemPrompt("I cannot spawn")
                .maxSpawnDepth(0)
                .build());

        memory = new TestMemory();
        ToolRegistry tools = new ToolRegistry();
        LLMProvider provider = new FastMockProvider();
        factory = new AgentFactory(provider, memory, tools);
        runtime = new SubagentRuntime(factory, registry, 5);
    }

    @Test
    @DisplayName("spawn 成功返回 runId 并发出 SUBAGENT_SPAWN 事件")
    void testSpawnEmitsEvent() throws InterruptedException {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:orchestrator-agent:main:s1")
                .task("帮我搜索文档")
                .agentId("worker")
                .build();

        List<StreamEvent> events = new ArrayList<>();
        String runId = runtime.spawn(request, events::add);

        assertNotNull(runId);
        assertFalse(runId.isEmpty());

        // 等待异步完成
        assertTrue(runtime.waitForCompletion(runId, 5, TimeUnit.SECONDS));

        // 应有 SUBAGENT_SPAWN 事件
        assertTrue(events.stream().anyMatch(e ->
                e.getType() == StreamEvent.EventType.SUBAGENT_SPAWN
                && runId.equals(e.getData().get("runId"))));
    }

    @Test
    @DisplayName("spawn 完成后发出 SUBAGENT_COMPLETE 事件（含 duration/tokenUsage）")
    void testCompleteEmitsEvent() throws InterruptedException {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("简单任务")
                .agentId("worker")
                .build();

        List<StreamEvent> events = java.util.Collections.synchronizedList(new ArrayList<>());
        String runId = runtime.spawn(request, events::add);
        assertTrue(runtime.waitForCompletion(runId, 5, TimeUnit.SECONDS));

        // 等待事件回调完成（异步线程中发出的事件可能略有延迟）
        Thread.sleep(100);

        // 应有 SUBAGENT_COMPLETE 事件
        assertTrue(events.stream().anyMatch(e ->
                e.getType() == StreamEvent.EventType.SUBAGENT_COMPLETE
                && runId.equals(e.getData().get("runId"))),
                "Expected SUBAGENT_COMPLETE event, got: " + events);

        // COMPLETE 事件应含 durationMs
        StreamEvent complete = events.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.SUBAGENT_COMPLETE)
                .findFirst().orElseThrow();
        assertNotNull(complete.getData().get("durationMs"));
    }

    @Test
    @DisplayName("超过 maxSpawnDepth 时拒绝 spawn")
    void testDepthLimit() {
        // worker 的 maxSpawnDepth = 1，从 depth=1 的 session 再 spawn 应该被拒绝
        // depth=1 的 session key 格式: agent:worker:subagent:<uuid>
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:worker:subagent:abc123")  // depth = 1
                .task("nested task")
                .agentId("worker")  // maxSpawnDepth = 1，但当前已在 depth 1
                .build();

        assertThrows(IllegalStateException.class, () ->
                runtime.spawn(request, e -> {}));
    }

    @Test
    @DisplayName("级联停止发出 SUBAGENT_CANCELLED 事件")
    void testCascadeStop() throws InterruptedException {
        // 创建一个慢任务
        LLMProvider slowProvider = new SlowMockProvider(2000);
        AgentFactory slowFactory = new AgentFactory(slowProvider, memory, new ToolRegistry());
        SubagentRuntime slowRuntime = new SubagentRuntime(slowFactory, registry, 5);

        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("慢任务")
                .agentId("worker")
                .build();

        List<StreamEvent> events = java.util.Collections.synchronizedList(new ArrayList<>());
        String runId = slowRuntime.spawn(request, events::add);

        // 等一小会让子 agent 开始
        Thread.sleep(100);

        // 级联停止（传入 announcer 回调）
        slowRuntime.stop(runId, events::add);

        // 等待取消完成
        Thread.sleep(200);

        // 应有 SUBAGENT_CANCELLED 事件
        assertTrue(events.stream().anyMatch(e ->
                e.getType() == StreamEvent.EventType.SUBAGENT_CANCELLED
                && runId.equals(e.getData().get("runId"))),
                "Expected SUBAGENT_CANCELLED event, got: " + events);
    }

    @Test
    @DisplayName("并发限制：超过 maxConcurrent 时抛异常")
    void testMaxConcurrent() throws InterruptedException {
        // 创建 maxConcurrent=2 的 runtime + 慢 provider
        LLMProvider slowProvider = new SlowMockProvider(5000);
        AgentFactory slowFactory = new AgentFactory(slowProvider, memory, new ToolRegistry());
        SubagentRuntime limitedRuntime = new SubagentRuntime(slowFactory, registry, 2);

        // spawn 2 个
        limitedRuntime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1").task("t1").agentId("worker").build(), e -> {});
        limitedRuntime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1").task("t2").agentId("worker").build(), e -> {});

        Thread.sleep(50);

        // 第 3 个应该被拒绝
        assertThrows(IllegalStateException.class, () ->
                limitedRuntime.spawn(SpawnRequest.builder()
                        .parentSessionKey("agent:worker:main:s1").task("t3").agentId("worker").build(), e -> {}));

        // 清理
        limitedRuntime.stopAll();
    }

    @Test
    @DisplayName("maxSpawnDepth=0 时拒绝 spawn")
    void testZeroDepthRejects() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:no-spawn:main:s1")
                .task("task")
                .agentId("no-spawn")  // maxSpawnDepth = 0
                .build();

        assertThrows(IllegalStateException.class, () ->
                runtime.spawn(request, e -> {}));
    }

    @Test
    @DisplayName("Acceptance: parent spawn child → child 和 parent 的 session key 正确命名空间化")
    void testSessionKeyNamespacing() throws InterruptedException {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:sess-1")
                .task("子任务")
                .agentId("worker")
                .build();

        List<StreamEvent> events = java.util.Collections.synchronizedList(new ArrayList<>());
        String runId = runtime.spawn(request, events::add);
        assertTrue(runtime.waitForCompletion(runId, 5, TimeUnit.SECONDS));
        Thread.sleep(100);

        // 子 agent 的 session key 应包含 parent key + :subagent: + runId
        SubagentRun run = runtime.getRunOrCompleted(runId);
        assertNotNull(run);
        assertTrue(run.getSessionKey().startsWith("agent:worker:main:sess-1:subagent:"),
                "Session key should be namespaced: " + run.getSessionKey());
        assertTrue(run.getSessionKey().contains(runId),
                "Session key should contain runId: " + run.getSessionKey());
    }

    @Test
    @DisplayName("Acceptance: orchestrator-agent (depth=2) 的 subagent 可以再 spawn")
    void testDepth2OrchestratorCanSpawnChildren() throws InterruptedException {
        // orchestrator-agent 有 maxSpawnDepth=2
        // 从 depth=0 (main) spawn → depth=1 → 应该允许
        SpawnRequest firstLevel = SpawnRequest.builder()
                .parentSessionKey("agent:orchestrator-agent:main:s1")
                .task("第一层子任务")
                .agentId("orchestrator-agent")
                .build();

        assertEquals(0, firstLevel.currentDepth());  // main session, depth=0

        List<StreamEvent> events = java.util.Collections.synchronizedList(new ArrayList<>());
        String runId1 = runtime.spawn(firstLevel, events::add);
        assertTrue(runtime.waitForCompletion(runId1, 5, TimeUnit.SECONDS));

        // 从 depth=1 session 再 spawn → depth=2 → maxSpawnDepth=2 刚好不允许
        SubagentRun firstRun = runtime.getRunOrCompleted(runId1);
        SpawnRequest secondLevel = SpawnRequest.builder()
                .parentSessionKey(firstRun.getSessionKey())  // depth=1
                .task("第二层子任务")
                .agentId("orchestrator-agent")
                .build();

        assertEquals(1, secondLevel.currentDepth());  // subagent session, depth=1

        // depth=1, maxSpawnDepth=2 → 1 < 2 → 允许
        String runId2 = runtime.spawn(secondLevel, events::add);
        assertTrue(runtime.waitForCompletion(runId2, 5, TimeUnit.SECONDS));
    }

    // ==================== Helper classes ====================

    private static class FastMockProvider implements LLMProvider {
        @Override
        public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            return Flux.just(
                    StreamEvent.textDelta("done"),
                    StreamEvent.llmComplete(buildResponse()));
        }
        private LLMResponse buildResponse() {
            return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent("done").build())
                    .stopReason("end_turn").build();
        }
        @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            return buildResponse();
        }
        @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
            return CompletableFuture.completedFuture(complete(m, o));
        }
        @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) {
            return CompletableFuture.completedFuture(complete(m, o));
        }
        @Override public ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return "fast-mock"; }
    }

    private static class SlowMockProvider implements LLMProvider {
        private final long delayMs;
        SlowMockProvider(long delayMs) { this.delayMs = delayMs; }
        @Override
        public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            return Flux.just(StreamEvent.textDelta("working..."))
                    .delayElements(java.time.Duration.ofMillis(delayMs))
                    .concatWith(Flux.just(StreamEvent.llmComplete(LLMResponse.builder()
                            .message(ConversationMessage.builder()
                                    .role(ConversationMessage.MessageRole.ASSISTANT)
                                    .textContent("done").build())
                            .stopReason("end_turn").build())));
        }
        @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
            return LLMResponse.builder().stopReason("end_turn").build();
        }
        @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
            return CompletableFuture.completedFuture(complete(m, o));
        }
        @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) {
            return CompletableFuture.completedFuture(complete(m, o));
        }
        @Override public ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return "slow-mock"; }
    }

    private static class TestMemory implements MemoryProvider {
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
