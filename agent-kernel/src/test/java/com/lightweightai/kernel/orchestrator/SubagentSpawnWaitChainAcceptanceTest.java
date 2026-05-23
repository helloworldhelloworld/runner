package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Acceptance test: spawn_subagent → wait_subagent → verify payloads
 *
 * This test verifies the full transmission chain:
 * SpawnSubagentTool → SubagentRuntime → AgentFactory → AgentLoop → LLMProvider
 * → SubagentRun.complete → WaitSubagentTool → aggregated result text
 *
 * Following CLAUDE.md TDD rule: "Assert the payload, not just the ceremony."
 */
@DisplayName("Spawn→Wait 链路验收测试 — 验证 payload 完整传输")
class SubagentSpawnWaitChainAcceptanceTest {

    private SubagentRuntime runtime;
    private List<StreamEvent> capturedEvents;

    @BeforeEach
    void setUp() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("analyzer")
                .systemPrompt("You analyze data")
                .maxSpawnDepth(2)
                .build());

        PayloadCapturingProvider llmProvider = new PayloadCapturingProvider("分析结果：数据正常");
        StubMemory memory = new StubMemory();
        ToolRegistry tools = new ToolRegistry();
        AgentFactory factory = new AgentFactory(llmProvider, memory, tools);
        runtime = new SubagentRuntime(factory, registry, 5);
        capturedEvents = new CopyOnWriteArrayList<>();
    }

    @AfterEach
    void tearDown() {
        runtime.shutdown();
    }

    @Test
    @DisplayName("spawn 产生 SUBAGENT_SPAWN 事件，包含正确的 runId、agentId、task")
    void spawnEventContainsCorrectPayload() {
        SpawnSubagentTool spawnTool = new SpawnSubagentTool(
                runtime, "agent:analyzer:main:s1", capturedEvents::add);

        ToolResult result = spawnTool.execute(Map.of("task", "分析用户情绪"));

        assertFalse(result.isError(), "spawn should succeed");
        String runId = extractRunId(result.getContent());
        assertNotNull(runId, "result should contain run ID");

        StreamEvent spawnEvent = capturedEvents.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.SUBAGENT_SPAWN)
                .findFirst()
                .orElseThrow(() -> new AssertionError("SUBAGENT_SPAWN event not emitted"));

        assertEquals(runId, spawnEvent.getData().get("runId"),
                "Spawn event runId should match returned runId");
        assertEquals("analyzer", spawnEvent.getData().get("agentId"),
                "Spawn event should carry target agentId");
        assertEquals("分析用户情绪", spawnEvent.getData().get("task"),
                "Spawn event should carry the task description");
    }

    @Test
    @DisplayName("spawn→wait 完整链路：wait 返回子 agent 的实际响应文本和耗时")
    void fullSpawnWaitChainCarriesPayload() throws InterruptedException {
        SpawnSubagentTool spawnTool = new SpawnSubagentTool(
                runtime, "agent:analyzer:main:s1", capturedEvents::add);
        WaitSubagentTool waitTool = new WaitSubagentTool(runtime);

        ToolResult spawnResult = spawnTool.execute(Map.of(
                "task", "分析数据",
                "agentId", "analyzer"));
        String runId = extractRunId(spawnResult.getContent());

        ToolResult waitResult = waitTool.execute(Map.of(
                "runIds", runId,
                "timeoutSeconds", 10));

        assertFalse(waitResult.isError(), "wait should succeed: " + waitResult.getContent());
        assertTrue(waitResult.getContent().contains(runId),
                "Wait result should reference the run ID");
        assertTrue(waitResult.getContent().contains("分析结果：数据正常"),
                "Wait result should contain the LLM's actual response text");
        assertTrue(waitResult.getContent().contains("ms"),
                "Wait result should include duration");
    }

    @Test
    @DisplayName("spawn→wait 多个子 agent：wait 聚合所有结果")
    void multipleSpawnWaitAggregatesResults() throws InterruptedException {
        SpawnSubagentTool spawnTool = new SpawnSubagentTool(
                runtime, "agent:analyzer:main:s1", capturedEvents::add);
        WaitSubagentTool waitTool = new WaitSubagentTool(runtime);

        ToolResult r1 = spawnTool.execute(Map.of("task", "任务A", "agentId", "analyzer"));
        ToolResult r2 = spawnTool.execute(Map.of("task", "任务B", "agentId", "analyzer"));
        String id1 = extractRunId(r1.getContent());
        String id2 = extractRunId(r2.getContent());

        ToolResult waitResult = waitTool.execute(Map.of(
                "runIds", id1 + "," + id2,
                "timeoutSeconds", 10));

        assertFalse(waitResult.isError());
        assertTrue(waitResult.getContent().contains(id1));
        assertTrue(waitResult.getContent().contains(id2));

        List<StreamEvent> spawnEvents = capturedEvents.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.SUBAGENT_SPAWN)
                .toList();
        assertEquals(2, spawnEvents.size(), "Should emit 2 SUBAGENT_SPAWN events");
    }

    @Test
    @DisplayName("spawn 失败时返回 error ToolResult，不抛异常")
    void spawnFailureReturnsErrorResult() {
        AgentRegistry emptyRegistry = new AgentRegistry();
        emptyRegistry.register(AgentProfile.builder()
                .agentId("blocked")
                .maxSpawnDepth(0)
                .build());
        SubagentRuntime blockedRuntime = new SubagentRuntime(
                new AgentFactory(new PayloadCapturingProvider("x"), new StubMemory(), new ToolRegistry()),
                emptyRegistry, 5);

        SpawnSubagentTool spawnTool = new SpawnSubagentTool(
                blockedRuntime, "agent:blocked:main:s1", capturedEvents::add);

        ToolResult result = spawnTool.execute(Map.of("task", "should fail", "agentId", "blocked"));

        assertTrue(result.isError(), "Spawn should fail for maxSpawnDepth=0");
        assertTrue(result.getContent().contains("Failed to spawn"),
                "Error message should explain failure");

        blockedRuntime.shutdown();
    }

    @Test
    @DisplayName("list_subagents 返回正确的 runId、status、task")
    void listReturnsActiveSubagentPayload() {
        AgentRegistry reg = new AgentRegistry();
        reg.register(AgentProfile.builder().agentId("slow").maxSpawnDepth(1).build());
        SubagentRuntime slowRuntime = new SubagentRuntime(
                new AgentFactory(new PayloadCapturingProvider("x", 3000), new StubMemory(), new ToolRegistry()),
                reg, 5);

        String runId = slowRuntime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:slow:main:s1")
                .task("long-running-analysis")
                .agentId("slow")
                .build(), e -> {});

        ListSubagentsTool listTool = new ListSubagentsTool(slowRuntime);
        ToolResult result = listTool.execute(Map.of());

        assertFalse(result.isError());
        assertTrue(result.getContent().contains(runId),
                "List should contain the active run ID");
        assertTrue(result.getContent().contains("long-running-analysis"),
                "List should contain the task description");
        assertTrue(result.getContent().contains("RUNNING"),
                "List should show RUNNING status");

        slowRuntime.shutdown();
    }

    // ==================== Helpers ====================

    private String extractRunId(String content) {
        int idx = content.indexOf("Run ID: ");
        if (idx < 0) return null;
        String afterId = content.substring(idx + 8);
        int end = afterId.indexOf(' ');
        return end > 0 ? afterId.substring(0, end) : afterId.trim();
    }

    private static class PayloadCapturingProvider implements LLMProvider {
        private final String responseText;
        private final long delayMs;

        PayloadCapturingProvider(String responseText) { this(responseText, 50); }
        PayloadCapturingProvider(String responseText, long delayMs) {
            this.responseText = responseText;
            this.delayMs = delayMs;
        }

        @Override
        public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent(responseText).build())
                    .stopReason("end_turn").build();
        }

        @Override
        public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            return Flux.just(StreamEvent.llmComplete(complete(m, o)));
        }

        @Override
        public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
            return CompletableFuture.supplyAsync(() -> complete(m, o));
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) {
            return CompletableFuture.completedFuture(complete(m, o));
        }

        @Override
        public ModelCapability getModelCapability() { return null; }

        @Override
        public String getProviderName() { return "test-capturing"; }
    }

    private static class StubMemory implements MemoryProvider {
        @Override public void addMessage(String s, Message m) {}
        @Override public List<Message> getHistory(String s, int l) { return List.of(); }
        @Override public void clearSession(String s) {}
        @Override public void writeEphemeral(String c) {}
        @Override public void writeDurable(String s, String c) {}
        @Override public List<MemorySearchResult> search(String q) { return List.of(); }
    }
}
