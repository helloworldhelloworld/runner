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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WaitSubagentTool — wait for subagent results")
class WaitSubagentToolTest {

    private SubagentRuntime runtime;
    private WaitSubagentTool tool;

    @BeforeEach
    void setUp() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("worker").systemPrompt("I am worker").maxSpawnDepth(1).build());
        registry.setDefault("worker");

        AgentFactory factory = new AgentFactory(new FastMockProvider(), new StubMemory(), new ToolRegistry());
        runtime = new SubagentRuntime(factory, registry, 5);
        tool = new WaitSubagentTool(runtime);
    }

    @AfterEach
    void tearDown() { runtime.shutdown(); }

    @Test
    @DisplayName("tool name and schema are correct")
    void toolMetadata() {
        assertEquals("wait_subagent", tool.getName());
        ToolSchema schema = tool.getSchema();
        assertTrue(schema.getProperties().containsKey("runIds"));
        assertTrue(schema.getProperties().containsKey("timeoutSeconds"));
    }

    @Test
    @DisplayName("wait for completed subagent returns success with result text")
    void waitForCompleted() throws InterruptedException {
        String runId = spawnAndWait();
        ToolResult result = tool.execute(Map.of("runIds", runId));

        assertFalse(result.isError(), "Should succeed: " + result.getContent());
        assertTrue(result.getContent().contains(runId));
        assertTrue(result.getContent().contains("ms)"), "Should contain duration");
    }

    @Test
    @DisplayName("wait for multiple subagents returns all results")
    void waitForMultiple() throws InterruptedException {
        String runId1 = spawnAndWait();
        String runId2 = spawnAndWait();

        ToolResult result = tool.execute(Map.of("runIds", runId1 + "," + runId2));
        assertFalse(result.isError());
        assertTrue(result.getContent().contains(runId1));
        assertTrue(result.getContent().contains(runId2));
    }

    @Test
    @DisplayName("wait for unknown runId returns error with 'Not found'")
    void waitForUnknown() {
        ToolResult result = tool.execute(Map.of("runIds", "nonexistent-id"));
        assertTrue(result.isError());
        assertTrue(result.getContent().contains("Not found"));
    }

    @Test
    @DisplayName("wait with custom timeout parameter is respected")
    void customTimeout() throws InterruptedException {
        String runId = spawnAndWait();
        ToolResult result = tool.execute(Map.of("runIds", runId, "timeoutSeconds", 5));
        assertFalse(result.isError());
    }

    @Test
    @DisplayName("wait for slow subagent times out")
    void timeoutOnSlowSubagent() {
        AgentRegistry reg = new AgentRegistry();
        reg.register(AgentProfile.builder().agentId("worker").systemPrompt("w").maxSpawnDepth(1).build());
        reg.setDefault("worker");

        SubagentRuntime slowRuntime = new SubagentRuntime(
                new AgentFactory(new SlowMockProvider(10000), new StubMemory(), new ToolRegistry()),
                reg, 5);

        String runId = slowRuntime.spawn(
                SpawnRequest.builder().parentSessionKey("agent:worker:main:s1")
                        .task("slow task").agentId("worker").build(), e -> {});

        WaitSubagentTool slowTool = new WaitSubagentTool(slowRuntime);
        ToolResult result = slowTool.execute(Map.of("runIds", runId, "timeoutSeconds", 1));
        assertTrue(result.isError());
        assertTrue(result.getContent().contains("timed out"));
        slowRuntime.shutdown();
    }

    private String spawnAndWait() throws InterruptedException {
        String runId = runtime.spawn(
                SpawnRequest.builder().parentSessionKey("agent:worker:main:s1")
                        .task("test task").agentId("worker").build(), e -> {});
        assertTrue(runtime.waitForCompletion(runId, 5, TimeUnit.SECONDS));
        Thread.sleep(100);
        return runId;
    }

    private static class FastMockProvider implements LLMProvider {
        @Override public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            return Flux.just(StreamEvent.textDelta("done"), StreamEvent.llmComplete(buildResponse()));
        }
        private LLMResponse buildResponse() {
            return LLMResponse.builder().message(ConversationMessage.builder()
                    .role(ConversationMessage.MessageRole.ASSISTANT).textContent("done").build())
                    .stopReason("end_turn").build();
        }
        @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) { return buildResponse(); }
        @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
            return CompletableFuture.completedFuture(complete(m, o));
        }
        @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) {
            return CompletableFuture.completedFuture(complete(m, o));
        }
        @Override public ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return "fast-mock"; }
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
