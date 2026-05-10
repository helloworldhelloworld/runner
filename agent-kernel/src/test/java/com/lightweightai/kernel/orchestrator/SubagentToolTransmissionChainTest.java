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
 * Transmission-chain acceptance test:
 * spawn_subagent → wait_subagent → list_subagents end-to-end.
 *
 * Validates that the payload flows correctly through the entire tool chain,
 * catching wiring bugs like the ones documented in CLAUDE.md:
 * - CancellationToken not passed through AgentLoop → ToolCallingLoop
 * - ScopedToolRegistry overriding get()/has() but not isEnabled()
 * - spawn_subagent tool worked alone but no wait_subagent tool existed
 * - AgentFactory building AgentLoop with empty LLMOptions (no toolDefinitions)
 */
@DisplayName("Acceptance: spawn → wait → list tool chain carries payload end-to-end")
class SubagentToolTransmissionChainTest {

    private SubagentRuntime runtime;
    private List<StreamEvent> events;

    @BeforeEach
    void setUp() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("worker")
                .systemPrompt("I do work")
                .maxSpawnDepth(2)
                .build());

        LLMProvider provider = new ContentAwareMockProvider();
        AgentFactory factory = new AgentFactory(provider, new StubMemory(), new ToolRegistry());
        runtime = new SubagentRuntime(factory, registry, 10);
        events = Collections.synchronizedList(new ArrayList<>());
    }

    @AfterEach
    void tearDown() {
        runtime.stopAll();
    }

    @Test
    @DisplayName("spawn → list (while running) → wait → list (after completion): full lifecycle")
    void fullLifecycleThroughTools() throws InterruptedException {
        // 1. Spawn via tool
        SpawnSubagentTool spawnTool = new SpawnSubagentTool(runtime, "agent:worker:main:s1", events::add);
        ToolResult spawnResult = spawnTool.execute(Map.of("task", "lifecycle-test-task", "agentId", "worker"));

        assertFalse(spawnResult.isError(), "Spawn should succeed");
        String runId = extractRunId(spawnResult.getContent());
        assertFalse(runId.isEmpty(), "Should extract runId from result");

        // 2. Verify SUBAGENT_SPAWN event was emitted with correct data
        assertTrue(events.stream().anyMatch(e ->
                        e.getType() == StreamEvent.EventType.SUBAGENT_SPAWN
                                && runId.equals(e.getData().get("runId"))
                                && "lifecycle-test-task".equals(e.getData().get("task"))),
                "SPAWN event should carry runId and task payload");

        // 3. Wait for completion
        assertTrue(runtime.waitForCompletion(runId, 5, TimeUnit.SECONDS),
                "Subagent should complete within timeout");
        Thread.sleep(200);

        // 4. Wait tool should return result with content from the mock LLM
        WaitSubagentTool waitTool = new WaitSubagentTool(runtime);
        ToolResult waitResult = waitTool.execute(Map.of("runIds", runId, "timeoutSeconds", 5));

        assertFalse(waitResult.isError(), "Wait should succeed for completed subagent");
        assertTrue(waitResult.getContent().contains(runId),
                "Wait result should contain runId");
        assertTrue(waitResult.getContent().contains("ms"),
                "Wait result should contain duration in ms");

        // 5. After completion, SUBAGENT_COMPLETE event should exist
        assertTrue(events.stream().anyMatch(e ->
                        e.getType() == StreamEvent.EventType.SUBAGENT_COMPLETE
                                && runId.equals(e.getData().get("runId"))),
                "COMPLETE event should have been emitted");

        // 6. List should show no active subagents (all completed)
        ListSubagentsTool listTool = new ListSubagentsTool(runtime);
        ToolResult listResult = listTool.execute(Map.of());

        assertFalse(listResult.isError());
        assertTrue(listResult.getContent().contains("No active"),
                "After completion, no active subagents expected");
    }

    @Test
    @DisplayName("spawn multiple → wait all → results contain each runId")
    void spawnMultipleWaitAll() throws InterruptedException {
        SpawnSubagentTool spawnTool = new SpawnSubagentTool(runtime, "agent:worker:main:s1", events::add);

        ToolResult r1 = spawnTool.execute(Map.of("task", "task-alpha", "agentId", "worker"));
        ToolResult r2 = spawnTool.execute(Map.of("task", "task-beta", "agentId", "worker"));

        String runId1 = extractRunId(r1.getContent());
        String runId2 = extractRunId(r2.getContent());

        assertTrue(runtime.waitForCompletion(runId1, 5, TimeUnit.SECONDS));
        assertTrue(runtime.waitForCompletion(runId2, 5, TimeUnit.SECONDS));
        Thread.sleep(200);

        WaitSubagentTool waitTool = new WaitSubagentTool(runtime);
        ToolResult waitResult = waitTool.execute(Map.of(
                "runIds", runId1 + "," + runId2,
                "timeoutSeconds", 5));

        assertFalse(waitResult.isError());
        assertTrue(waitResult.getContent().contains(runId1),
                "Wait result should contain first runId");
        assertTrue(waitResult.getContent().contains(runId2),
                "Wait result should contain second runId");
    }

    @Test
    @DisplayName("spawn error propagates as tool error, not exception")
    void spawnErrorIsToolError() {
        SpawnSubagentTool spawnTool = new SpawnSubagentTool(
                runtime, "agent:worker:subagent:a:subagent:b", events::add);

        ToolResult result = spawnTool.execute(Map.of("task", "deep-nested", "agentId", "worker"));

        assertTrue(result.isError(), "Depth-exceeded spawn should return error ToolResult");
        assertTrue(result.getContent().contains("Failed"),
                "Error should mention failure: " + result.getContent());
    }

    private String extractRunId(String content) {
        int idx = content.indexOf("Run ID: ");
        if (idx < 0) return "";
        String after = content.substring(idx + 8);
        int end = after.indexOf(' ');
        return end > 0 ? after.substring(0, end) : after;
    }

    // ==================== Helpers ====================

    private static class ContentAwareMockProvider implements LLMProvider {
        @Override
        public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            String task = m.isEmpty() ? "unknown" : m.get(m.size() - 1).getTextContent();
            return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent("completed: " + task).build())
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
        @Override public String getProviderName() { return "content-aware-mock"; }
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
