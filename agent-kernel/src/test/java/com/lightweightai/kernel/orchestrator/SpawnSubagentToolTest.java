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

@DisplayName("SpawnSubagentTool — tool contract and payload transmission")
class SpawnSubagentToolTest {

    private SubagentRuntime runtime;
    private List<StreamEvent> events;
    private SpawnSubagentTool tool;

    @BeforeEach
    void setUp() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("worker")
                .systemPrompt("I work")
                .maxSpawnDepth(2)
                .build());
        registry.register(AgentProfile.builder()
                .agentId("no-spawn")
                .systemPrompt("no spawn")
                .maxSpawnDepth(0)
                .build());

        TestMemory memory = new TestMemory();
        ToolRegistry tools = new ToolRegistry();
        LLMProvider provider = new FastMockProvider();
        AgentFactory factory = new AgentFactory(provider, memory, tools);
        runtime = new SubagentRuntime(factory, registry, 5);
        events = java.util.Collections.synchronizedList(new ArrayList<>());
        tool = new SpawnSubagentTool(runtime, "agent:orchestrator:main:s1", events::add);
    }

    @Test
    @DisplayName("getName returns 'spawn_subagent'")
    void nameIsCorrect() {
        assertEquals("spawn_subagent", tool.getName());
    }

    @Test
    @DisplayName("getSchema includes required 'task' parameter")
    void schemaHasTaskRequired() {
        ToolSchema schema = tool.getSchema();
        Map<String, Object> props = schema.getProperties();
        assertTrue(props.containsKey("task"));
        assertTrue(schema.toMap().toString().contains("task"));
    }

    @Test
    @DisplayName("execute with valid task returns success with runId")
    void executeReturnsRunId() {
        ToolResult result = tool.execute(Map.of("task", "translate document", "agentId", "worker"));

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("Run ID:"));
        assertTrue(result.getContent().contains("non-blocking"));
    }

    @Test
    @DisplayName("execute emits SUBAGENT_SPAWN event with correct runId and task")
    void executeEmitsSpawnEvent() throws InterruptedException {
        ToolResult result = tool.execute(Map.of("task", "summarize", "agentId", "worker"));

        String runId = extractRunId(result.getContent());
        assertTrue(runtime.waitForCompletion(runId, 5, TimeUnit.SECONDS));

        assertTrue(events.stream().anyMatch(e ->
                e.getType() == StreamEvent.EventType.SUBAGENT_SPAWN
                        && "worker".equals(e.getData().get("agentId"))
                        && "summarize".equals(e.getData().get("task"))));
    }

    @Test
    @DisplayName("execute with agentId routes to specific agent")
    void executeWithAgentIdRoutes() {
        ToolResult result = tool.execute(Map.of("task", "work", "agentId", "worker"));

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("Run ID:"));
    }

    @Test
    @DisplayName("execute with non-existent agentId throws IllegalArgumentException")
    void executeWithBadAgentIdThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                tool.execute(Map.of("task", "work", "agentId", "nonexistent")));
    }

    @Test
    @DisplayName("execute with maxSpawnDepth=0 agent returns error")
    void executeDepthLimitReturnsError() {
        SpawnSubagentTool deepTool = new SpawnSubagentTool(runtime,
                "agent:no-spawn:main:s1", events::add);

        ToolResult result = deepTool.execute(Map.of("task", "work", "agentId", "no-spawn"));

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("Failed to spawn"));
    }

    @Test
    @DisplayName("execute is non-blocking (returns in under 100ms)")
    void executeIsNonBlocking() {
        long start = System.currentTimeMillis();
        tool.execute(Map.of("task", "long running task", "agentId", "worker"));
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 100, "spawn should be non-blocking, took " + elapsed + "ms");
    }

    @Test
    @DisplayName("spawned subagent eventually emits SUBAGENT_COMPLETE event")
    void spawnedAgentCompletesWithEvent() throws InterruptedException {
        ToolResult result = tool.execute(Map.of("task", "compute", "agentId", "worker"));
        String runId = extractRunId(result.getContent());

        assertTrue(runtime.waitForCompletion(runId, 5, TimeUnit.SECONDS));

        assertTrue(events.stream().anyMatch(e ->
                e.getType() == StreamEvent.EventType.SUBAGENT_COMPLETE
                        && runId.equals(e.getData().get("runId"))));
    }

    private String extractRunId(String content) {
        int idx = content.indexOf("Run ID: ");
        if (idx < 0) return "";
        String after = content.substring(idx + 8);
        int end = after.indexOf(" ");
        return end > 0 ? after.substring(0, end) : after.trim();
    }

    // ==================== Helpers ====================

    private static class FastMockProvider implements LLMProvider {
        @Override
        public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent("done").build())
                    .stopReason("end_turn").build();
        }
        @Override public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            return Flux.just(StreamEvent.llmComplete(complete(m, o)));
        }
        @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
            return CompletableFuture.completedFuture(complete(m, o));
        }
        @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) {
            LLMResponse r = complete(m, o);
            h.onComplete(r);
            return CompletableFuture.completedFuture(r);
        }
        @Override public ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return "fast-mock"; }
    }

    private static class TestMemory implements MemoryProvider {
        @Override public void addMessage(String s, Message m) {}
        @Override public List<Message> getHistory(String s, int l) { return List.of(); }
        @Override public void clearSession(String s) {}
        @Override public void writeEphemeral(String c) {}
        @Override public void writeDurable(String s, String c) {}
        @Override public List<MemorySearchResult> search(String q) { return List.of(); }
    }
}
