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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ListSubagentsTool — critical path tests")
class ListSubagentsToolTest {

    private SubagentRuntime runtime;

    @BeforeEach
    void setUp() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("w")
                .systemPrompt("test")
                .maxSpawnDepth(1)
                .build());

        LLMProvider slow = new SlowProvider(5000);
        AgentFactory factory = new AgentFactory(slow, new NoOpMemory(), new ToolRegistry());
        runtime = new SubagentRuntime(factory, registry, 10);
    }

    @Test
    @DisplayName("name is 'list_subagents' and schema is empty")
    void nameAndSchema() {
        ListSubagentsTool tool = new ListSubagentsTool(runtime);
        assertEquals("list_subagents", tool.getName());
        assertNotNull(tool.getSchema());
    }

    @Test
    @DisplayName("no active subagents returns 'No active sub-agents.'")
    void noActiveSubagents() {
        ListSubagentsTool tool = new ListSubagentsTool(runtime);
        ToolResult result = tool.execute(Map.of());

        assertFalse(result.isError());
        assertEquals("No active sub-agents.", result.getContent());
    }

    @Test
    @DisplayName("lists active subagents with runId, status, task, and duration")
    void listsActiveSubagents() {
        runtime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:w:main:s1")
                .task("research AI papers")
                .agentId("w")
                .build(), e -> {});
        runtime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:w:main:s1")
                .task("summarize docs")
                .agentId("w")
                .build(), e -> {});

        ListSubagentsTool tool = new ListSubagentsTool(runtime);
        ToolResult result = tool.execute(Map.of());

        assertFalse(result.isError());
        String content = result.getContent();
        assertTrue(content.contains("Active sub-agents (2)"));
        assertTrue(content.contains("research AI papers"));
        assertTrue(content.contains("summarize docs"));
        assertTrue(content.contains("RUNNING"));
        assertTrue(content.contains("ms"));

        runtime.stopAll();
    }

    @Test
    @DisplayName("list ignores args parameter (no schema)")
    void ignoresArgs() {
        ListSubagentsTool tool = new ListSubagentsTool(runtime);
        ToolResult result1 = tool.execute(Map.of());
        ToolResult result2 = tool.execute(Map.of("foo", "bar"));
        assertEquals(result1.getContent(), result2.getContent());
    }

    // ==================== Helpers ====================

    private static class SlowProvider implements LLMProvider {
        private final long delayMs;
        SlowProvider(long delayMs) { this.delayMs = delayMs; }
        @Override
        public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
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
