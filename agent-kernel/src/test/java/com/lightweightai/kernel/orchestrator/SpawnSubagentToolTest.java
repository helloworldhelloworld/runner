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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SpawnSubagentTool — 单元测试")
class SpawnSubagentToolTest {

    private SubagentRuntime runtime;
    private List<StreamEvent> capturedEvents;

    @BeforeEach
    void setUp() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("worker")
                .systemPrompt("I work")
                .maxSpawnDepth(1)
                .build());

        ToolRegistry tools = new ToolRegistry();
        LLMProvider provider = new ImmediateMockProvider();
        AgentFactory factory = new AgentFactory(provider, new StubMemory(), tools);
        runtime = new SubagentRuntime(factory, registry, 10);
        capturedEvents = java.util.Collections.synchronizedList(new ArrayList<>());
    }

    @Test
    @DisplayName("getName 返回 spawn_subagent")
    void nameIsCorrect() {
        SpawnSubagentTool tool = new SpawnSubagentTool(runtime, "key", e -> {});
        assertEquals("spawn_subagent", tool.getName());
    }

    @Test
    @DisplayName("getDescription 非空")
    void descriptionNotEmpty() {
        SpawnSubagentTool tool = new SpawnSubagentTool(runtime, "key", e -> {});
        assertNotNull(tool.getDescription());
        assertFalse(tool.getDescription().isEmpty());
    }

    @Test
    @DisplayName("getSchema 包含 task 为 required")
    void schemaRequiresTask() {
        SpawnSubagentTool tool = new SpawnSubagentTool(runtime, "key", e -> {});
        Map<String, Object> schema = tool.getSchema().toMap();
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertNotNull(required);
        assertTrue(required.contains("task"));
    }

    @Test
    @DisplayName("execute 成功返回包含 Run ID 的结果")
    void executeReturnsRunId() {
        SpawnSubagentTool tool = new SpawnSubagentTool(
                runtime, "agent:worker:main:s1", capturedEvents::add);

        ToolResult result = tool.execute(Map.of("task", "do work"));

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("Run ID:"));
    }

    @Test
    @DisplayName("execute 将 agentId 和 model 传给 SpawnRequest")
    void executePassesOptionalArgs() {
        SpawnSubagentTool tool = new SpawnSubagentTool(
                runtime, "agent:worker:main:s1", capturedEvents::add);

        ToolResult result = tool.execute(Map.of(
                "task", "search docs",
                "agentId", "worker",
                "model", "claude-3"));

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("Run ID:"));
    }

    @Test
    @DisplayName("execute 将 announcer 回调传给 runtime → SUBAGENT_SPAWN 事件被捕获")
    void executeUsesAnnouncerCallback() throws InterruptedException {
        SpawnSubagentTool tool = new SpawnSubagentTool(
                runtime, "agent:worker:main:s1", capturedEvents::add);

        ToolResult result = tool.execute(Map.of("task", "work task"));
        assertFalse(result.isError());

        String runId = result.getContent().replaceAll(".*Run ID: ", "").split(" ")[0];
        runtime.waitForCompletion(runId, 5, java.util.concurrent.TimeUnit.SECONDS);
        Thread.sleep(100);

        assertTrue(capturedEvents.stream().anyMatch(
                e -> e.getType() == StreamEvent.EventType.SUBAGENT_SPAWN),
                "Should have received SUBAGENT_SPAWN event");
    }

    @Test
    @DisplayName("execute 在 runtime.spawn 抛异常时返回 error ToolResult")
    void executeReturnsErrorOnSpawnFailure() {
        AgentRegistry reg = new AgentRegistry();
        reg.register(AgentProfile.builder()
                .agentId("no-spawn")
                .maxSpawnDepth(0)
                .build());
        LLMProvider p = new ImmediateMockProvider();
        AgentFactory f = new AgentFactory(p, new StubMemory(), new ToolRegistry());
        SubagentRuntime failRuntime = new SubagentRuntime(f, reg, 5);

        SpawnSubagentTool tool = new SpawnSubagentTool(
                failRuntime, "agent:no-spawn:main:s1", e -> {});

        ToolResult result = tool.execute(Map.of("task", "will fail", "agentId", "no-spawn"));
        assertTrue(result.isError());
        assertTrue(result.getContent().contains("Failed to spawn"));
    }

    @Test
    @DisplayName("execute 只传 task（agentId、model 为 null）不抛异常")
    void executeWithOnlyTask() {
        SpawnSubagentTool tool = new SpawnSubagentTool(
                runtime, "agent:worker:main:s1", capturedEvents::add);

        ToolResult result = tool.execute(Map.of("task", "minimal task"));
        assertFalse(result.isError());
    }

    // ==================== Helpers ====================

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

    private static class StubMemory implements MemoryProvider {
        @Override public void addMessage(String s, Message m) {}
        @Override public List<Message> getHistory(String s, int l) { return List.of(); }
        @Override public void clearSession(String s) {}
        @Override public void writeEphemeral(String c) {}
        @Override public void writeDurable(String s, String c) {}
        @Override public List<MemorySearchResult> search(String q) { return List.of(); }
    }
}
