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

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ListSubagentsTool - LLM-callable list active subagents")
class ListSubagentsToolTest {

    private SubagentRuntime runtime;
    private ListSubagentsTool tool;

    @BeforeEach
    void setUp() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("worker")
                .systemPrompt("I am worker")
                .maxSpawnDepth(2)
                .build());
        registry.setDefault("worker");

        ToolRegistry tools = new ToolRegistry();
        AgentFactory factory = new AgentFactory(new SlowMockProvider(3000), new InertMemory(), tools);
        runtime = new SubagentRuntime(factory, registry, 5);
        tool = new ListSubagentsTool(runtime);
    }

    @Test
    @DisplayName("getName returns list_subagents")
    void testName() {
        assertEquals("list_subagents", tool.getName());
    }

    @Test
    @DisplayName("getSchema returns empty schema (no params needed)")
    void testSchema() {
        ToolSchema schema = tool.getSchema();
        assertNotNull(schema);
    }

    @Test
    @DisplayName("no active subagents returns 'No active sub-agents'")
    void testNoActive() {
        ToolResult result = tool.execute(Map.of());

        assertFalse(result.isError());
        assertEquals("No active sub-agents.", result.getContent());
    }

    @Test
    @DisplayName("active subagents listed with runId, status, task, duration")
    void testActiveSubagentsListed() throws InterruptedException {
        SpawnRequest req = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("analyze logs")
                .agentId("worker")
                .build();
        runtime.spawn(req, e -> {});

        Thread.sleep(100);

        ToolResult result = tool.execute(Map.of());

        assertFalse(result.isError());
        String content = result.getContent();
        assertTrue(content.contains("Active sub-agents (1)"));
        assertTrue(content.contains("RUNNING"));
        assertTrue(content.contains("analyze logs"));
        assertTrue(content.contains("ms"));

        runtime.stopAll();
    }

    @Test
    @DisplayName("multiple active subagents all listed")
    void testMultipleActive() throws InterruptedException {
        runtime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1").task("task-a").agentId("worker").build(), e -> {});
        runtime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1").task("task-b").agentId("worker").build(), e -> {});

        Thread.sleep(100);

        ToolResult result = tool.execute(Map.of());

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("Active sub-agents (2)"));
        assertTrue(result.getContent().contains("task-a"));
        assertTrue(result.getContent().contains("task-b"));

        runtime.stopAll();
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

    private static class InertMemory implements MemoryProvider {
        @Override public void addMessage(String sid, Message msg) {}
        @Override public List<Message> getHistory(String sid, int limit) { return List.of(); }
        @Override public void clearSession(String sid) {}
        @Override public void writeEphemeral(String content) {}
        @Override public void writeDurable(String section, String content) {}
        @Override public List<MemorySearchResult> search(String query) { return List.of(); }
    }
}
