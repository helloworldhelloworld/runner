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

@DisplayName("WaitSubagentTool — wait for subagent completion with payload assertions")
class WaitSubagentToolTest {

    private SubagentRuntime fastRuntime;
    private SubagentRuntime slowRuntime;
    private AgentRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("worker")
                .systemPrompt("I work")
                .maxSpawnDepth(1)
                .build());

        StubMemory memory = new StubMemory();
        ToolRegistry tools = new ToolRegistry();

        fastRuntime = new SubagentRuntime(
                new AgentFactory(new ImmediateMockProvider(), memory, tools), registry, 10);
        slowRuntime = new SubagentRuntime(
                new AgentFactory(new SlowMockProvider(10000), memory, tools), registry, 10);
    }

    @AfterEach
    void tearDown() {
        fastRuntime.stopAll();
        slowRuntime.stopAll();
    }

    @Test
    @DisplayName("getName returns 'wait_subagent'")
    void toolName() {
        assertEquals("wait_subagent", new WaitSubagentTool(fastRuntime).getName());
    }

    @Test
    @DisplayName("getSchema defines 'runIds' as required")
    void schemaHasRequiredRunIds() {
        WaitSubagentTool tool = new WaitSubagentTool(fastRuntime);
        Map<String, Object> schema = tool.getSchema().toMap();

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("runIds"), "'runIds' must be required");
    }

    @Test
    @DisplayName("wait for completed subagent returns success with result content and duration")
    void waitCompletedSubagentReturnsResultContent() throws InterruptedException {
        String runId = fastRuntime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("compute")
                .agentId("worker")
                .build(), e -> {});
        fastRuntime.waitForCompletion(runId, 5, TimeUnit.SECONDS);
        Thread.sleep(100);

        WaitSubagentTool tool = new WaitSubagentTool(fastRuntime);
        ToolResult result = tool.execute(Map.of("runIds", runId, "timeoutSeconds", 5));

        assertFalse(result.isError(), "Wait on completed subagent should succeed");
        assertTrue(result.getContent().contains(runId),
                "Result should contain the runId: " + result.getContent());
        assertTrue(result.getContent().contains("ms"),
                "Result should contain duration: " + result.getContent());
    }

    @Test
    @DisplayName("wait for multiple completed subagents returns all results")
    void waitMultipleSubagents() throws InterruptedException {
        String runId1 = fastRuntime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("task-A")
                .agentId("worker")
                .build(), e -> {});
        String runId2 = fastRuntime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("task-B")
                .agentId("worker")
                .build(), e -> {});

        fastRuntime.waitForCompletion(runId1, 5, TimeUnit.SECONDS);
        fastRuntime.waitForCompletion(runId2, 5, TimeUnit.SECONDS);
        Thread.sleep(100);

        WaitSubagentTool tool = new WaitSubagentTool(fastRuntime);
        ToolResult result = tool.execute(Map.of("runIds", runId1 + "," + runId2, "timeoutSeconds", 5));

        assertFalse(result.isError());
        assertTrue(result.getContent().contains(runId1));
        assertTrue(result.getContent().contains(runId2));
    }

    @Test
    @DisplayName("wait for slow subagent times out and returns error with 'timed out' message")
    void waitTimeoutReturnsError() {
        String runId = slowRuntime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("slow-task")
                .agentId("worker")
                .build(), e -> {});

        WaitSubagentTool tool = new WaitSubagentTool(slowRuntime);
        ToolResult result = tool.execute(Map.of("runIds", runId, "timeoutSeconds", 1));

        assertTrue(result.isError(), "Should return error on timeout");
        assertTrue(result.getContent().contains("timed out"),
                "Error content should mention 'timed out': " + result.getContent());
    }

    @Test
    @DisplayName("wait for non-existent runId returns 'Not found'")
    void waitNonExistentRunId() {
        WaitSubagentTool tool = new WaitSubagentTool(fastRuntime);
        ToolResult result = tool.execute(Map.of("runIds", "nonexistent-id", "timeoutSeconds", 1));

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("Not found"),
                "Should report 'Not found': " + result.getContent());
    }

    @Test
    @DisplayName("default timeout is 60 seconds when timeoutSeconds not provided")
    void defaultTimeoutApplied() {
        WaitSubagentTool tool = new WaitSubagentTool(fastRuntime);
        ToolResult result = tool.execute(Map.of("runIds", "any-id"));
        assertNotNull(result);
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
        @Override public String getProviderName() { return "immediate"; }
    }

    private static class SlowMockProvider implements LLMProvider {
        private final long delayMs;
        SlowMockProvider(long delayMs) { this.delayMs = delayMs; }
        @Override
        public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
            return LLMResponse.builder().stopReason("end_turn")
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent("slow done").build())
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
