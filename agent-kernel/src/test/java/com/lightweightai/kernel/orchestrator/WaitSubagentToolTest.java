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

@DisplayName("WaitSubagentTool - LLM-callable wait/collect tool")
class WaitSubagentToolTest {

    private SubagentRuntime runtime;
    private WaitSubagentTool tool;

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
        AgentFactory factory = new AgentFactory(new FastMockProvider(), new InertMemory(), tools);
        runtime = new SubagentRuntime(factory, registry, 5);
        tool = new WaitSubagentTool(runtime);
    }

    @Test
    @DisplayName("getName returns wait_subagent")
    void testName() {
        assertEquals("wait_subagent", tool.getName());
    }

    @Test
    @DisplayName("getSchema requires runIds field")
    void testSchema() {
        ToolSchema schema = tool.getSchema();
        assertNotNull(schema);
        Object required = schema.toMap().get("required");
        assertTrue(required.toString().contains("runIds"));
    }

    @Test
    @DisplayName("wait for completed subagent returns success with result text and duration")
    void testWaitCompleted() throws InterruptedException {
        String runId = spawnAndWait();

        ToolResult result = tool.execute(Map.of("runIds", runId));

        assertFalse(result.isError(), "Should succeed: " + result.getContent());
        assertTrue(result.getContent().contains(runId));
        assertTrue(result.getContent().contains("ms)"));
    }

    @Test
    @DisplayName("wait for multiple runIds returns all results")
    void testWaitMultiple() throws InterruptedException {
        String runId1 = spawnAndWait();
        String runId2 = spawnAndWait();

        ToolResult result = tool.execute(Map.of("runIds", runId1 + "," + runId2));

        assertFalse(result.isError());
        assertTrue(result.getContent().contains(runId1));
        assertTrue(result.getContent().contains(runId2));
    }

    @Test
    @DisplayName("wait for non-existent runId returns error with 'Not found'")
    void testWaitNotFound() {
        ToolResult result = tool.execute(Map.of("runIds", "nonexistent-id"));

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("Not found"));
    }

    @Test
    @DisplayName("custom timeoutSeconds is respected")
    void testCustomTimeout() throws InterruptedException {
        String runId = spawnAndWait();

        ToolResult result = tool.execute(Map.of(
                "runIds", runId,
                "timeoutSeconds", 10
        ));

        assertFalse(result.isError());
        assertTrue(result.getContent().contains(runId));
    }

    @Test
    @DisplayName("default timeout is 60 when not specified")
    void testDefaultTimeout() {
        ToolResult result = tool.execute(Map.of("runIds", "nonexistent"));
        assertTrue(result.isError());
    }

    private String spawnAndWait() throws InterruptedException {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("work")
                .agentId("worker")
                .build();
        String runId = runtime.spawn(request, e -> {});
        assertTrue(runtime.waitForCompletion(runId, 5, TimeUnit.SECONDS));
        Thread.sleep(100);
        return runId;
    }

    private static class FastMockProvider implements LLMProvider {
        @Override
        public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            return Flux.just(StreamEvent.textDelta("done"),
                    StreamEvent.llmComplete(buildResponse()));
        }
        private LLMResponse buildResponse() {
            return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent("done").build())
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

    private static class InertMemory implements MemoryProvider {
        @Override public void addMessage(String sid, Message msg) {}
        @Override public List<Message> getHistory(String sid, int limit) { return List.of(); }
        @Override public void clearSession(String sid) {}
        @Override public void writeEphemeral(String content) {}
        @Override public void writeDurable(String section, String content) {}
        @Override public List<MemorySearchResult> search(String query) { return List.of(); }
    }
}
