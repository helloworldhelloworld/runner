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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ListSubagentsTool — list active subagents")
class ListSubagentsToolTest {

    private SubagentRuntime runtime;
    private ListSubagentsTool tool;

    @BeforeEach
    void setUp() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("worker").systemPrompt("I am worker").maxSpawnDepth(1).build());
        registry.setDefault("worker");

        AgentFactory factory = new AgentFactory(new SlowMockProvider(5000), new StubMemory(), new ToolRegistry());
        runtime = new SubagentRuntime(factory, registry, 5);
        tool = new ListSubagentsTool(runtime);
    }

    @AfterEach
    void tearDown() { runtime.shutdown(); }

    @Test
    @DisplayName("tool name and schema are correct")
    void toolMetadata() {
        assertEquals("list_subagents", tool.getName());
        assertNotNull(tool.getDescription());
        assertNotNull(tool.getSchema());
    }

    @Test
    @DisplayName("returns 'No active sub-agents' when none running")
    void emptyList() {
        ToolResult result = tool.execute(Map.of());
        assertFalse(result.isError());
        assertEquals("No active sub-agents.", result.getContent());
    }

    @Test
    @DisplayName("lists active subagents with status, task, and duration")
    void listsActiveAgents() throws InterruptedException {
        runtime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1").task("task A").agentId("worker").build(), e -> {});
        runtime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1").task("task B").agentId("worker").build(), e -> {});

        Thread.sleep(100);

        ToolResult result = tool.execute(Map.of());
        assertFalse(result.isError());

        String content = result.getContent();
        assertTrue(content.contains("Active sub-agents (2)"), "Should show count: " + content);
        assertTrue(content.contains("task A"), "Should contain task A: " + content);
        assertTrue(content.contains("task B"), "Should contain task B: " + content);
        assertTrue(content.contains("RUNNING"), "Should show RUNNING status: " + content);
        assertTrue(content.contains("duration:"), "Should show duration: " + content);
    }

    private static class SlowMockProvider implements LLMProvider {
        private final long delayMs;
        SlowMockProvider(long delayMs) { this.delayMs = delayMs; }
        @Override public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            return Flux.just(StreamEvent.textDelta("...")).delayElements(java.time.Duration.ofMillis(delayMs));
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

    private static class StubMemory implements MemoryProvider {
        @Override public void addMessage(String sid, Message m) {}
        @Override public List<Message> getHistory(String sid, int limit) { return List.of(); }
        @Override public void clearSession(String sid) {}
        @Override public void writeEphemeral(String content) {}
        @Override public void writeDurable(String section, String content) {}
        @Override public List<MemorySearchResult> search(String query) { return List.of(); }
    }
}
