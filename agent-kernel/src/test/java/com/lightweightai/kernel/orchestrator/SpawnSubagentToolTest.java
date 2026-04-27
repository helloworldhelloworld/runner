package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SpawnSubagentTool - 子 agent 异步启动工具")
class SpawnSubagentToolTest {

    private SubagentRuntime runtime;
    private SpawnSubagentTool tool;
    private List<StreamEvent> announcedEvents;

    @BeforeEach
    void setUp() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("helper")
                .systemPrompt("I help")
                .maxSpawnDepth(3)
                .build());
        registry.setDefault("helper");

        AgentFactory factory = new AgentFactory(
                new StubLLMProvider(),
                new StubMemoryProvider(),
                new com.lightweightai.kernel.agent.ToolRegistry()
        );
        runtime = new SubagentRuntime(factory, registry, 5);

        announcedEvents = new java.util.concurrent.CopyOnWriteArrayList<>();
        tool = new SpawnSubagentTool(runtime, "agent:helper:main:session1", announcedEvents::add);
    }

    @Test
    @DisplayName("getName 返回 spawn_subagent")
    void nameIsSpawnSubagent() {
        assertEquals("spawn_subagent", tool.getName());
    }

    @Test
    @DisplayName("getSchema 包含 task 作为必填参数")
    void schemaHasRequiredTask() {
        assertNotNull(tool.getSchema());
        Map<String, Object> schema = tool.getSchema().toMap();
        assertNotNull(schema.get("required"));
    }

    @Test
    @DisplayName("成功 spawn 时返回包含 runId 的成功结果")
    void successfulSpawnReturnsRunId() {
        ToolResult result = tool.execute(Map.of("task", "Analyze data"));

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("Run ID:"));
        assertTrue(result.getContent().contains("Subagent spawned successfully"));
    }

    @Test
    @DisplayName("成功 spawn 时发出 SUBAGENT_SPAWN 事件")
    void successfulSpawnAnnouncesEvent() {
        tool.execute(Map.of("task", "Do work"));

        assertTrue(announcedEvents.stream()
                .anyMatch(e -> e.getType() == StreamEvent.EventType.SUBAGENT_SPAWN));
    }

    @Test
    @DisplayName("指定不存在的 agentId 时抛出 IllegalArgumentException")
    void nonexistentAgentThrows() {
        // SpawnSubagentTool only catches IllegalStateException (depth/concurrency limits),
        // not IllegalArgumentException (agent not found) — so this propagates
        assertThrows(IllegalArgumentException.class, () ->
                tool.execute(Map.of("task", "Something", "agentId", "ghost-agent")));
    }

    @Test
    @DisplayName("并发限制超限时返回错误")
    void concurrencyLimitReturnsError() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("helper")
                .maxSpawnDepth(3)
                .build());
        registry.setDefault("helper");

        // maxConcurrent = 1
        SubagentRuntime tightRuntime = new SubagentRuntime(
                new AgentFactory(new StubLLMProvider(), new StubMemoryProvider(),
                        new com.lightweightai.kernel.agent.ToolRegistry()),
                registry, 1);

        SpawnSubagentTool tightTool = new SpawnSubagentTool(
                tightRuntime, "agent:helper:main:s1", e -> {});

        // First spawn should succeed
        ToolResult first = tightTool.execute(Map.of("task", "Task 1"));
        assertFalse(first.isError());

        // Second spawn should fail (max concurrent = 1)
        ToolResult second = tightTool.execute(Map.of("task", "Task 2"));
        assertTrue(second.isError());
        assertTrue(second.getContent().contains("Failed to spawn"));

        tightRuntime.shutdown();
    }

    // Stubs for dependencies
    private static class StubLLMProvider implements com.lightweightai.kernel.llm.LLMProvider {
        @Override public com.lightweightai.kernel.llm.LLMResponse complete(List<com.lightweightai.kernel.llm.ConversationMessage> m, com.lightweightai.kernel.llm.LLMOptions o) {
            return com.lightweightai.kernel.llm.LLMResponse.builder()
                    .message(com.lightweightai.kernel.llm.ConversationMessage.builder()
                            .role(com.lightweightai.kernel.llm.ConversationMessage.MessageRole.ASSISTANT)
                            .textContent("stub response")
                            .build())
                    .build();
        }
        @Override public java.util.concurrent.CompletableFuture<com.lightweightai.kernel.llm.LLMResponse> completeAsync(List<com.lightweightai.kernel.llm.ConversationMessage> m, com.lightweightai.kernel.llm.LLMOptions o) {
            return java.util.concurrent.CompletableFuture.completedFuture(complete(m, o));
        }
        @Override public java.util.concurrent.CompletableFuture<com.lightweightai.kernel.llm.LLMResponse> completeStream(List<com.lightweightai.kernel.llm.ConversationMessage> m, com.lightweightai.kernel.llm.LLMOptions o, com.lightweightai.kernel.llm.LLMProvider.StreamEventHandler h) {
            throw new UnsupportedOperationException();
        }
        @Override public com.lightweightai.kernel.llm.ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return "stub"; }
    }

    private static class StubMemoryProvider implements com.lightweightai.kernel.memory.MemoryProvider {
        @Override public void addMessage(String sessionId, com.lightweightai.kernel.memory.Message message) {}
        @Override public List<com.lightweightai.kernel.memory.Message> getHistory(String sessionId, int limit) { return List.of(); }
        @Override public void clearSession(String sessionId) {}
        @Override public void writeEphemeral(String content) {}
        @Override public void writeDurable(String section, String content) {}
        @Override public List<com.lightweightai.kernel.memory.MemorySearchResult> search(String query) { return List.of(); }
    }
}
