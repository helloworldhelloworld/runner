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

@DisplayName("WaitSubagentTool - 等待 subagent 完成并返回聚合结果")
class WaitSubagentToolTest {

    private SubagentRuntime runtime;
    private WaitSubagentTool tool;

    @BeforeEach
    void setUp() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("worker")
                .systemPrompt("worker")
                .maxSpawnDepth(2)
                .build());
        registry.setDefault("worker");

        LLMProvider provider = new FastMockProvider();
        AgentFactory factory = new AgentFactory(provider, new StubMemory(), new ToolRegistry());
        runtime = new SubagentRuntime(factory, registry, 5);
        tool = new WaitSubagentTool(runtime);
    }

    @Test
    @DisplayName("工具名称为 wait_subagent")
    void toolName() {
        assertEquals("wait_subagent", tool.getName());
    }

    @Test
    @DisplayName("schema 包含 runIds 为必填参数")
    void schemaHasRequiredRunIds() {
        ToolSchema schema = tool.getSchema();
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.toMap().get("properties");
        assertTrue(props.containsKey("runIds"));
        assertTrue(props.containsKey("timeoutSeconds"));
    }

    @Test
    @DisplayName("等待已完成的 subagent 返回成功结果")
    void waitForCompletedSubagent() throws InterruptedException {
        String runId = spawnAndWaitForCompletion();

        ToolResult result = tool.execute(Map.of("runIds", runId));

        assertFalse(result.isError());
        assertTrue(result.getContent().contains(runId));
        assertTrue(result.getContent().contains("ms)"));
    }

    @Test
    @DisplayName("等待多个 subagent（逗号分隔）")
    void waitForMultipleSubagents() throws InterruptedException {
        String runId1 = spawnAndWaitForCompletion();
        String runId2 = spawnAndWaitForCompletion();

        ToolResult result = tool.execute(Map.of("runIds", runId1 + "," + runId2));

        assertFalse(result.isError());
        assertTrue(result.getContent().contains(runId1));
        assertTrue(result.getContent().contains(runId2));
    }

    @Test
    @DisplayName("不存在的 runId 返回 Not found")
    void unknownRunIdReturnsNotFound() {
        ToolResult result = tool.execute(Map.of("runIds", "nonexistent-id"));

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("Not found"));
    }

    @Test
    @DisplayName("超时的 subagent 返回 timed out")
    void timedOutSubagentReportsTimeout() {
        LLMProvider slowProvider = new SlowMockProvider(10000);
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("slow")
                .systemPrompt("slow")
                .maxSpawnDepth(2)
                .build());
        registry.setDefault("slow");

        AgentFactory slowFactory = new AgentFactory(slowProvider, new StubMemory(), new ToolRegistry());
        SubagentRuntime slowRuntime = new SubagentRuntime(slowFactory, registry, 5);
        WaitSubagentTool slowTool = new WaitSubagentTool(slowRuntime);

        String runId = slowRuntime.spawn(
                SpawnRequest.builder()
                        .parentSessionKey("agent:slow:main:s1")
                        .task("slow task")
                        .agentId("slow")
                        .build(),
                e -> {});

        ToolResult result = slowTool.execute(Map.of(
                "runIds", runId,
                "timeoutSeconds", 1
        ));

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("timed out"));

        slowRuntime.stopAll();
    }

    @Test
    @DisplayName("默认超时为 60 秒")
    void defaultTimeoutIs60Seconds() {
        assertDoesNotThrow(() -> {
            ToolSchema schema = tool.getSchema();
            assertNotNull(schema);
        });
    }

    private String spawnAndWaitForCompletion() throws InterruptedException {
        String runId = runtime.spawn(
                SpawnRequest.builder()
                        .parentSessionKey("agent:worker:main:s1")
                        .task("quick task")
                        .agentId("worker")
                        .build(),
                e -> {});
        assertTrue(runtime.waitForCompletion(runId, 5, TimeUnit.SECONDS));
        Thread.sleep(100);
        return runId;
    }

    private static class FastMockProvider implements LLMProvider {
        @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent("done").build())
                    .stopReason("end_turn").build();
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
        @Override public String getProviderName() { return "fast-mock"; }
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
