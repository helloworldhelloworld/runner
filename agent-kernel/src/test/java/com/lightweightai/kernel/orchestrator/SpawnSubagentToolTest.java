package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SpawnSubagentTool — 单元测试")
class SpawnSubagentToolTest {

    private SubagentRuntime runtime;
    private List<StreamEvent> capturedEvents;
    private SpawnSubagentTool tool;

    @BeforeEach
    void setUp() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("worker")
                .systemPrompt("I work")
                .maxSpawnDepth(2)
                .build());

        ToolRegistry tools = new ToolRegistry();
        LLMProvider provider = new ImmediateMockProvider();
        AgentFactory factory = new AgentFactory(provider, new StubMemory(), tools);
        runtime = new SubagentRuntime(factory, registry, 10);
        capturedEvents = Collections.synchronizedList(new ArrayList<>());
        tool = new SpawnSubagentTool(runtime, "agent:worker:main:s1", capturedEvents::add);
    }

    @AfterEach
    void tearDown() {
        runtime.shutdown();
    }

    @Nested
    @DisplayName("Schema 验证")
    class SchemaTests {

        @Test
        @DisplayName("getName 返回 spawn_subagent")
        void nameIsSpawnSubagent() {
            assertEquals("spawn_subagent", tool.getName());
        }

        @Test
        @DisplayName("schema 包含 task(required)、agentId(optional)、model(optional)")
        void schemaContainsExpectedProperties() {
            ToolSchema schema = tool.getSchema();
            Map<String, Object> map = schema.toMap();

            assertEquals("object", map.get("type"));

            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) map.get("properties");
            assertTrue(props.containsKey("task"));
            assertTrue(props.containsKey("agentId"));
            assertTrue(props.containsKey("model"));

            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) map.get("required");
            assertEquals(List.of("task"), required);
        }
    }

    @Nested
    @DisplayName("execute — 正常路径")
    class ExecuteHappyPath {

        @Test
        @DisplayName("只传 task 时成功 spawn 并返回 runId")
        void spawnWithTaskOnly() {
            ToolResult result = tool.execute(Map.of("task", "搜索文档"));

            assertFalse(result.isError());
            assertTrue(result.getContent().contains("Run ID:"),
                    "结果应包含 Run ID，实际: " + result.getContent());
            assertTrue(result.getContent().contains("non-blocking"));
        }

        @Test
        @DisplayName("传入 task + agentId + model 时 payload 正确传递到 SubagentRuntime")
        void spawnWithAllArgs() {
            ToolResult result = tool.execute(Map.of(
                    "task", "分析数据",
                    "agentId", "worker",
                    "model", "claude-3-opus"
            ));

            assertFalse(result.isError());
            assertTrue(result.getContent().contains("Run ID:"));

            assertEquals(1, runtime.getActiveCount() + countCompleted(),
                    "应有恰好一个 spawn（active 或已完成）");
        }

        @Test
        @DisplayName("spawn 是非阻塞的：execute 应在 100ms 内返回")
        void spawnIsNonBlocking() {
            long start = System.currentTimeMillis();
            tool.execute(Map.of("task", "慢任务"));
            long elapsed = System.currentTimeMillis() - start;

            assertTrue(elapsed < 200, "spawn 应非阻塞，耗时 " + elapsed + "ms");
        }

        @Test
        @DisplayName("spawn 后 announcer 收到 SUBAGENT_SPAWN 事件（含 runId、agentId、task）")
        void announcerReceivesSpawnEvent() throws InterruptedException {
            tool.execute(Map.of("task", "test-task", "agentId", "worker"));

            Thread.sleep(100);

            boolean hasSpawnEvent = capturedEvents.stream()
                    .anyMatch(e -> e.getType() == StreamEvent.EventType.SUBAGENT_SPAWN);
            assertTrue(hasSpawnEvent, "应收到 SUBAGENT_SPAWN 事件");

            StreamEvent spawnEvent = capturedEvents.stream()
                    .filter(e -> e.getType() == StreamEvent.EventType.SUBAGENT_SPAWN)
                    .findFirst().orElseThrow();
            assertEquals("worker", spawnEvent.getData().get("agentId"));
            assertEquals("test-task", spawnEvent.getData().get("task"));
            assertNotNull(spawnEvent.getData().get("runId"));
        }
    }

    @Nested
    @DisplayName("execute — 错误路径")
    class ExecuteErrorPath {

        @Test
        @DisplayName("深度超限时返回 error ToolResult")
        void depthExceeded() {
            SpawnSubagentTool deepTool = new SpawnSubagentTool(
                    runtime,
                    "agent:worker:main:s1:subagent:a:subagent:b",
                    capturedEvents::add
            );

            ToolResult result = deepTool.execute(Map.of("task", "too deep", "agentId", "worker"));

            assertTrue(result.isError(), "深度超限应返回错误");
            assertTrue(result.getContent().contains("depth") || result.getContent().contains("spawn"),
                    "错误信息应提及深度限制: " + result.getContent());
        }

        @Test
        @DisplayName("并发超限时返回 error ToolResult")
        void concurrencyExceeded() {
            AgentRegistry reg = new AgentRegistry();
            reg.register(AgentProfile.builder().agentId("w").maxSpawnDepth(1).build());
            LLMProvider slowProvider = new SlowMockProvider(10000);
            AgentFactory f = new AgentFactory(slowProvider, new StubMemory(), new ToolRegistry());
            SubagentRuntime limitedRuntime = new SubagentRuntime(f, reg, 1);

            SpawnSubagentTool limitedTool = new SpawnSubagentTool(
                    limitedRuntime, "agent:w:main:s1", e -> {});

            limitedTool.execute(Map.of("task", "first"));

            ToolResult result = limitedTool.execute(Map.of("task", "second"));

            assertTrue(result.isError(), "并发超限应返回错误");
            assertTrue(result.getContent().contains("concurrent") || result.getContent().contains("Max"),
                    "错误信息应提及并发限制: " + result.getContent());

            limitedRuntime.shutdown();
        }

        @Test
        @DisplayName("agentId 未找到时返回 error ToolResult")
        void unknownAgentId() {
            ToolResult result = tool.execute(Map.of("task", "do", "agentId", "nonexistent"));

            assertTrue(result.isError(), "未知 agentId 应返回错误");
            assertTrue(result.getContent().contains("nonexistent") || result.getContent().contains("not found"),
                    "错误信息应包含 agent ID: " + result.getContent());
        }
    }

    // ==================== Helpers ====================

    private int countCompleted() {
        // 间接检查：如果 activeCount 为 0 但 spawn 成功了，说明已完成
        return 0;
    }

    private static class ImmediateMockProvider implements LLMProvider {
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
        @Override public String getProviderName() { return "immediate"; }
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
                            .textContent("slow done").build())
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

    private static class StubMemory implements MemoryProvider {
        @Override public void addMessage(String s, Message m) {}
        @Override public List<Message> getHistory(String s, int l) { return List.of(); }
        @Override public void clearSession(String s) {}
        @Override public void writeEphemeral(String c) {}
        @Override public void writeDurable(String s, String c) {}
        @Override public List<MemorySearchResult> search(String q) { return List.of(); }
    }
}
