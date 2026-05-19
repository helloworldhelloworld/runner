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

@DisplayName("WaitSubagentTool — 单元测试")
class WaitSubagentToolTest {

    private SubagentRuntime runtime;

    @BeforeEach
    void setUp() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("worker")
                .systemPrompt("I work")
                .maxSpawnDepth(1)
                .build());

        LLMProvider provider = new ImmediateMockProvider();
        AgentFactory factory = new AgentFactory(provider, new StubMemory(), new ToolRegistry());
        runtime = new SubagentRuntime(factory, registry, 10);
    }

    @Test
    @DisplayName("getName 返回 wait_subagent")
    void nameIsCorrect() {
        WaitSubagentTool tool = new WaitSubagentTool(runtime);
        assertEquals("wait_subagent", tool.getName());
    }

    @Test
    @DisplayName("getSchema 要求 runIds 为 required")
    void schemaRequiresRunIds() {
        WaitSubagentTool tool = new WaitSubagentTool(runtime);
        Map<String, Object> schema = tool.getSchema().toMap();
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertNotNull(required);
        assertTrue(required.contains("runIds"));
    }

    @Test
    @DisplayName("等待已完成的 subagent 返回成功结果")
    void waitCompletedSubagent() throws InterruptedException {
        String runId = spawnAndWaitForCompletion();

        WaitSubagentTool tool = new WaitSubagentTool(runtime);
        ToolResult result = tool.execute(Map.of("runIds", runId, "timeoutSeconds", 5));

        assertFalse(result.isError(), "Completed subagent should yield success result");
        assertTrue(result.getContent().contains(runId));
        assertTrue(result.getContent().contains("ms"),
                "Result should contain duration in ms");
    }

    @Test
    @DisplayName("等待不存在的 runId 返回 Not found 错误")
    void waitNonExistentRunId() {
        WaitSubagentTool tool = new WaitSubagentTool(runtime);
        ToolResult result = tool.execute(Map.of(
                "runIds", "nonexistent-id",
                "timeoutSeconds", 1));

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("Not found"));
    }

    @Test
    @DisplayName("等待超时的 subagent 返回 timed out 错误")
    void waitTimedOutSubagent() {
        AgentRegistry reg = new AgentRegistry();
        reg.register(AgentProfile.builder().agentId("slow").maxSpawnDepth(1).build());
        LLMProvider slowProvider = new SlowMockProvider(10000);
        AgentFactory f = new AgentFactory(slowProvider, new StubMemory(), new ToolRegistry());
        SubagentRuntime slowRuntime = new SubagentRuntime(f, reg, 5);

        String runId = slowRuntime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:slow:main:s1")
                .task("slow task")
                .agentId("slow")
                .build(), e -> {});

        WaitSubagentTool tool = new WaitSubagentTool(slowRuntime);
        ToolResult result = tool.execute(Map.of("runIds", runId, "timeoutSeconds", 1));

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("timed out"));

        slowRuntime.stopAll();
    }

    @Test
    @DisplayName("默认 timeout 为 60 秒（不传 timeoutSeconds）")
    void defaultTimeoutIs60() throws InterruptedException {
        String runId = spawnAndWaitForCompletion();

        WaitSubagentTool tool = new WaitSubagentTool(runtime);
        ToolResult result = tool.execute(Map.of("runIds", runId));

        assertFalse(result.isError(), "Should succeed with default timeout");
    }

    @Test
    @DisplayName("等待多个逗号分隔的 runIds 全部完成")
    void waitMultipleRunIds() throws InterruptedException {
        String runId1 = spawnAndWaitForCompletion();
        String runId2 = spawnAndWaitForCompletion();

        WaitSubagentTool tool = new WaitSubagentTool(runtime);
        ToolResult result = tool.execute(Map.of(
                "runIds", runId1 + " , " + runId2,
                "timeoutSeconds", 5));

        assertFalse(result.isError());
        assertTrue(result.getContent().contains(runId1));
        assertTrue(result.getContent().contains(runId2));
    }

    @Test
    @DisplayName("等待已取消的 subagent 返回 CANCELLED 信息")
    void waitCancelledSubagent() throws InterruptedException {
        AgentRegistry reg = new AgentRegistry();
        reg.register(AgentProfile.builder().agentId("w").maxSpawnDepth(1).build());
        LLMProvider slowProvider = new SlowMockProvider(5000);
        AgentFactory f = new AgentFactory(slowProvider, new StubMemory(), new ToolRegistry());
        SubagentRuntime rt = new SubagentRuntime(f, reg, 5);

        String runId = rt.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:w:main:s1")
                .task("task")
                .agentId("w")
                .build(), e -> {});

        Thread.sleep(50);
        rt.stop(runId, e -> {});
        Thread.sleep(200);

        WaitSubagentTool tool = new WaitSubagentTool(rt);
        ToolResult result = tool.execute(Map.of("runIds", runId, "timeoutSeconds", 2));

        assertTrue(result.getContent().contains("CANCELLED"),
                "Should show CANCELLED status, got: " + result.getContent());

        rt.stopAll();
    }

    // ==================== Helpers ====================

    private String spawnAndWaitForCompletion() throws InterruptedException {
        String runId = runtime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("quick task")
                .agentId("worker")
                .build(), e -> {});
        assertTrue(runtime.waitForCompletion(runId, 5, TimeUnit.SECONDS));
        return runId;
    }

    private static class ImmediateMockProvider implements LLMProvider {
        @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
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
        @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
            return LLMResponse.builder().stopReason("end_turn")
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent("done").build())
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
