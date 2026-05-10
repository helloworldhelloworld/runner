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

@DisplayName("ListSubagentsTool — list active subagents with status and task info")
class ListSubagentsToolTest {

    private SubagentRuntime slowRuntime;

    @BeforeEach
    void setUp() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("worker")
                .systemPrompt("I work")
                .maxSpawnDepth(1)
                .build());

        LLMProvider slowProvider = new SlowMockProvider(5000);
        AgentFactory factory = new AgentFactory(slowProvider, new StubMemory(), new ToolRegistry());
        slowRuntime = new SubagentRuntime(factory, registry, 10);
    }

    @AfterEach
    void tearDown() {
        slowRuntime.stopAll();
    }

    @Test
    @DisplayName("getName returns 'list_subagents'")
    void toolName() {
        assertEquals("list_subagents", new ListSubagentsTool(slowRuntime).getName());
    }

    @Test
    @DisplayName("getSchema returns empty schema (no params)")
    void emptySchema() {
        ListSubagentsTool tool = new ListSubagentsTool(slowRuntime);
        assertTrue(tool.getSchema().getProperties().isEmpty());
    }

    @Test
    @DisplayName("returns 'No active sub-agents' when none are running")
    void noActiveSubagents() {
        ListSubagentsTool tool = new ListSubagentsTool(slowRuntime);
        ToolResult result = tool.execute(Map.of());

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("No active"),
                "Should say no active sub-agents: " + result.getContent());
    }

    @Test
    @DisplayName("lists all active subagents with their task descriptions and status")
    void listsActiveSubagentsWithPayload() throws InterruptedException {
        slowRuntime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("analyze-data")
                .agentId("worker")
                .build(), e -> {});
        slowRuntime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("generate-report")
                .agentId("worker")
                .build(), e -> {});

        Thread.sleep(100);

        ListSubagentsTool tool = new ListSubagentsTool(slowRuntime);
        ToolResult result = tool.execute(Map.of());

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("analyze-data"),
                "Should list first task: " + result.getContent());
        assertTrue(result.getContent().contains("generate-report"),
                "Should list second task: " + result.getContent());
        assertTrue(result.getContent().contains("2"),
                "Should show count of 2: " + result.getContent());
        assertTrue(result.getContent().contains("RUNNING"),
                "Should show RUNNING status: " + result.getContent());
    }

    @Test
    @DisplayName("after stopAll, list returns no active subagents")
    void afterStopAllReturnsEmpty() throws InterruptedException {
        slowRuntime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("will-be-stopped")
                .agentId("worker")
                .build(), e -> {});
        Thread.sleep(100);

        slowRuntime.stopAll();

        ListSubagentsTool tool = new ListSubagentsTool(slowRuntime);
        ToolResult result = tool.execute(Map.of());

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("No active"));
    }

    // ==================== Helpers ====================

    private static class SlowMockProvider implements LLMProvider {
        private final long delayMs;
        SlowMockProvider(long delayMs) { this.delayMs = delayMs; }
        @Override
        public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
            return LLMResponse.builder().stopReason("end_turn")
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent("done").build())
                    .build();
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

    private static class StubMemory implements MemoryProvider {
        @Override public void addMessage(String s, Message m) {}
        @Override public List<Message> getHistory(String s, int l) { return List.of(); }
        @Override public void clearSession(String s) {}
        @Override public void writeEphemeral(String c) {}
        @Override public void writeDurable(String s, String c) {}
        @Override public List<MemorySearchResult> search(String q) { return List.of(); }
    }
}
