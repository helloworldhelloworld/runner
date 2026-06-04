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

@DisplayName("ListSubagentsTool - 列出活跃 subagent 状态")
class ListSubagentsToolTest {

    private SubagentRuntime runtime;
    private ListSubagentsTool tool;

    @BeforeEach
    void setUp() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("worker")
                .systemPrompt("worker")
                .maxSpawnDepth(2)
                .build());
        registry.setDefault("worker");

        LLMProvider slowProvider = new SlowMockProvider(5000);
        AgentFactory factory = new AgentFactory(slowProvider, new StubMemory(), new ToolRegistry());
        runtime = new SubagentRuntime(factory, registry, 10);
        tool = new ListSubagentsTool(runtime);
    }

    @Test
    @DisplayName("工具名称为 list_subagents")
    void toolName() {
        assertEquals("list_subagents", tool.getName());
    }

    @Test
    @DisplayName("schema 为空（无参数工具）")
    void schemaIsEmpty() {
        ToolSchema schema = tool.getSchema();
        assertTrue(schema.getProperties().isEmpty());
    }

    @Test
    @DisplayName("无活跃 subagent 时返回提示信息")
    void noActiveSubagents() {
        ToolResult result = tool.execute(Map.of());

        assertFalse(result.isError());
        assertEquals("No active sub-agents.", result.getContent());
    }

    @Test
    @DisplayName("有活跃 subagent 时列出状态信息")
    void listsActiveSubagents() throws InterruptedException {
        String runId = runtime.spawn(
                SpawnRequest.builder()
                        .parentSessionKey("agent:worker:main:s1")
                        .task("analyzing data")
                        .agentId("worker")
                        .build(),
                e -> {});

        Thread.sleep(50);

        ToolResult result = tool.execute(Map.of());

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("Active sub-agents (1)"));
        assertTrue(result.getContent().contains(runId));
        assertTrue(result.getContent().contains("analyzing data"));
        assertTrue(result.getContent().contains("RUNNING"));

        runtime.stopAll();
    }

    @Test
    @DisplayName("列出多个活跃 subagent")
    void listsMultipleActiveSubagents() throws InterruptedException {
        runtime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("task-1").agentId("worker").build(), e -> {});
        runtime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("task-2").agentId("worker").build(), e -> {});

        Thread.sleep(50);

        ToolResult result = tool.execute(Map.of());

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("Active sub-agents (2)"));
        assertTrue(result.getContent().contains("task-1"));
        assertTrue(result.getContent().contains("task-2"));

        runtime.stopAll();
    }

    private static class SlowMockProvider implements LLMProvider {
        private final long delayMs;
        SlowMockProvider(long delayMs) { this.delayMs = delayMs; }
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
        @Override public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            return Flux.just(StreamEvent.llmComplete(complete(m, o)));
        }
        @Override public ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return "slow-mock"; }
    }

    private static class StubMemory implements MemoryProvider {
        @Override public void addMessage(String sessionId, Message message) {}
        @Override public List<Message> getHistory(String sessionId, int limit) { return List.of(); }
        @Override public void clearSession(String sessionId) {}
        @Override public void writeEphemeral(String content) {}
        @Override public void writeDurable(String section, String content) {}
        @Override public List<MemorySearchResult> search(String query) { return List.of(); }
    }
}
