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

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Subagent Tools — spawn / wait / list")
class SubagentToolsTest {

    private SubagentRuntime runtime;
    private List<StreamEvent> events;

    @BeforeEach
    void setUp() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("worker")
                .systemPrompt("I work")
                .maxSpawnDepth(1)
                .build());

        TestMemory memory = new TestMemory();
        ToolRegistry tools = new ToolRegistry();
        LLMProvider provider = new DelayedMockProvider(200);  // 200ms delay
        AgentFactory factory = new AgentFactory(provider, memory, tools);
        runtime = new SubagentRuntime(factory, registry, 10);
        events = java.util.Collections.synchronizedList(new ArrayList<>());
    }

    @Test
    @DisplayName("spawn_subagent 立即返回 runId")
    void spawnReturnsImmediately() {
        SpawnSubagentTool spawnTool = new SpawnSubagentTool(runtime, "agent:worker:main:s1", events::add);

        long start = System.currentTimeMillis();
        ToolResult result = spawnTool.execute(Map.of("task", "搜索文档"));
        long elapsed = System.currentTimeMillis() - start;

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("Run ID:"));
        assertTrue(elapsed < 100, "spawn should be non-blocking, took " + elapsed + "ms");
    }

    @Test
    @DisplayName("wait_subagent 阻塞等待单个 subagent 完成并返回结果")
    void waitSingleSubagent() throws InterruptedException {
        String runId = runtime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("计算 1+1")
                .agentId("worker")
                .build(), events::add);

        WaitSubagentTool waitTool = new WaitSubagentTool(runtime);
        ToolResult result = waitTool.execute(Map.of("runIds", runId, "timeoutSeconds", 10));

        assertFalse(result.isError());
        assertTrue(result.getContent().contains(runId));
    }

    @Test
    @DisplayName("wait_subagent 等待多个 subagent 全部完成")
    void waitMultipleSubagents() throws InterruptedException {
        String runId1 = runtime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("任务A")
                .agentId("worker")
                .build(), events::add);
        String runId2 = runtime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("任务B")
                .agentId("worker")
                .build(), events::add);

        WaitSubagentTool waitTool = new WaitSubagentTool(runtime);
        ToolResult result = waitTool.execute(Map.of(
                "runIds", runId1 + "," + runId2,
                "timeoutSeconds", 10));

        assertFalse(result.isError());
        assertTrue(result.getContent().contains(runId1));
        assertTrue(result.getContent().contains(runId2));
    }

    @Test
    @DisplayName("wait_subagent 超时返回错误")
    void waitTimeout() {
        // 创建一个很慢的 subagent
        AgentRegistry slowRegistry = new AgentRegistry();
        slowRegistry.register(AgentProfile.builder().agentId("slow").maxSpawnDepth(1).build());
        LLMProvider slowProvider = new DelayedMockProvider(10000);  // 10s
        AgentFactory slowFactory = new AgentFactory(slowProvider, new TestMemory(), new ToolRegistry());
        SubagentRuntime slowRuntime = new SubagentRuntime(slowFactory, slowRegistry, 5);

        String runId = slowRuntime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:slow:main:s1")
                .task("慢任务")
                .agentId("slow")
                .build(), e -> {});

        WaitSubagentTool waitTool = new WaitSubagentTool(slowRuntime);
        ToolResult result = waitTool.execute(Map.of("runIds", runId, "timeoutSeconds", 1));

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("timed out"));

        slowRuntime.stopAll();
    }

    @Test
    @DisplayName("list_subagents 返回所有活跃 subagent 状态")
    void listActiveSubagents() {
        // spawn 一个慢任务让它保持 active
        AgentRegistry reg = new AgentRegistry();
        reg.register(AgentProfile.builder().agentId("w").maxSpawnDepth(1).build());
        LLMProvider slowProvider = new DelayedMockProvider(5000);
        AgentFactory f = new AgentFactory(slowProvider, new TestMemory(), new ToolRegistry());
        SubagentRuntime r = new SubagentRuntime(f, reg, 5);

        r.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:w:main:s1").task("task1").agentId("w").build(), e -> {});
        r.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:w:main:s1").task("task2").agentId("w").build(), e -> {});

        ListSubagentsTool listTool = new ListSubagentsTool(r);
        ToolResult result = listTool.execute(Map.of());

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("task1"));
        assertTrue(result.getContent().contains("task2"));

        r.stopAll();
    }

    // ==================== Helpers ====================

    private static class DelayedMockProvider implements LLMProvider {
        private final long delayMs;
        DelayedMockProvider(long delayMs) { this.delayMs = delayMs; }

        @Override
        public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
            return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent("done after " + delayMs + "ms").build())
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
        @Override public String getProviderName() { return "delayed"; }
    }

    private static class TestMemory implements MemoryProvider {
        @Override public void addMessage(String s, Message m) {}
        @Override public List<Message> getHistory(String s, int l) { return List.of(); }
        @Override public void clearSession(String s) {}
        @Override public void writeEphemeral(String c) {}
        @Override public void writeDurable(String s, String c) {}
        @Override public List<MemorySearchResult> search(String q) { return List.of(); }
    }
}
