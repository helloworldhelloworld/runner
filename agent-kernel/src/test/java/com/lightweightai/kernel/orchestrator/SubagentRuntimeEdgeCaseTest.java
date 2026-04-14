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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SubagentRuntime 补充测试 — 缓存、错误处理、边界条件
 *
 * 覆盖：
 * - getRunOrCompleted 从 completedRuns 缓存查找
 * - stopAll 清理所有活跃 run
 * - stop 不存在的 runId（no-op）
 * - 未知 agentId spawn 抛异常
 * - getActiveRuns 快照正确
 * - spawn 后 getActiveCount 一致性
 * - 子 agent 执行失败发出 SUBAGENT_ERROR 事件
 */
@DisplayName("SubagentRuntime - 缓存与边界条件")
class SubagentRuntimeEdgeCaseTest {

    private AgentRegistry registry;
    private AgentFactory factory;
    private SubagentRuntime runtime;
    private TestMemory memory;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("worker")
                .systemPrompt("worker")
                .maxSpawnDepth(2)
                .build());
        registry.register(AgentProfile.builder()
                .agentId("default")
                .systemPrompt("default")
                .maxSpawnDepth(1)
                .build());
        registry.setDefault("default");

        memory = new TestMemory();
        ToolRegistry tools = new ToolRegistry();
        LLMProvider provider = new FastProvider();
        factory = new AgentFactory(provider, memory, tools);
        runtime = new SubagentRuntime(factory, registry, 10);
    }

    @Test
    @DisplayName("getRunOrCompleted 在 run 完成后仍可查询（从 completedRuns 缓存）")
    void getRunOrCompletedFindsCompletedRuns() throws InterruptedException {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("cached task")
                .agentId("worker")
                .build();

        List<StreamEvent> events = java.util.Collections.synchronizedList(new ArrayList<>());
        String runId = runtime.spawn(request, events::add);
        assertTrue(runtime.waitForCompletion(runId, 5, TimeUnit.SECONDS));
        Thread.sleep(100);

        // 从 activeRuns 已移除，但 completedRuns 可查
        assertNull(runtime.getRun(runId), "Should be removed from activeRuns");
        SubagentRun cached = runtime.getRunOrCompleted(runId);
        assertNotNull(cached, "Should be found in completedRuns cache");
        assertEquals(runId, cached.getRunId());
    }

    @Test
    @DisplayName("stopAll 清理所有活跃 run")
    void stopAllClearsAllRuns() throws InterruptedException {
        // 使用慢 provider 保持 runs 活跃
        LLMProvider slowProvider = new SlowProvider(3000);
        AgentFactory slowFactory = new AgentFactory(slowProvider, memory, new ToolRegistry());
        SubagentRuntime slowRuntime = new SubagentRuntime(slowFactory, registry, 10);

        slowRuntime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1").task("t1").agentId("worker").build(), e -> {});
        slowRuntime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1").task("t2").agentId("worker").build(), e -> {});

        Thread.sleep(50);
        assertTrue(slowRuntime.getActiveCount() >= 2, "Should have at least 2 active runs");

        slowRuntime.stopAll();
        assertEquals(0, slowRuntime.getActiveCount(), "All runs should be cleared");
    }

    @Test
    @DisplayName("stop 不存在的 runId 是 no-op")
    void stopNonExistentRunIsNoop() {
        List<StreamEvent> events = new ArrayList<>();
        // 不应抛异常
        runtime.stop("non-existent-id", events::add);
        assertTrue(events.isEmpty(), "No events should be emitted for non-existent run");
    }

    @Test
    @DisplayName("spawn 未知 agentId 抛 IllegalArgumentException")
    void spawnUnknownAgentThrows() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:unknown:main:s1")
                .task("task")
                .agentId("totally-unknown-agent")
                .build();

        assertThrows(IllegalArgumentException.class, () ->
                runtime.spawn(request, e -> {}));
    }

    @Test
    @DisplayName("agentId=null 时 fallback 到 default agent")
    void spawnWithNullAgentIdUsesDefault() throws InterruptedException {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:default:main:s1")
                .task("default task")
                // agentId = null (不设置)
                .build();

        List<StreamEvent> events = java.util.Collections.synchronizedList(new ArrayList<>());
        String runId = runtime.spawn(request, events::add);
        assertTrue(runtime.waitForCompletion(runId, 5, TimeUnit.SECONDS));
        Thread.sleep(100);

        // 验证使用了 default agent
        assertTrue(events.stream().anyMatch(e ->
                e.getType() == StreamEvent.EventType.SUBAGENT_SPAWN));
    }

    @Test
    @DisplayName("getActiveRuns 返回当前快照")
    void getActiveRunsReturnsSnapshot() throws InterruptedException {
        LLMProvider slowProvider = new SlowProvider(3000);
        AgentFactory slowFactory = new AgentFactory(slowProvider, memory, new ToolRegistry());
        SubagentRuntime slowRuntime = new SubagentRuntime(slowFactory, registry, 10);

        String id1 = slowRuntime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1").task("t1").agentId("worker").build(), e -> {});
        String id2 = slowRuntime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1").task("t2").agentId("worker").build(), e -> {});

        Thread.sleep(50);

        List<SubagentRun> active = slowRuntime.getActiveRuns();
        assertEquals(2, active.size());

        // 快照应包含两个 run
        List<String> ids = active.stream().map(SubagentRun::getRunId).toList();
        assertTrue(ids.contains(id1));
        assertTrue(ids.contains(id2));

        slowRuntime.stopAll();
    }

    @Test
    @DisplayName("子 agent 执行失败发出 SUBAGENT_ERROR 事件")
    void failedSubagentEmitsErrorEvent() throws InterruptedException {
        // 使用会抛异常的 provider
        LLMProvider failProvider = new FailingProvider();
        AgentFactory failFactory = new AgentFactory(failProvider, memory, new ToolRegistry());
        SubagentRuntime failRuntime = new SubagentRuntime(failFactory, registry, 10);

        List<StreamEvent> events = java.util.Collections.synchronizedList(new ArrayList<>());
        String runId = failRuntime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("will fail")
                .agentId("worker")
                .build(), events::add);

        assertTrue(failRuntime.waitForCompletion(runId, 5, TimeUnit.SECONDS));
        Thread.sleep(200);

        assertTrue(events.stream().anyMatch(e ->
                e.getType() == StreamEvent.EventType.SUBAGENT_ERROR
                && runId.equals(e.getData().get("runId"))),
                "Expected SUBAGENT_ERROR event, got: " + events);
    }

    @Test
    @DisplayName("SpawnRequest.currentDepth 从 sessionKey 正确解析嵌套深度")
    void currentDepthParsesCorrectly() {
        // depth 0: main session
        SpawnRequest d0 = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("t").agentId("worker").build();
        assertEquals(0, d0.currentDepth());

        // depth 1: one subagent level
        SpawnRequest d1 = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1:subagent:abc123")
                .task("t").agentId("worker").build();
        assertEquals(1, d1.currentDepth());

        // depth 2: two subagent levels
        SpawnRequest d2 = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1:subagent:abc:subagent:def")
                .task("t").agentId("worker").build();
        assertEquals(2, d2.currentDepth());
    }

    @Test
    @DisplayName("SubagentRun 状态跟踪正确")
    void subagentRunStatusTracking() {
        SpawnRequest req = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("test")
                .agentId("worker")
                .build();
        SubagentRun run = new SubagentRun("r1", "sess-key", req);

        assertEquals(SubagentRun.Status.RUNNING, run.getStatus());
        assertTrue(run.isRunning());
        assertEquals("r1", run.getRunId());
        assertEquals("sess-key", run.getSessionKey());

        // Complete
        run.complete(null);
        assertEquals(SubagentRun.Status.COMPLETED, run.getStatus());
        assertFalse(run.isRunning());
    }

    @Test
    @DisplayName("SubagentRun.fail 记录错误信息")
    void subagentRunFailRecordsError() {
        SpawnRequest req = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("test")
                .agentId("worker")
                .build();
        SubagentRun run = new SubagentRun("r1", "sess-key", req);

        run.fail(new RuntimeException("something broke"));
        assertEquals(SubagentRun.Status.FAILED, run.getStatus());
        assertEquals("something broke", run.getErrorMessage());
    }

    @Test
    @DisplayName("SubagentRun.cancel 记录取消原因")
    void subagentRunCancelRecordsReason() {
        SpawnRequest req = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("test")
                .agentId("worker")
                .build();
        SubagentRun run = new SubagentRun("r1", "sess-key", req);

        run.cancel("user requested");
        assertEquals(SubagentRun.Status.CANCELLED, run.getStatus());
        assertEquals("user requested", run.getErrorMessage());
    }

    // ==================== Helper classes ====================

    private static class FastProvider implements LLMProvider {
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
        @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) { return buildResponse(); }
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
        private final long delayMs;
        SlowProvider(long delayMs) { this.delayMs = delayMs; }
        @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
            return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent("slow done").build())
                    .stopReason("end_turn").build();
        }
        @Override public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            return Flux.just(StreamEvent.textDelta("working..."))
                    .delayElements(java.time.Duration.ofMillis(delayMs));
        }
        @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
            return CompletableFuture.completedFuture(complete(m, o));
        }
        @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) {
            return CompletableFuture.completedFuture(complete(m, o));
        }
        @Override public ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return "slow"; }
    }

    private static class FailingProvider implements LLMProvider {
        @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            throw new RuntimeException("LLM provider failed");
        }
        @Override public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            return Flux.error(new RuntimeException("LLM provider failed"));
        }
        @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
            return CompletableFuture.failedFuture(new RuntimeException("LLM provider failed"));
        }
        @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) {
            return CompletableFuture.failedFuture(new RuntimeException("LLM provider failed"));
        }
        @Override public ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return "failing"; }
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
