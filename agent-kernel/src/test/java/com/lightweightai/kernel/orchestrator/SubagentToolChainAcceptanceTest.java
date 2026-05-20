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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Acceptance test: spawn → wait → list → collect results.
 *
 * Tests the full subagent tool chain end-to-end using real
 * SpawnSubagentTool, WaitSubagentTool, and ListSubagentsTool
 * backed by a SubagentRuntime with a controllable mock LLM provider.
 *
 * This test targets the CLAUDE.md-documented bug class:
 *   "spawn_subagent tool worked alone, but no wait_subagent tool existed
 *    → LLM could spawn but never collect results"
 */
@DisplayName("Subagent Tool Chain — spawn → wait → list acceptance test")
class SubagentToolChainAcceptanceTest {

    private SubagentRuntime runtime;
    private List<StreamEvent> capturedEvents;
    private SpawnSubagentTool spawnTool;
    private WaitSubagentTool waitTool;
    private ListSubagentsTool listTool;

    @BeforeEach
    void setUp() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("researcher")
                .systemPrompt("You are a researcher")
                .maxSpawnDepth(2)
                .build());

        LLMProvider provider = new FastMockProvider("research complete");
        AgentFactory factory = new AgentFactory(provider, new StubMemory(), new ToolRegistry());
        runtime = new SubagentRuntime(factory, registry, 5);
        capturedEvents = Collections.synchronizedList(new ArrayList<>());

        spawnTool = new SpawnSubagentTool(runtime, "agent:researcher:main:s1", capturedEvents::add);
        waitTool = new WaitSubagentTool(runtime);
        listTool = new ListSubagentsTool(runtime);
    }

    @AfterEach
    void tearDown() {
        runtime.stopAll();
    }

    @Test
    @DisplayName("spawn returns runId, wait collects result, list shows status")
    void fullSpawnWaitListCycle() throws InterruptedException {
        // Step 1: Spawn
        ToolResult spawnResult = spawnTool.execute(Map.of(
                "task", "Research quantum computing",
                "agentId", "researcher"));
        assertFalse(spawnResult.isError(), "spawn should succeed");
        assertTrue(spawnResult.getContent().contains("Run ID:"),
                "spawn result must contain runId");

        String runId = extractRunId(spawnResult.getContent());
        assertNotNull(runId, "runId must be extractable");
        assertFalse(runId.isBlank());

        // Step 2: List — should show active subagent
        ToolResult listResult = listTool.execute(Map.of());
        assertFalse(listResult.isError());
        // May already be completed given fast mock, but structure should be valid

        // Step 3: Wait for completion
        ToolResult waitResult = waitTool.execute(Map.of(
                "runIds", runId,
                "timeoutSeconds", 10));
        assertFalse(waitResult.isError(),
                "wait should succeed for completed subagent");
        assertTrue(waitResult.getContent().contains(runId),
                "wait result must reference the runId");
        assertTrue(waitResult.getContent().contains("research complete"),
                "wait result must carry the subagent's output payload");

        // Step 4: Verify events were emitted
        assertTrue(capturedEvents.stream()
                        .anyMatch(e -> e.getType() == StreamEvent.EventType.SUBAGENT_SPAWN),
                "SUBAGENT_SPAWN event must have been emitted");
        assertTrue(capturedEvents.stream()
                        .anyMatch(e -> e.getType() == StreamEvent.EventType.SUBAGENT_COMPLETE),
                "SUBAGENT_COMPLETE event must have been emitted");
    }

    @Test
    @DisplayName("spawn multiple → wait all → results contain both")
    void spawnMultipleAndWaitAll() {
        ToolResult spawn1 = spawnTool.execute(Map.of(
                "task", "Task A", "agentId", "researcher"));
        ToolResult spawn2 = spawnTool.execute(Map.of(
                "task", "Task B", "agentId", "researcher"));

        String runId1 = extractRunId(spawn1.getContent());
        String runId2 = extractRunId(spawn2.getContent());

        ToolResult waitResult = waitTool.execute(Map.of(
                "runIds", runId1 + "," + runId2,
                "timeoutSeconds", 10));

        assertFalse(waitResult.isError());
        assertTrue(waitResult.getContent().contains(runId1));
        assertTrue(waitResult.getContent().contains(runId2));
    }

    @Test
    @DisplayName("wait for non-existent runId returns error")
    void waitNonExistentRunId() {
        ToolResult result = waitTool.execute(Map.of(
                "runIds", "nonexistent-id",
                "timeoutSeconds", 1));

        assertTrue(result.isError() || result.getContent().contains("Not found"),
                "waiting for unknown runId should indicate failure");
    }

    @Test
    @DisplayName("list on empty runtime returns 'no active' message")
    void listEmptyRuntime() throws InterruptedException {
        // Wait briefly for any fast-completing tasks
        Thread.sleep(100);

        SubagentRuntime emptyRuntime = new SubagentRuntime(
                new AgentFactory(new FastMockProvider("x"), new StubMemory(), new ToolRegistry()),
                new AgentRegistry(), 5);
        ListSubagentsTool emptyList = new ListSubagentsTool(emptyRuntime);

        ToolResult result = emptyList.execute(Map.of());
        assertFalse(result.isError());
        assertTrue(result.getContent().contains("No active"),
                "empty runtime should report no active subagents");
    }

    @Test
    @DisplayName("spawn respects depth limit")
    void spawnRespectsDepthLimit() {
        AgentRegistry limitedRegistry = new AgentRegistry();
        limitedRegistry.register(AgentProfile.builder()
                .agentId("shallow")
                .maxSpawnDepth(0)
                .build());
        SubagentRuntime limitedRuntime = new SubagentRuntime(
                new AgentFactory(new FastMockProvider("x"), new StubMemory(), new ToolRegistry()),
                limitedRegistry, 5);

        SpawnSubagentTool limitedSpawn = new SpawnSubagentTool(
                limitedRuntime, "agent:shallow:main:s1", e -> {});

        ToolResult result = limitedSpawn.execute(Map.of(
                "task", "should fail", "agentId", "shallow"));
        assertTrue(result.isError(),
                "spawn beyond depth limit should fail");
        assertTrue(result.getContent().contains("cannot spawn") ||
                        result.getContent().contains("Failed"),
                "error message should explain the failure");
    }

    @Test
    @DisplayName("spawn without agentId uses default agent")
    void spawnWithoutAgentIdUsesDefault() {
        ToolResult result = spawnTool.execute(Map.of("task", "auto-route task"));
        assertFalse(result.isError(),
                "spawn without agentId should use default agent");
    }

    private String extractRunId(String spawnOutput) {
        int idx = spawnOutput.indexOf("Run ID: ");
        if (idx < 0) return null;
        String afterPrefix = spawnOutput.substring(idx + "Run ID: ".length());
        int end = afterPrefix.indexOf(' ');
        return end > 0 ? afterPrefix.substring(0, end) : afterPrefix.trim();
    }

    // ==================== Test helpers ====================

    private static class FastMockProvider implements LLMProvider {
        private final String response;
        FastMockProvider(String response) { this.response = response; }

        @Override
        public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent(response).build())
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

    private static class StubMemory implements MemoryProvider {
        @Override public void addMessage(String s, Message m) {}
        @Override public List<Message> getHistory(String s, int l) { return List.of(); }
        @Override public void clearSession(String s) {}
        @Override public void writeEphemeral(String c) {}
        @Override public void writeDurable(String s, String c) {}
        @Override public List<MemorySearchResult> search(String q) { return List.of(); }
    }
}
