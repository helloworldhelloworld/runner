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

@DisplayName("SpawnSubagentTool - LLM-callable spawn tool")
class SpawnSubagentToolTest {

    private SubagentRuntime runtime;
    private SpawnSubagentTool tool;
    private List<StreamEvent> capturedEvents;

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

        capturedEvents = java.util.Collections.synchronizedList(new ArrayList<>());
        tool = new SpawnSubagentTool(runtime, "agent:worker:main:s1", capturedEvents::add);
    }

    @Test
    @DisplayName("getName returns spawn_subagent")
    void testName() {
        assertEquals("spawn_subagent", tool.getName());
    }

    @Test
    @DisplayName("getSchema requires task field")
    void testSchema() {
        ToolSchema schema = tool.getSchema();
        assertNotNull(schema);
        assertNotNull(schema.toMap());
        Object required = schema.toMap().get("required");
        assertNotNull(required);
        assertTrue(required.toString().contains("task"));
    }

    @Test
    @DisplayName("execute spawns subagent and returns runId in success result")
    void testExecuteReturnsRunId() throws InterruptedException {
        ToolResult result = tool.execute(Map.of("task", "analyze data"));

        assertFalse(result.isError(), "Should succeed: " + result.getContent());
        assertTrue(result.getContent().contains("Run ID:"));
        assertTrue(result.getContent().contains("Subagent spawned successfully"));

        String runId = extractRunId(result.getContent());
        assertTrue(runtime.waitForCompletion(runId, 5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("execute with agentId routes to specific agent")
    void testExecuteWithAgentId() throws InterruptedException {
        ToolResult result = tool.execute(Map.of(
                "task", "do work",
                "agentId", "worker"
        ));

        assertFalse(result.isError());
        String runId = extractRunId(result.getContent());
        assertTrue(runtime.waitForCompletion(runId, 5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("execute emits SUBAGENT_SPAWN event via announcer")
    void testExecuteEmitsSpawnEvent() throws InterruptedException {
        ToolResult result = tool.execute(Map.of("task", "test task"));
        String runId = extractRunId(result.getContent());

        assertTrue(runtime.waitForCompletion(runId, 5, TimeUnit.SECONDS));
        Thread.sleep(100);

        assertTrue(capturedEvents.stream().anyMatch(e ->
                e.getType() == StreamEvent.EventType.SUBAGENT_SPAWN),
                "Expected SUBAGENT_SPAWN event");
    }

    @Test
    @DisplayName("execute returns error when depth limit exceeded")
    void testDepthLimitReturnsError() {
        SpawnSubagentTool deepTool = new SpawnSubagentTool(
                runtime,
                "agent:worker:subagent:a:subagent:b",
                capturedEvents::add);

        ToolResult result = deepTool.execute(Map.of("task", "too deep"));
        assertTrue(result.isError());
        assertTrue(result.getContent().contains("Failed to spawn"));
    }

    private String extractRunId(String content) {
        int start = content.indexOf("Run ID: ") + 8;
        int end = content.indexOf(" ", start);
        if (end == -1) end = content.length();
        return content.substring(start, end);
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
