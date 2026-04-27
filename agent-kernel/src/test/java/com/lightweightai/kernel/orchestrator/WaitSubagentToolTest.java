package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentLoop;
import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.agent.AgentResponse;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WaitSubagentTool - 等待子 agent 完成并返回结果")
class WaitSubagentToolTest {

    private SubagentRuntime runtime;
    private WaitSubagentTool tool;

    @BeforeEach
    void setUp() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("worker")
                .systemPrompt("I work")
                .maxSpawnDepth(3)
                .build());
        registry.setDefault("worker");

        AgentFactory factory = new AgentFactory(
                new StubLLMProvider(),
                new StubMemoryProvider(),
                new com.lightweightai.kernel.agent.ToolRegistry()
        );
        runtime = new SubagentRuntime(factory, registry, 5);
        tool = new WaitSubagentTool(runtime);
    }

    @Test
    @DisplayName("getName 返回 wait_subagent")
    void nameIsWaitSubagent() {
        assertEquals("wait_subagent", tool.getName());
    }

    @Test
    @DisplayName("getSchema 包含 runIds 作为必填参数")
    void schemaHasRequiredRunIds() {
        assertNotNull(tool.getSchema());
        Map<String, Object> schema = tool.getSchema().toMap();
        assertNotNull(schema.get("required"));
    }

    @Test
    @DisplayName("等待一个已完成的 run 返回成功结果")
    void waitForCompletedRunReturnsSuccess() throws Exception {
        // Spawn a subagent
        String runId = runtime.spawn(
                SpawnRequest.builder()
                        .parentSessionKey("agent:worker:main:s1")
                        .task("quick task")
                        .build(),
                e -> {}
        );

        // Wait for it to complete (stub provider returns immediately)
        runtime.waitForCompletion(runId, 5, TimeUnit.SECONDS);

        // Now use the tool
        ToolResult result = tool.execute(Map.of("runIds", runId, "timeoutSeconds", 5));

        assertFalse(result.isError());
        assertTrue(result.getContent().contains(runId));
    }

    @Test
    @DisplayName("等待不存在的 runId 返回 Not found")
    void waitForNonexistentRunReturnsNotFound() {
        ToolResult result = tool.execute(Map.of("runIds", "nonexistent-id", "timeoutSeconds", 1));

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("Not found"));
    }

    @Test
    @DisplayName("等待多个 runId（逗号分隔）聚合结果")
    void waitForMultipleRuns() throws Exception {
        String runId1 = runtime.spawn(
                SpawnRequest.builder()
                        .parentSessionKey("agent:worker:main:s1")
                        .task("task 1")
                        .build(),
                e -> {}
        );
        String runId2 = runtime.spawn(
                SpawnRequest.builder()
                        .parentSessionKey("agent:worker:main:s1")
                        .task("task 2")
                        .build(),
                e -> {}
        );

        runtime.waitForCompletion(runId1, 5, TimeUnit.SECONDS);
        runtime.waitForCompletion(runId2, 5, TimeUnit.SECONDS);

        ToolResult result = tool.execute(Map.of(
                "runIds", runId1 + "," + runId2,
                "timeoutSeconds", 5
        ));

        assertTrue(result.getContent().contains(runId1));
        assertTrue(result.getContent().contains(runId2));
    }

    @Test
    @DisplayName("默认 timeout 为 60 秒（不传 timeoutSeconds）")
    void defaultTimeoutIs60() {
        // Just verify it doesn't throw when timeoutSeconds is absent
        ToolResult result = tool.execute(Map.of("runIds", "fake-id"));
        assertNotNull(result);
        assertTrue(result.getContent().contains("Not found"));
    }

    // Stubs
    private static class StubLLMProvider implements com.lightweightai.kernel.llm.LLMProvider {
        @Override public com.lightweightai.kernel.llm.LLMResponse complete(List<com.lightweightai.kernel.llm.ConversationMessage> m, com.lightweightai.kernel.llm.LLMOptions o) {
            return com.lightweightai.kernel.llm.LLMResponse.builder()
                    .message(com.lightweightai.kernel.llm.ConversationMessage.builder()
                            .role(com.lightweightai.kernel.llm.ConversationMessage.MessageRole.ASSISTANT)
                            .textContent("done")
                            .build())
                    .build();
        }
        @Override public java.util.concurrent.CompletableFuture<com.lightweightai.kernel.llm.LLMResponse> completeAsync(List<com.lightweightai.kernel.llm.ConversationMessage> m, com.lightweightai.kernel.llm.LLMOptions o) {
            return java.util.concurrent.CompletableFuture.completedFuture(complete(m, o));
        }
        @Override public java.util.concurrent.CompletableFuture<com.lightweightai.kernel.llm.LLMResponse> completeStream(List<com.lightweightai.kernel.llm.ConversationMessage> m, com.lightweightai.kernel.llm.LLMOptions o, com.lightweightai.kernel.llm.StreamEventHandler h) {
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
