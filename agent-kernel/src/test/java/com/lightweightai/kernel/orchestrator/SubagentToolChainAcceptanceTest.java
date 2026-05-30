package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端验收测试：spawn → wait → list 完整工具链传输链路
 *
 * 验证核心 payload 传递：
 * 1. SpawnSubagentTool.execute() → SubagentRuntime.spawn() → runId 返回
 * 2. 子 agent 运行后结果通过 SubagentRun.complete() 存储
 * 3. WaitSubagentTool.execute(runId) → 拿到子 agent 的 AgentResponse 文本
 * 4. 事件流包含完整 SPAWN → COMPLETE 生命周期
 */
@DisplayName("Subagent 工具链端到端验收测试 — 传输链路 payload 验证")
class SubagentToolChainAcceptanceTest {

    private SubagentRuntime runtime;
    private List<StreamEvent> capturedEvents;
    private SpawnSubagentTool spawnTool;
    private WaitSubagentTool waitTool;
    private ListSubagentsTool listTool;

    @BeforeEach
    void setUp() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("worker")
                .systemPrompt("I am a worker")
                .maxSpawnDepth(2)
                .build());

        PayloadCapturingProvider provider = new PayloadCapturingProvider("task result payload");
        AgentFactory factory = new AgentFactory(provider, new SpawnSubagentToolTest.StubMemory(), new ToolRegistry());
        runtime = new SubagentRuntime(factory, registry, 10);
        capturedEvents = java.util.Collections.synchronizedList(new ArrayList<>());

        spawnTool = new SpawnSubagentTool(runtime, "agent:worker:main:sess1", capturedEvents::add);
        waitTool = new WaitSubagentTool(runtime);
        listTool = new ListSubagentsTool(runtime);
    }

    @AfterEach
    void tearDown() {
        runtime.shutdown();
    }

    @Test
    @DisplayName("spawn → wait 完整链路: runId 传递 + 子 agent 结果文本到达 wait 输出")
    void spawnThenWaitCarriesResultPayload() throws InterruptedException {
        ToolResult spawnResult = spawnTool.execute(Map.of("task", "analyze data", "agentId", "worker"));
        assertFalse(spawnResult.isError(), "spawn should succeed");

        String runId = extractRunId(spawnResult.getContent());
        assertNotNull(runId, "runId should be extractable from spawn result");

        Thread.sleep(400);

        ToolResult waitResult = waitTool.execute(Map.of("runIds", runId, "timeoutSeconds", 10));
        assertFalse(waitResult.isError(), "wait should succeed: " + waitResult.getContent());
        assertTrue(waitResult.getContent().contains(runId),
                "wait output should reference runId");
        assertTrue(waitResult.getContent().contains("task result payload"),
                "wait output must carry the actual LLM response text from the subagent — " +
                "this proves the transmission chain: LLMProvider → AgentLoop.run() → " +
                "SubagentRun.complete() → WaitSubagentTool.execute(). Actual: " + waitResult.getContent());
    }

    @Test
    @DisplayName("spawn 后 list 看到活跃 subagent 的 task 描述")
    void spawnThenListShowsActiveTask() {
        AgentRegistry reg = new AgentRegistry();
        reg.register(AgentProfile.builder()
                .agentId("slow").systemPrompt("slow").maxSpawnDepth(1).build());
        SubagentRuntime slowRuntime = new SubagentRuntime(
                new AgentFactory(new SpawnSubagentToolTest.SlowProvider(5000),
                        new SpawnSubagentToolTest.StubMemory(), new ToolRegistry()),
                reg, 5);

        SpawnSubagentTool sp = new SpawnSubagentTool(slowRuntime, "agent:slow:main:s1", e -> {});
        ListSubagentsTool ls = new ListSubagentsTool(slowRuntime);

        sp.execute(Map.of("task", "slow analysis"));
        ToolResult listResult = ls.execute(Map.of());

        assertFalse(listResult.isError());
        assertTrue(listResult.getContent().contains("slow analysis"),
                "list should show the spawned task description");

        slowRuntime.stopAll();
        slowRuntime.shutdown();
    }

    @Test
    @DisplayName("事件流包含 SUBAGENT_SPAWN + SUBAGENT_COMPLETE 生命周期事件")
    void eventLifecycleSpawnToComplete() throws InterruptedException {
        spawnTool.execute(Map.of("task", "lifecycle test"));
        Thread.sleep(500);

        assertTrue(capturedEvents.stream()
                        .anyMatch(e -> e.getType() == StreamEvent.EventType.SUBAGENT_SPAWN),
                "should emit SUBAGENT_SPAWN event");
        assertTrue(capturedEvents.stream()
                        .anyMatch(e -> e.getType() == StreamEvent.EventType.SUBAGENT_COMPLETE),
                "should emit SUBAGENT_COMPLETE event after subagent finishes");
    }

    @Test
    @DisplayName("spawn 两个 → wait 两个 → 结果包含两个 runId")
    void spawnTwoWaitBoth() throws InterruptedException {
        ToolResult spawn1 = spawnTool.execute(Map.of("task", "task-A"));
        ToolResult spawn2 = spawnTool.execute(Map.of("task", "task-B"));

        String runId1 = extractRunId(spawn1.getContent());
        String runId2 = extractRunId(spawn2.getContent());

        Thread.sleep(500);

        ToolResult waitResult = waitTool.execute(Map.of(
                "runIds", runId1 + "," + runId2,
                "timeoutSeconds", 10));

        assertFalse(waitResult.isError());
        assertTrue(waitResult.getContent().contains(runId1));
        assertTrue(waitResult.getContent().contains(runId2));
    }

    private String extractRunId(String content) {
        int idx = content.indexOf("Run ID: ");
        if (idx == -1) return null;
        String after = content.substring(idx + 8);
        int end = after.indexOf(' ');
        return end > 0 ? after.substring(0, end) : after.trim();
    }

    // ==================== Payload-capturing provider ====================

    private static class PayloadCapturingProvider implements LLMProvider {
        private final String responseText;

        PayloadCapturingProvider(String responseText) {
            this.responseText = responseText;
        }

        @Override
        public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent(responseText).build())
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
        @Override public String getProviderName() { return "capturing"; }
    }
}
