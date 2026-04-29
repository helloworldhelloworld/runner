package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Subagent Tool Error Paths — 深度超限、并发超限、not-found")
class SubagentToolErrorPathTest {

    private AgentRegistry registry;
    private List<StreamEvent> events;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
        events = Collections.synchronizedList(new ArrayList<>());
    }

    @Nested
    @DisplayName("SpawnSubagentTool — 深度限制")
    class DepthLimitTests {

        @Test
        @DisplayName("maxSpawnDepth=0 的 agent 拒绝 spawn")
        void rejectSpawnWhenDepthZero() {
            registry.register(AgentProfile.builder()
                    .agentId("no-spawn")
                    .maxSpawnDepth(0)
                    .build());

            SubagentRuntime runtime = createRuntime(registry, 10);
            SpawnSubagentTool tool = new SpawnSubagentTool(
                    runtime, "agent:no-spawn:main:s1", events::add);

            ToolResult result = tool.execute(Map.of("task", "do something", "agentId", "no-spawn"));

            assertTrue(result.isError(), "Should return error when maxSpawnDepth=0");
            assertTrue(result.getContent().contains("Failed to spawn"),
                    "Error message should explain failure: " + result.getContent());
        }

        @Test
        @DisplayName("嵌套深度达到 maxSpawnDepth 时拒绝 spawn")
        void rejectSpawnWhenDepthExceeded() {
            registry.register(AgentProfile.builder()
                    .agentId("shallow")
                    .maxSpawnDepth(1)
                    .build());

            SubagentRuntime runtime = createRuntime(registry, 10);
            SpawnSubagentTool tool = new SpawnSubagentTool(
                    runtime, "agent:shallow:main:s1:subagent:x1", events::add);

            ToolResult result = tool.execute(Map.of("task", "go deeper", "agentId", "shallow"));

            assertTrue(result.isError());
            assertTrue(result.getContent().contains("Failed to spawn"));
        }

        @Test
        @DisplayName("深度未达限制时允许 spawn")
        void allowSpawnWhenUnderLimit() {
            registry.register(AgentProfile.builder()
                    .agentId("deep")
                    .maxSpawnDepth(3)
                    .build());

            SubagentRuntime runtime = createRuntime(registry, 10);
            SpawnSubagentTool tool = new SpawnSubagentTool(
                    runtime, "agent:deep:main:s1:subagent:x1", events::add);

            ToolResult result = tool.execute(Map.of("task", "go deeper", "agentId", "deep"));

            assertFalse(result.isError());
            assertTrue(result.getContent().contains("Run ID:"));

            runtime.stopAll();
        }
    }

    @Nested
    @DisplayName("SpawnSubagentTool — 并发限制")
    class ConcurrencyLimitTests {

        @Test
        @DisplayName("超过 maxConcurrent 时拒绝 spawn")
        void rejectSpawnWhenConcurrencyExceeded() {
            registry.register(AgentProfile.builder()
                    .agentId("w")
                    .maxSpawnDepth(2)
                    .build());

            SubagentRuntime runtime = createSlowRuntime(registry, 2, 10000);

            SpawnSubagentTool tool = new SpawnSubagentTool(
                    runtime, "agent:w:main:s1", events::add);
            ToolResult r1 = tool.execute(Map.of("task", "task1", "agentId", "w"));
            ToolResult r2 = tool.execute(Map.of("task", "task2", "agentId", "w"));
            assertFalse(r1.isError());
            assertFalse(r2.isError());

            ToolResult r3 = tool.execute(Map.of("task", "task3", "agentId", "w"));
            assertTrue(r3.isError(), "Should reject 3rd spawn when maxConcurrent=2");
            assertTrue(r3.getContent().contains("Failed to spawn"));

            runtime.stopAll();
        }
    }

    @Nested
    @DisplayName("WaitSubagentTool — not-found runId")
    class WaitNotFoundTests {

        @Test
        @DisplayName("等待不存在的 runId 返回 Not found 错误")
        void waitForNonexistentRunId() {
            registry.register(AgentProfile.builder().agentId("w").maxSpawnDepth(1).build());
            SubagentRuntime runtime = createRuntime(registry, 5);

            WaitSubagentTool waitTool = new WaitSubagentTool(runtime);
            ToolResult result = waitTool.execute(Map.of(
                    "runIds", "non-existent-id",
                    "timeoutSeconds", 1));

            assertTrue(result.isError());
            assertTrue(result.getContent().contains("Not found"));
        }

        @Test
        @DisplayName("混合存在和不存在的 runId — 部分成功")
        void waitForMixedRunIds() throws InterruptedException {
            registry.register(AgentProfile.builder().agentId("w").maxSpawnDepth(1).build());
            SubagentRuntime runtime = createRuntime(registry, 5);

            String realRunId = runtime.spawn(SpawnRequest.builder()
                    .parentSessionKey("agent:w:main:s1")
                    .task("real task")
                    .agentId("w")
                    .build(), events::add);

            runtime.waitForCompletion(realRunId, 5, java.util.concurrent.TimeUnit.SECONDS);

            WaitSubagentTool waitTool = new WaitSubagentTool(runtime);
            ToolResult result = waitTool.execute(Map.of(
                    "runIds", realRunId + ",ghost-id",
                    "timeoutSeconds", 5));

            assertTrue(result.isError());
            assertTrue(result.getContent().contains(realRunId));
            assertTrue(result.getContent().contains("Not found"));
        }
    }

    @Nested
    @DisplayName("ListSubagentsTool — 空列表")
    class ListEmptyTests {

        @Test
        @DisplayName("无活跃 subagent 时返回 'No active' 消息")
        void listWhenEmpty() {
            registry.register(AgentProfile.builder().agentId("w").maxSpawnDepth(1).build());
            SubagentRuntime runtime = createRuntime(registry, 5);

            ListSubagentsTool listTool = new ListSubagentsTool(runtime);
            ToolResult result = listTool.execute(Map.of());

            assertFalse(result.isError());
            assertTrue(result.getContent().contains("No active"));
        }
    }

    @Nested
    @DisplayName("SpawnSubagentTool — SUBAGENT_SPAWN 事件发射")
    class SpawnEventTests {

        @Test
        @DisplayName("spawn 成功后立即发射 SUBAGENT_SPAWN 事件，包含 runId 和 task")
        void spawnEmitsEvent() {
            registry.register(AgentProfile.builder()
                    .agentId("ev")
                    .maxSpawnDepth(1)
                    .build());
            SubagentRuntime runtime = createSlowRuntime(registry, 5, 5000);

            SpawnSubagentTool tool = new SpawnSubagentTool(
                    runtime, "agent:ev:main:s1", events::add);
            tool.execute(Map.of("task", "event test", "agentId", "ev"));

            assertTrue(events.stream().anyMatch(
                    e -> e.getType() == StreamEvent.EventType.SUBAGENT_SPAWN),
                    "SUBAGENT_SPAWN event should be emitted");

            StreamEvent spawnEvent = events.stream()
                    .filter(e -> e.getType() == StreamEvent.EventType.SUBAGENT_SPAWN)
                    .findFirst().get();
            assertEquals("event test", spawnEvent.getData().get("task"));
            assertEquals("ev", spawnEvent.getData().get("agentId"));

            runtime.stopAll();
        }
    }

    // ==================== Helpers ====================

    private SubagentRuntime createRuntime(AgentRegistry reg, int maxConcurrent) {
        LLMProvider fast = new FastMockProvider();
        AgentFactory factory = new AgentFactory(fast, new NoOpMemory(), new ToolRegistry());
        return new SubagentRuntime(factory, reg, maxConcurrent);
    }

    private SubagentRuntime createSlowRuntime(AgentRegistry reg, int maxConcurrent, long delayMs) {
        LLMProvider slow = new SlowMockProvider(delayMs);
        AgentFactory factory = new AgentFactory(slow, new NoOpMemory(), new ToolRegistry());
        return new SubagentRuntime(factory, reg, maxConcurrent);
    }

    private static class FastMockProvider implements LLMProvider {
        @Override
        public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent("fast reply").build())
                    .stopReason("end_turn").build();
        }
        @Override public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            return Flux.just(StreamEvent.llmComplete(complete(m, o)));
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
        public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
            return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent("slow reply").build())
                    .stopReason("end_turn").build();
        }
        @Override public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            return Flux.just(StreamEvent.llmComplete(complete(m, o)));
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

    private static class NoOpMemory implements MemoryProvider {
        @Override public void addMessage(String s, Message m) {}
        @Override public List<Message> getHistory(String s, int l) { return List.of(); }
        @Override public void clearSession(String s) {}
        @Override public void writeEphemeral(String c) {}
        @Override public void writeDurable(String s, String c) {}
        @Override public List<MemorySearchResult> search(String q) { return List.of(); }
    }
}
