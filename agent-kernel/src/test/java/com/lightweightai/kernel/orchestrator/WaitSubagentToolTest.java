package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.agent.AgentResponse;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WaitSubagentTool — 单元测试")
class WaitSubagentToolTest {

    private SubagentRuntime fastRuntime;
    private SubagentRuntime slowRuntime;
    private SubagentRuntime failRuntime;

    @BeforeEach
    void setUp() {
        fastRuntime = createRuntime(new ImmediateProvider(), 10);
        slowRuntime = createRuntime(new SlowProvider(10000), 10);
        failRuntime = createRuntime(new FailingProvider(), 10);
    }

    @AfterEach
    void tearDown() {
        fastRuntime.shutdown();
        slowRuntime.shutdown();
        failRuntime.shutdown();
    }

    @Nested
    @DisplayName("Schema 验证")
    class SchemaTests {

        @Test
        @DisplayName("getName 返回 wait_subagent")
        void nameIsWaitSubagent() {
            assertEquals("wait_subagent", new WaitSubagentTool(fastRuntime).getName());
        }

        @Test
        @DisplayName("schema 包含 runIds(required) 和 timeoutSeconds(optional)")
        void schemaCorrect() {
            Map<String, Object> schema = new WaitSubagentTool(fastRuntime).getSchema().toMap();

            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            assertTrue(props.containsKey("runIds"));
            assertTrue(props.containsKey("timeoutSeconds"));

            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) schema.get("required");
            assertEquals(List.of("runIds"), required);
        }
    }

    @Nested
    @DisplayName("execute — 成功完成")
    class CompletedRuns {

        @Test
        @DisplayName("等待已完成的 subagent 返回 success 含结果文本和耗时")
        void waitCompletedRun() throws InterruptedException {
            String runId = spawnAndWaitCompletion(fastRuntime);

            WaitSubagentTool waitTool = new WaitSubagentTool(fastRuntime);
            ToolResult result = waitTool.execute(Map.of("runIds", runId, "timeoutSeconds", 5));

            assertFalse(result.isError(), "已完成的 run 应返回 success");
            assertTrue(result.getContent().contains(runId),
                    "结果应包含 runId: " + result.getContent());
            assertTrue(result.getContent().contains("ms"),
                    "结果应包含耗时: " + result.getContent());
        }

        @Test
        @DisplayName("等待多个已完成的 subagent 全部返回")
        void waitMultipleCompleted() throws InterruptedException {
            String runId1 = spawnAndWaitCompletion(fastRuntime);
            String runId2 = spawnAndWaitCompletion(fastRuntime);

            WaitSubagentTool waitTool = new WaitSubagentTool(fastRuntime);
            ToolResult result = waitTool.execute(Map.of(
                    "runIds", runId1 + ", " + runId2,
                    "timeoutSeconds", 5));

            assertFalse(result.isError());
            assertTrue(result.getContent().contains(runId1));
            assertTrue(result.getContent().contains(runId2));
        }
    }

    @Nested
    @DisplayName("execute — 失败路径")
    class FailedRuns {

        @Test
        @DisplayName("run 失败时返回 FAILED 和错误信息")
        void waitFailedRun() throws InterruptedException {
            String runId = failRuntime.spawn(SpawnRequest.builder()
                    .parentSessionKey("agent:w:main:s1")
                    .task("will fail")
                    .agentId("worker")
                    .build(), e -> {});

            failRuntime.waitForCompletion(runId, 5, TimeUnit.SECONDS);

            WaitSubagentTool waitTool = new WaitSubagentTool(failRuntime);
            ToolResult result = waitTool.execute(Map.of("runIds", runId, "timeoutSeconds", 5));

            assertTrue(result.getContent().contains("FAILED") || result.getContent().contains("fail"),
                    "结果应指示失败: " + result.getContent());
        }

        @Test
        @DisplayName("run 被取消时返回 CANCELLED")
        void waitCancelledRun() throws InterruptedException {
            String runId = slowRuntime.spawn(SpawnRequest.builder()
                    .parentSessionKey("agent:w:main:s1")
                    .task("will cancel")
                    .agentId("worker")
                    .build(), e -> {});

            slowRuntime.stop(runId);
            Thread.sleep(100);

            WaitSubagentTool waitTool = new WaitSubagentTool(slowRuntime);
            ToolResult result = waitTool.execute(Map.of("runIds", runId, "timeoutSeconds", 2));

            String content = result.getContent();
            assertTrue(content.contains("CANCELLED") || content.contains("Not found"),
                    "结果应指示已取消或已移除: " + content);
        }

        @Test
        @DisplayName("不存在的 runId 返回 Not found")
        void waitNonExistentRun() {
            WaitSubagentTool waitTool = new WaitSubagentTool(fastRuntime);
            ToolResult result = waitTool.execute(Map.of("runIds", "nonexistent", "timeoutSeconds", 1));

            assertTrue(result.getContent().contains("Not found"),
                    "不存在的 runId 应返回 Not found: " + result.getContent());
        }

        @Test
        @DisplayName("超时未完成返回 timed out")
        void waitTimeout() {
            String runId = slowRuntime.spawn(SpawnRequest.builder()
                    .parentSessionKey("agent:w:main:s1")
                    .task("slow")
                    .agentId("worker")
                    .build(), e -> {});

            WaitSubagentTool waitTool = new WaitSubagentTool(slowRuntime);
            ToolResult result = waitTool.execute(Map.of("runIds", runId, "timeoutSeconds", 1));

            assertTrue(result.isError(), "超时应返回错误");
            assertTrue(result.getContent().contains("timed out"),
                    "应包含 timed out: " + result.getContent());
        }
    }

    @Nested
    @DisplayName("execute — 参数处理")
    class ParameterHandling {

        @Test
        @DisplayName("runIds 的空格被正确 trim")
        void runIdsAreTrimmed() throws InterruptedException {
            String runId = spawnAndWaitCompletion(fastRuntime);

            WaitSubagentTool waitTool = new WaitSubagentTool(fastRuntime);
            ToolResult result = waitTool.execute(Map.of(
                    "runIds", "  " + runId + "  ",
                    "timeoutSeconds", 5));

            assertFalse(result.isError(), "trimmed runId 应正常工作");
            assertTrue(result.getContent().contains(runId));
        }

        @Test
        @DisplayName("不传 timeoutSeconds 时使用默认 60s")
        void defaultTimeout() {
            WaitSubagentTool waitTool = new WaitSubagentTool(fastRuntime);
            ToolResult result = waitTool.execute(Map.of("runIds", "nonexistent"));

            assertTrue(result.getContent().contains("Not found"),
                    "使用默认超时也应正常执行");
        }
    }

    // ==================== Helpers ====================

    private SubagentRuntime createRuntime(LLMProvider provider, int maxConcurrent) {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("worker")
                .systemPrompt("I work")
                .maxSpawnDepth(2)
                .build());
        AgentFactory factory = new AgentFactory(provider, new StubMemory(), new ToolRegistry());
        return new SubagentRuntime(factory, registry, maxConcurrent);
    }

    private String spawnAndWaitCompletion(SubagentRuntime runtime) throws InterruptedException {
        String runId = runtime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("quick task")
                .agentId("worker")
                .build(), e -> {});
        runtime.waitForCompletion(runId, 5, TimeUnit.SECONDS);
        return runId;
    }

    private static class ImmediateProvider implements LLMProvider {
        @Override
        public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent("result-text").build())
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

    private static class SlowProvider implements LLMProvider {
        private final long delayMs;
        SlowProvider(long delayMs) { this.delayMs = delayMs; }
        @Override
        public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent("slow").build())
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
        @Override public String getProviderName() { return "slow"; }
    }

    private static class FailingProvider implements LLMProvider {
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
        @Override public String getProviderName() { return "failing"; }
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
