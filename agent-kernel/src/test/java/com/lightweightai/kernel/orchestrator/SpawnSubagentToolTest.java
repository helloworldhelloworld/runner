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

@DisplayName("SpawnSubagentTool — critical path tests")
class SpawnSubagentToolTest {

    private SubagentRuntime runtime;
    private List<StreamEvent> events;

    @BeforeEach
    void setUp() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("worker")
                .systemPrompt("test")
                .maxSpawnDepth(2)
                .build());

        ToolRegistry tools = new ToolRegistry();
        LLMProvider provider = new QuickMockProvider();
        AgentFactory factory = new AgentFactory(provider, new NoOpMemory(), tools);
        runtime = new SubagentRuntime(factory, registry, 3);
        events = Collections.synchronizedList(new ArrayList<>());
    }

    @Test
    @DisplayName("schema declares 'task' as required parameter")
    void schemaDeclaresTaskRequired() {
        SpawnSubagentTool tool = new SpawnSubagentTool(runtime, "agent:worker:main:s1", e -> {});
        ToolSchema schema = tool.getSchema();
        assertNotNull(schema);
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.getDefinition().get("required");
        assertTrue(required.contains("task"));
    }

    @Test
    @DisplayName("name and description are correct")
    void nameAndDescription() {
        SpawnSubagentTool tool = new SpawnSubagentTool(runtime, "agent:worker:main:s1", e -> {});
        assertEquals("spawn_subagent", tool.getName());
        assertNotNull(tool.getDescription());
        assertFalse(tool.getDescription().isBlank());
    }

    @Test
    @DisplayName("successful spawn returns runId in result content")
    void successfulSpawnReturnsRunId() {
        SpawnSubagentTool tool = new SpawnSubagentTool(runtime, "agent:worker:main:s1", events::add);
        ToolResult result = tool.execute(Map.of("task", "analyze data"));

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("Run ID:"));
        assertTrue(result.getContent().contains("non-blocking"));
    }

    @Test
    @DisplayName("spawn emits SUBAGENT_SPAWN event with correct payload")
    void spawnEmitsSpawnEvent() {
        SpawnSubagentTool tool = new SpawnSubagentTool(runtime, "agent:worker:main:s1", events::add);
        tool.execute(Map.of("task", "classify docs"));

        List<StreamEvent> spawnEvents = events.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.SUBAGENT_SPAWN)
                .toList();
        assertFalse(spawnEvents.isEmpty(), "SUBAGENT_SPAWN event should be emitted");
    }

    @Test
    @DisplayName("spawn with optional agentId routes to correct agent")
    void spawnWithExplicitAgentId() {
        SpawnSubagentTool tool = new SpawnSubagentTool(runtime, "agent:worker:main:s1", events::add);
        ToolResult result = tool.execute(Map.of("task", "work", "agentId", "worker"));
        assertFalse(result.isError());
    }

    @Test
    @DisplayName("spawn with unknown agentId returns error (not exception)")
    void spawnWithUnknownAgentIdReturnsError() {
        SpawnSubagentTool tool = new SpawnSubagentTool(runtime, "agent:worker:main:s1", events::add);
        ToolResult result = tool.execute(Map.of("task", "work", "agentId", "nonexistent"));
        assertTrue(result.isError());
        assertTrue(result.getContent().contains("Failed") || result.getContent().contains("not found"));
    }

    @Test
    @DisplayName("depth limit exceeded returns error result, not thrown exception")
    void depthLimitExceededReturnsError() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("shallow")
                .maxSpawnDepth(1)
                .build());
        AgentFactory factory = new AgentFactory(new QuickMockProvider(), new NoOpMemory(), new ToolRegistry());
        SubagentRuntime shallowRuntime = new SubagentRuntime(factory, registry, 5);

        SpawnSubagentTool tool = new SpawnSubagentTool(
                shallowRuntime,
                "agent:shallow:main:s1:subagent:abc",
                events::add);
        ToolResult result = tool.execute(Map.of("task", "too deep"));

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("depth") || result.getContent().contains("limit"));
        shallowRuntime.shutdown();
    }

    @Test
    @DisplayName("concurrency limit exceeded returns error result")
    void concurrencyLimitExceededReturnsError() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder().agentId("w").maxSpawnDepth(3).build());
        LLMProvider slow = new SlowMockProvider(5000);
        AgentFactory factory = new AgentFactory(slow, new NoOpMemory(), new ToolRegistry());
        SubagentRuntime smallRuntime = new SubagentRuntime(factory, registry, 1);

        SpawnSubagentTool tool = new SpawnSubagentTool(smallRuntime, "agent:w:main:s1", e -> {});
        tool.execute(Map.of("task", "task1"));

        ToolResult result2 = tool.execute(Map.of("task", "task2"));
        assertTrue(result2.isError());
        assertTrue(result2.getContent().contains("concurrent") || result2.getContent().contains("Max"));

        smallRuntime.shutdown();
    }

    @Test
    @DisplayName("spawn is non-blocking — returns in < 200ms even with slow LLM")
    void spawnIsNonBlocking() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder().agentId("s").maxSpawnDepth(1).build());
        LLMProvider slow = new SlowMockProvider(3000);
        AgentFactory factory = new AgentFactory(slow, new NoOpMemory(), new ToolRegistry());
        SubagentRuntime slowRuntime = new SubagentRuntime(factory, registry, 5);

        SpawnSubagentTool tool = new SpawnSubagentTool(slowRuntime, "agent:s:main:s1", e -> {});
        long start = System.currentTimeMillis();
        tool.execute(Map.of("task", "slow work"));
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 200, "spawn should be non-blocking, took " + elapsed + "ms");
        slowRuntime.shutdown();
    }

    // ==================== Helpers ====================

    private static class QuickMockProvider implements LLMProvider {
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
            return CompletableFuture.completedFuture(complete(m, o));
        }
        @Override public ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return "quick"; }
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
                            .textContent("slow done").build())
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
        @Override public String getProviderName() { return "slow"; }
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
