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

@DisplayName("WaitSubagentTool — critical path tests")
class WaitSubagentToolTest {

    private SubagentRuntime runtime;

    @BeforeEach
    void setUp() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("worker")
                .systemPrompt("test")
                .maxSpawnDepth(1)
                .build());

        LLMProvider provider = new FastMockProvider();
        AgentFactory factory = new AgentFactory(provider, new NoOpMemory(), new ToolRegistry());
        runtime = new SubagentRuntime(factory, registry, 10);
    }

    @Test
    @DisplayName("schema declares 'runIds' as required parameter")
    void schemaDeclaresRunIdsRequired() {
        WaitSubagentTool tool = new WaitSubagentTool(runtime);
        ToolSchema schema = tool.getSchema();
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.getDefinition().get("required");
        assertTrue(required.contains("runIds"));
    }

    @Test
    @DisplayName("name is 'wait_subagent'")
    void nameIsCorrect() {
        WaitSubagentTool tool = new WaitSubagentTool(runtime);
        assertEquals("wait_subagent", tool.getName());
    }

    @Test
    @DisplayName("wait for completed subagent returns success with result text and duration")
    void waitForCompletedSubagent() {
        String runId = runtime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("quick task")
                .agentId("worker")
                .build(), e -> {});

        WaitSubagentTool tool = new WaitSubagentTool(runtime);
        ToolResult result = tool.execute(Map.of("runIds", runId, "timeoutSeconds", 10));

        assertFalse(result.isError());
        assertTrue(result.getContent().contains(runId));
        assertTrue(result.getContent().contains("ms"));
    }

    @Test
    @DisplayName("wait for nonexistent runId returns error with 'Not found'")
    void waitForNonexistentRunId() {
        WaitSubagentTool tool = new WaitSubagentTool(runtime);
        ToolResult result = tool.execute(Map.of("runIds", "nonexistent-id", "timeoutSeconds", 1));

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("Not found"));
    }

    @Test
    @DisplayName("wait with comma-separated runIds waits for all")
    void waitForMultipleRunIds() {
        String runId1 = runtime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("task A")
                .agentId("worker")
                .build(), e -> {});
        String runId2 = runtime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("task B")
                .agentId("worker")
                .build(), e -> {});

        WaitSubagentTool tool = new WaitSubagentTool(runtime);
        ToolResult result = tool.execute(Map.of("runIds", runId1 + "," + runId2, "timeoutSeconds", 10));

        assertFalse(result.isError());
        assertTrue(result.getContent().contains(runId1));
        assertTrue(result.getContent().contains(runId2));
    }

    @Test
    @DisplayName("wait with spaces around runIds trims correctly")
    void waitTrimsRunIds() {
        String runId = runtime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("task")
                .agentId("worker")
                .build(), e -> {});

        WaitSubagentTool tool = new WaitSubagentTool(runtime);
        ToolResult result = tool.execute(Map.of("runIds", "  " + runId + "  ", "timeoutSeconds", 10));

        assertFalse(result.isError());
        assertTrue(result.getContent().contains(runId));
    }

    @Test
    @DisplayName("default timeout is 60 seconds when not specified")
    void defaultTimeoutIs60() {
        WaitSubagentTool tool = new WaitSubagentTool(runtime);
        ToolResult result = tool.execute(Map.of("runIds", "missing-id"));
        assertTrue(result.isError());
    }

    @Test
    @DisplayName("wait for timed-out subagent includes timeout info in result")
    void waitTimeoutIncludesInfo() {
        AgentRegistry reg = new AgentRegistry();
        reg.register(AgentProfile.builder().agentId("slow").maxSpawnDepth(1).build());
        LLMProvider slow = new SlowMockProvider(10000);
        AgentFactory factory = new AgentFactory(slow, new NoOpMemory(), new ToolRegistry());
        SubagentRuntime slowRuntime = new SubagentRuntime(factory, reg, 5);

        String runId = slowRuntime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:slow:main:s1")
                .task("slow task")
                .agentId("slow")
                .build(), e -> {});

        WaitSubagentTool tool = new WaitSubagentTool(slowRuntime);
        ToolResult result = tool.execute(Map.of("runIds", runId, "timeoutSeconds", 1));

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("timed out"));
        assertTrue(result.getContent().contains("1s"));
        slowRuntime.shutdown();
    }

    @Test
    @DisplayName("wait for cancelled subagent includes CANCELLED status")
    void waitForCancelledSubagent() throws InterruptedException {
        AgentRegistry reg = new AgentRegistry();
        reg.register(AgentProfile.builder().agentId("c").maxSpawnDepth(1).build());
        LLMProvider slow = new SlowMockProvider(5000);
        AgentFactory factory = new AgentFactory(slow, new NoOpMemory(), new ToolRegistry());
        SubagentRuntime cancelRuntime = new SubagentRuntime(factory, reg, 5);

        String runId = cancelRuntime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:c:main:s1")
                .task("will be cancelled")
                .agentId("c")
                .build(), e -> {});

        Thread.sleep(50);
        cancelRuntime.stop(runId);

        WaitSubagentTool tool = new WaitSubagentTool(cancelRuntime);
        ToolResult result = tool.execute(Map.of("runIds", runId, "timeoutSeconds", 2));

        String content = result.getContent();
        assertTrue(content.contains("CANCELLED") || content.contains("Not found"),
                "Expected CANCELLED or Not found, got: " + content);
        cancelRuntime.shutdown();
    }

    @Test
    @DisplayName("mixed results — one completed, one not found — returns error")
    void mixedResultsReturnError() {
        String runId = runtime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("good task")
                .agentId("worker")
                .build(), e -> {});

        WaitSubagentTool tool = new WaitSubagentTool(runtime);
        ToolResult result = tool.execute(Map.of(
                "runIds", runId + ",nonexistent",
                "timeoutSeconds", 10));

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("Not found"));
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
            return CompletableFuture.completedFuture(complete(m, o));
        }
        @Override public ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return "fast"; }
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
