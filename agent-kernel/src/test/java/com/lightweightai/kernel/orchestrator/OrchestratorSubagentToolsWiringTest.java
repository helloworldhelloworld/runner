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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Transmission chain test: ToolRegistry → SubagentRuntime → subagent tools.
 * Verifies that tools registered in OrchestratorConfig wiring are visible
 * in getToolDefinitions() and executable through the registry.
 */
@DisplayName("Orchestrator subagent tools wiring — transmission chain")
class OrchestratorSubagentToolsWiringTest {

    private ToolRegistry toolRegistry;
    private SubagentRuntime runtime;

    @BeforeEach
    void setUp() {
        AgentRegistry agentRegistry = new AgentRegistry();
        agentRegistry.register(AgentProfile.builder()
                .agentId("default").displayName("Default").maxSpawnDepth(1).build());
        agentRegistry.setDefault("default");

        toolRegistry = new ToolRegistry();
        AgentFactory agentFactory = new AgentFactory(new FastMockProvider(), new StubMemory(), toolRegistry);
        runtime = new SubagentRuntime(agentFactory, agentRegistry, 5);

        toolRegistry.register(new WaitSubagentTool(runtime));
        toolRegistry.register(new ListSubagentsTool(runtime));
    }

    @AfterEach
    void tearDown() { runtime.shutdown(); }

    @Test
    @DisplayName("wait_subagent and list_subagents are registered and visible")
    void toolsAreRegistered() {
        assertTrue(toolRegistry.get("wait_subagent").isPresent());
        assertTrue(toolRegistry.get("list_subagents").isPresent());
    }

    @Test
    @DisplayName("tool definitions include subagent tools for LLM visibility")
    void toolDefinitionsIncludeSubagentTools() {
        List<String> names = toolRegistry.getToolDefinitions().stream()
                .map(d -> (String) d.get("name")).toList();
        assertTrue(names.contains("wait_subagent"));
        assertTrue(names.contains("list_subagents"));
    }

    @Test
    @DisplayName("end-to-end: spawn → wait → verify result propagates through registry")
    void spawnWaitTransmissionChain() throws InterruptedException {
        List<StreamEvent> events = java.util.Collections.synchronizedList(new ArrayList<>());
        SpawnSubagentTool spawnTool = new SpawnSubagentTool(runtime, "agent:default:main:s1", events::add);

        ToolResult spawnResult = spawnTool.execute(Map.of("task", "compute"));
        assertFalse(spawnResult.isError());

        String content = spawnResult.getContent();
        String runId = content.substring(content.indexOf("Run ID: ") + 8,
                content.indexOf(" (non-blocking"));

        assertTrue(runtime.waitForCompletion(runId, 5, TimeUnit.SECONDS));
        Thread.sleep(100);

        Tool waitTool = toolRegistry.get("wait_subagent").orElseThrow();
        ToolResult waitResult = waitTool.execute(Map.of("runIds", runId));
        assertFalse(waitResult.isError(), "Wait should succeed: " + waitResult.getContent());
        assertTrue(waitResult.getContent().contains("ms)"));
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

    private static class StubMemory implements MemoryProvider {
        @Override public void addMessage(String sid, Message m) {}
        @Override public List<Message> getHistory(String sid, int limit) { return List.of(); }
        @Override public void clearSession(String sid) {}
        @Override public void writeEphemeral(String content) {}
        @Override public void writeDurable(String section, String content) {}
        @Override public List<MemorySearchResult> search(String query) { return List.of(); }
    }
}
