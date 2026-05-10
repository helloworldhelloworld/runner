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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SpawnSubagentTool — unit tests for tool schema, execution payload, and error handling")
class SpawnSubagentToolTest {

    private SubagentRuntime runtime;
    private List<StreamEvent> capturedEvents;
    private SpawnSubagentTool tool;

    @BeforeEach
    void setUp() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("worker")
                .systemPrompt("I work")
                .maxSpawnDepth(2)
                .build());
        registry.setDefault("worker");
        registry.register(AgentProfile.builder()
                .agentId("no-spawn")
                .systemPrompt("Cannot spawn")
                .maxSpawnDepth(0)
                .build());

        LLMProvider provider = new ImmediateMockProvider();
        AgentFactory factory = new AgentFactory(provider, new StubMemory(), new ToolRegistry());
        runtime = new SubagentRuntime(factory, registry, 5);
        capturedEvents = Collections.synchronizedList(new ArrayList<>());
        tool = new SpawnSubagentTool(runtime, "agent:worker:main:s1", capturedEvents::add);
    }

    @Test
    @DisplayName("getName returns 'spawn_subagent'")
    void toolName() {
        assertEquals("spawn_subagent", tool.getName());
    }

    @Test
    @DisplayName("getSchema defines 'task' as required and 'agentId', 'model' as optional")
    void schemaDefinesRequiredTask() {
        Map<String, Object> schema = tool.getSchema().toMap();
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");

        assertTrue(props.containsKey("task"), "Schema must have 'task' property");
        assertTrue(props.containsKey("agentId"), "Schema must have 'agentId' property");
        assertTrue(props.containsKey("model"), "Schema must have 'model' property");

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("task"), "'task' must be required");
        assertFalse(required.contains("agentId"), "'agentId' should not be required");
    }

    @Test
    @DisplayName("execute with valid task returns success containing runId")
    void executeReturnsRunId() {
        ToolResult result = tool.execute(Map.of("task", "Analyze data"));

        assertFalse(result.isError(), "Should succeed");
        assertTrue(result.getContent().contains("Run ID:"),
                "Result should contain Run ID, got: " + result.getContent());
        assertTrue(result.getContent().contains("non-blocking"),
                "Result should mention non-blocking");
    }

    @Test
    @DisplayName("execute with agentId routes to specified agent")
    void executeWithSpecificAgent() {
        ToolResult result = tool.execute(Map.of("task", "Work task", "agentId", "worker"));

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("Run ID:"));
    }

    @Test
    @DisplayName("execute emits SUBAGENT_SPAWN event with correct runId and task")
    void executeEmitsSpawnEvent() {
        ToolResult result = tool.execute(Map.of("task", "My task"));

        String runId = extractRunId(result.getContent());
        assertTrue(capturedEvents.stream().anyMatch(e ->
                        e.getType() == StreamEvent.EventType.SUBAGENT_SPAWN
                                && runId.equals(e.getData().get("runId"))),
                "Should emit SUBAGENT_SPAWN event with correct runId");
    }

    @Test
    @DisplayName("execute with agent maxSpawnDepth=0 returns error")
    void executeDepthZeroReturnsError() {
        SpawnSubagentTool noSpawnTool = new SpawnSubagentTool(
                runtime, "agent:no-spawn:main:s1", capturedEvents::add);

        ToolResult result = noSpawnTool.execute(Map.of("task", "forbidden", "agentId", "no-spawn"));

        assertTrue(result.isError(), "Should fail for maxSpawnDepth=0 agent");
        assertTrue(result.getContent().contains("Failed to spawn"),
                "Error should mention spawn failure");
    }

    @Test
    @DisplayName("execute is non-blocking (returns before subagent completes)")
    void executeIsNonBlocking() {
        long start = System.currentTimeMillis();
        ToolResult result = tool.execute(Map.of("task", "quick task"));
        long elapsed = System.currentTimeMillis() - start;

        assertFalse(result.isError());
        assertTrue(elapsed < 500, "spawn should return immediately, took " + elapsed + "ms");
    }

    private String extractRunId(String content) {
        int idx = content.indexOf("Run ID: ");
        if (idx < 0) return "";
        String after = content.substring(idx + 8);
        int end = after.indexOf(' ');
        return end > 0 ? after.substring(0, end) : after;
    }

    // ==================== Helpers ====================

    private static class ImmediateMockProvider implements LLMProvider {
        @Override
        public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent("done").build())
                    .stopReason("end_turn").build();
        }
        @Override
        public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            return Flux.just(StreamEvent.llmComplete(complete(m, o)));
        }
        @Override
        public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
            return CompletableFuture.completedFuture(complete(m, o));
        }
        @Override
        public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) {
            return CompletableFuture.completedFuture(complete(m, o));
        }
        @Override public ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return "immediate-mock"; }
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
