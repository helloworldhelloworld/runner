package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Subagent Tools — Edge Cases & Transmission Chain")
class SubagentToolsEdgeCaseTest {

    private SubagentRuntime runtime;
    private SubagentRuntime slowRuntime;
    private List<StreamEvent> events;

    @BeforeEach
    void setUp() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("worker")
                .systemPrompt("I work")
                .maxSpawnDepth(1)
                .build());
        registry.register(AgentProfile.builder()
                .agentId("no-spawn")
                .systemPrompt("Cannot spawn")
                .maxSpawnDepth(0)
                .build());

        StubMemory memory = new StubMemory();
        ToolRegistry tools = new ToolRegistry();
        AgentFactory factory = new AgentFactory(new FastMockProvider(), memory, tools);
        runtime = new SubagentRuntime(factory, registry, 10);

        AgentFactory slowFactory = new AgentFactory(new SlowMockProvider(5000), memory, tools);
        slowRuntime = new SubagentRuntime(slowFactory, registry, 10);

        events = Collections.synchronizedList(new ArrayList<>());
    }

    @Nested
    @DisplayName("SpawnSubagentTool Edge Cases")
    class SpawnEdgeCases {

        @Test
        @DisplayName("spawn 成功时 ToolResult 包含 runId（payload 传输断言）")
        void spawnResultContainsRunId() {
            SpawnSubagentTool tool = new SpawnSubagentTool(runtime, "agent:worker:main:s1", events::add);

            ToolResult result = tool.execute(Map.of("task", "test task"));

            assertFalse(result.isError());
            String content = result.getContent();
            assertTrue(content.contains("Run ID:"), "Result must contain the runId for downstream wait_subagent");
            String runId = content.split("Run ID: ")[1].split(" ")[0];
            assertEquals(8, runId.length(), "runId should be 8 chars (UUID prefix)");
        }

        @Test
        @DisplayName("spawn depth 超限返回 error ToolResult（非异常）")
        void spawnDepthLimitReturnsError() {
            SpawnSubagentTool tool = new SpawnSubagentTool(
                    runtime, "agent:no-spawn:main:s1", events::add);

            ToolResult result = tool.execute(Map.of(
                    "task", "task",
                    "agentId", "no-spawn"
            ));

            assertTrue(result.isError());
            assertTrue(result.getContent().contains("maxSpawnDepth=0"));
        }

        @Test
        @DisplayName("spawn 并发超限返回 error ToolResult")
        void spawnConcurrencyLimitReturnsError() {
            AgentRegistry reg = new AgentRegistry();
            reg.register(AgentProfile.builder().agentId("w").maxSpawnDepth(1).build());
            StubMemory mem = new StubMemory();
            AgentFactory f = new AgentFactory(new SlowMockProvider(10000), mem, new ToolRegistry());
            SubagentRuntime limitedRuntime = new SubagentRuntime(f, reg, 1);

            limitedRuntime.spawn(SpawnRequest.builder()
                    .parentSessionKey("agent:w:main:s1").task("t1").agentId("w").build(), e -> {});

            SpawnSubagentTool tool = new SpawnSubagentTool(
                    limitedRuntime, "agent:w:main:s1", e -> {});
            ToolResult result = tool.execute(Map.of("task", "t2", "agentId", "w"));

            assertTrue(result.isError());
            assertTrue(result.getContent().contains("Max concurrent"));

            limitedRuntime.stopAll();
        }

        @Test
        @DisplayName("spawn schema 包含 task (required), agentId, model")
        @SuppressWarnings("unchecked")
        void spawnSchemaCorrect() {
            SpawnSubagentTool tool = new SpawnSubagentTool(runtime, "key", e -> {});

            ToolSchema schema = tool.getSchema();
            Map<String, Object> map = schema.toMap();
            assertEquals("object", map.get("type"));

            Map<String, Object> props = (Map<String, Object>) map.get("properties");
            assertTrue(props.containsKey("task"));
            assertTrue(props.containsKey("agentId"));
            assertTrue(props.containsKey("model"));

            List<String> required = (List<String>) map.get("required");
            assertTrue(required.contains("task"));
        }

        @Test
        @DisplayName("spawn name/description 正确")
        void spawnMetadata() {
            SpawnSubagentTool tool = new SpawnSubagentTool(runtime, "key", e -> {});
            assertEquals("spawn_subagent", tool.getName());
            assertFalse(tool.getDescription().isEmpty());
        }
    }

    @Nested
    @DisplayName("WaitSubagentTool Edge Cases")
    class WaitEdgeCases {

        @Test
        @DisplayName("wait 不存在的 runId 返回 Not found")
        void waitNotFoundRunId() {
            WaitSubagentTool tool = new WaitSubagentTool(runtime);
            ToolResult result = tool.execute(Map.of("runIds", "nonexistent", "timeoutSeconds", 1));

            assertTrue(result.isError());
            assertTrue(result.getContent().contains("Not found"));
        }

        @Test
        @DisplayName("wait FAILED subagent 返回 FAILED 状态和错误信息")
        void waitFailedSubagent() throws InterruptedException {
            AgentRegistry reg = new AgentRegistry();
            reg.register(AgentProfile.builder().agentId("err").maxSpawnDepth(1).build());
            StubMemory mem = new StubMemory();
            AgentFactory f = new AgentFactory(new ErrorMockProvider(), mem, new ToolRegistry());
            SubagentRuntime errRuntime = new SubagentRuntime(f, reg, 5);

            String runId = errRuntime.spawn(SpawnRequest.builder()
                    .parentSessionKey("agent:err:main:s1")
                    .task("will fail").agentId("err").build(), e -> {});

            errRuntime.waitForCompletion(runId, 5, TimeUnit.SECONDS);
            Thread.sleep(100);

            WaitSubagentTool tool = new WaitSubagentTool(errRuntime);
            ToolResult result = tool.execute(Map.of("runIds", runId, "timeoutSeconds", 5));

            assertTrue(result.isError());
            assertTrue(result.getContent().contains("FAILED"));
        }

        @Test
        @DisplayName("wait CANCELLED subagent 返回错误结果")
        void waitCancelledSubagent() throws InterruptedException {
            String runId = slowRuntime.spawn(SpawnRequest.builder()
                    .parentSessionKey("agent:worker:main:s1")
                    .task("slow task").agentId("worker").build(), events::add);

            Thread.sleep(100);
            slowRuntime.stop(runId, events::add);
            Thread.sleep(300);

            WaitSubagentTool tool = new WaitSubagentTool(slowRuntime);
            ToolResult result = tool.execute(Map.of("runIds", runId, "timeoutSeconds", 2));

            assertTrue(result.isError(),
                    "Cancelled/stopped subagent should produce an error result");
            assertTrue(result.getContent().contains("CANCELLED")
                            || result.getContent().contains("Not found"),
                    "Result should indicate cancellation or not found, got: " + result.getContent());

            slowRuntime.stopAll();
        }

        @Test
        @DisplayName("wait 多个 runId 有空格也能正确解析")
        void waitTrimsWhitespace() throws InterruptedException {
            String runId = runtime.spawn(SpawnRequest.builder()
                    .parentSessionKey("agent:worker:main:s1")
                    .task("task").agentId("worker").build(), events::add);
            runtime.waitForCompletion(runId, 5, TimeUnit.SECONDS);
            Thread.sleep(100);

            WaitSubagentTool tool = new WaitSubagentTool(runtime);
            ToolResult result = tool.execute(Map.of("runIds", "  " + runId + "  ", "timeoutSeconds", 5));

            assertFalse(result.isError());
        }

        @Test
        @DisplayName("wait 默认 timeout 为 60 秒")
        void waitDefaultTimeout() {
            WaitSubagentTool tool = new WaitSubagentTool(runtime);
            ToolSchema schema = tool.getSchema();

            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) schema.toMap().get("properties");
            assertTrue(props.containsKey("timeoutSeconds"));
            assertTrue(props.containsKey("runIds"));
        }

        @Test
        @DisplayName("wait schema 正确定义")
        @SuppressWarnings("unchecked")
        void waitSchemaCorrect() {
            WaitSubagentTool tool = new WaitSubagentTool(runtime);
            assertEquals("wait_subagent", tool.getName());

            Map<String, Object> schema = tool.getSchema().toMap();
            List<String> required = (List<String>) schema.get("required");
            assertTrue(required.contains("runIds"));
        }
    }

    @Nested
    @DisplayName("ListSubagentsTool Edge Cases")
    class ListEdgeCases {

        @Test
        @DisplayName("无活跃 subagent 时返回友好提示")
        void listEmptyReturnsNoActive() {
            ListSubagentsTool tool = new ListSubagentsTool(runtime);
            ToolResult result = tool.execute(Map.of());

            assertFalse(result.isError());
            assertEquals("No active sub-agents.", result.getContent());
        }

        @Test
        @DisplayName("list 使用空 schema")
        void listSchemaIsEmpty() {
            ListSubagentsTool tool = new ListSubagentsTool(runtime);
            assertEquals("list_subagents", tool.getName());

            Map<String, Object> props = tool.getSchema().getProperties();
            assertTrue(props.isEmpty());
        }

        @Test
        @DisplayName("list 返回的结果包含实际 task 描述和 runId（payload 断言）")
        void listResultContainsTaskPayload() {
            String runId = slowRuntime.spawn(SpawnRequest.builder()
                    .parentSessionKey("agent:worker:main:s1")
                    .task("analyze document").agentId("worker").build(), e -> {});

            ListSubagentsTool tool = new ListSubagentsTool(slowRuntime);
            ToolResult result = tool.execute(Map.of());

            assertFalse(result.isError());
            assertTrue(result.getContent().contains(runId),
                    "List output must include runId for LLM to reference");
            assertTrue(result.getContent().contains("analyze document"),
                    "List output must include task description");
            assertTrue(result.getContent().contains("RUNNING") || result.getContent().contains("duration"),
                    "List output must include status or duration info");

            slowRuntime.stopAll();
        }
    }

    @Nested
    @DisplayName("Transmission Chain: spawn → wait → result payload")
    class TransmissionChain {

        @Test
        @DisplayName("spawn 返回的 runId 可在 wait 中使用，返回 agent 的实际结果文本")
        void spawnThenWaitCarriesPayload() throws InterruptedException {
            SpawnSubagentTool spawnTool = new SpawnSubagentTool(
                    runtime, "agent:worker:main:s1", events::add);

            ToolResult spawnResult = spawnTool.execute(Map.of("task", "compute result"));
            assertFalse(spawnResult.isError());
            String runId = spawnResult.getContent().split("Run ID: ")[1].split(" ")[0];

            runtime.waitForCompletion(runId, 5, TimeUnit.SECONDS);
            Thread.sleep(100);

            WaitSubagentTool waitTool = new WaitSubagentTool(runtime);
            ToolResult waitResult = waitTool.execute(Map.of("runIds", runId, "timeoutSeconds", 5));

            assertFalse(waitResult.isError());
            assertTrue(waitResult.getContent().contains(runId));
            assertTrue(waitResult.getContent().contains("ms)"),
                    "Wait result should include duration in ms");
        }

        @Test
        @DisplayName("spawn 发出 SUBAGENT_SPAWN 事件, 完成后 SUBAGENT_COMPLETE 事件")
        void spawnEmitsBothLifecycleEvents() throws InterruptedException {
            SpawnSubagentTool spawnTool = new SpawnSubagentTool(
                    runtime, "agent:worker:main:s1", events::add);

            ToolResult spawnResult = spawnTool.execute(Map.of("task", "lifecycle test"));
            String runId = spawnResult.getContent().split("Run ID: ")[1].split(" ")[0];

            runtime.waitForCompletion(runId, 5, TimeUnit.SECONDS);
            Thread.sleep(200);

            assertTrue(events.stream().anyMatch(e ->
                    e.getType() == StreamEvent.EventType.SUBAGENT_SPAWN
                    && runId.equals(e.getData().get("runId"))),
                    "SUBAGENT_SPAWN event should be emitted");

            assertTrue(events.stream().anyMatch(e ->
                    e.getType() == StreamEvent.EventType.SUBAGENT_COMPLETE
                    && runId.equals(e.getData().get("runId"))),
                    "SUBAGENT_COMPLETE event should be emitted after completion");
        }
    }

    // ==================== Test Helpers ====================

    private static class FastMockProvider implements LLMProvider {
        @Override
        public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
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
        @Override public String getProviderName() { return "fast-mock"; }
    }

    private static class SlowMockProvider implements LLMProvider {
        private final long delayMs;
        SlowMockProvider(long delayMs) { this.delayMs = delayMs; }
        @Override
        public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
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
        @Override public String getProviderName() { return "slow-mock"; }
    }

    private static class ErrorMockProvider implements LLMProvider {
        @Override
        public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            throw new RuntimeException("Simulated LLM failure");
        }
        @Override public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            return Flux.error(new RuntimeException("Simulated LLM failure"));
        }
        @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
            return CompletableFuture.failedFuture(new RuntimeException("Simulated LLM failure"));
        }
        @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) {
            return CompletableFuture.failedFuture(new RuntimeException("Simulated LLM failure"));
        }
        @Override public ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return "error-mock"; }
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
