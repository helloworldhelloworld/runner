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

@DisplayName("SpawnSubagentTool — LLM-callable spawn tool")
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
        LLMProvider provider = new FastMockProvider();
        AgentFactory factory = new AgentFactory(provider, new StubMemory(), tools);
        runtime = new SubagentRuntime(factory, registry, 5);

        capturedEvents = java.util.Collections.synchronizedList(new ArrayList<>());
        tool = new SpawnSubagentTool(runtime, "agent:worker:main:s1", capturedEvents::add);
    }

    @Test
    @DisplayName("tool name and schema are correct")
    void toolMetadata() {
        assertEquals("spawn_subagent", tool.getName());
        assertNotNull(tool.getDescription());
        assertFalse(tool.getDescription().isEmpty());

        ToolSchema schema = tool.getSchema();
        assertNotNull(schema);
        Map<String, Object> props = schema.getProperties();
        assertTrue(props.containsKey("task"), "Schema should have 'task' property");
    }

    @Test
    @DisplayName("execute spawns subagent and returns success with runId")
    void executeSuccess() throws InterruptedException {
        ToolResult result = tool.execute(Map.of("task", "do something"));

        assertFalse(result.isError(), "Should succeed: " + result.getContent());
        assertTrue(result.getContent().contains("Run ID:"), "Should contain run ID");
        assertTrue(result.getContent().contains("non-blocking"), "Should mention non-blocking");

        assertTrue(capturedEvents.stream()
                .anyMatch(e -> e.getType() == StreamEvent.EventType.SUBAGENT_SPAWN));

        Thread.sleep(500);
        assertTrue(capturedEvents.stream()
                .anyMatch(e -> e.getType() == StreamEvent.EventType.SUBAGENT_COMPLETE));
    }

    @Test
    @DisplayName("execute with agentId routes to specific agent")
    void executeWithAgentId() {
        ToolResult result = tool.execute(Map.of("task", "targeted task", "agentId", "worker"));
        assertFalse(result.isError());
        assertTrue(result.getContent().contains("Run ID:"));
    }

    @Test
    @DisplayName("execute returns error when spawn fails (depth limit)")
    void executeFailsOnDepthLimit() {
        SpawnSubagentTool deepTool = new SpawnSubagentTool(
                runtime,
                "agent:worker:subagent:a:subagent:b",
                capturedEvents::add);

        ToolResult result = deepTool.execute(Map.of("task", "too deep", "agentId", "worker"));
        assertTrue(result.isError(), "Should fail on depth limit");
        assertTrue(result.getContent().contains("Failed to spawn"));
    }

    @Test
    @DisplayName("execute with optional model parameter does not fail")
    void executeWithModel() {
        ToolResult result = tool.execute(Map.of(
                "task", "with model",
                "model", "claude-3-opus"));
        assertFalse(result.isError());
    }

    private static class FastMockProvider implements LLMProvider {
        @Override
        public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            return Flux.just(StreamEvent.textDelta("done"), StreamEvent.llmComplete(buildResponse()));
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

    private static class StubMemory implements MemoryProvider {
        @Override public void addMessage(String sid, Message m) {}
        @Override public List<Message> getHistory(String sid, int limit) { return List.of(); }
        @Override public void clearSession(String sid) {}
        @Override public void writeEphemeral(String content) {}
        @Override public void writeDurable(String section, String content) {}
        @Override public List<MemorySearchResult> search(String query) { return List.of(); }
    }
}
