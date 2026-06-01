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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ListSubagentsTool — 单元测试")
class ListSubagentsToolTest {

    private SubagentRuntime runtime;

    @BeforeEach
    void setUp() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("worker")
                .systemPrompt("I work")
                .maxSpawnDepth(2)
                .build());

        LLMProvider slowProvider = new SlowProvider(10000);
        AgentFactory factory = new AgentFactory(slowProvider, new StubMemory(), new ToolRegistry());
        runtime = new SubagentRuntime(factory, registry, 10);
    }

    @AfterEach
    void tearDown() {
        runtime.shutdown();
    }

    @Nested
    @DisplayName("Schema 验证")
    class SchemaTests {

        @Test
        @DisplayName("getName 返回 list_subagents")
        void nameIsListSubagents() {
            assertEquals("list_subagents", new ListSubagentsTool(runtime).getName());
        }

        @Test
        @DisplayName("schema 为空（无需参数）")
        void schemaIsEmpty() {
            ToolSchema schema = new ListSubagentsTool(runtime).getSchema();
            Map<String, Object> props = schema.getProperties();
            assertTrue(props.isEmpty(), "list_subagents 不需要参数");
        }
    }

    @Nested
    @DisplayName("execute — 无活跃 subagent")
    class EmptyState {

        @Test
        @DisplayName("无活跃 subagent 时返回 'No active sub-agents.'")
        void emptyListMessage() {
            ListSubagentsTool listTool = new ListSubagentsTool(runtime);
            ToolResult result = listTool.execute(Map.of());

            assertFalse(result.isError());
            assertEquals("No active sub-agents.", result.getContent());
        }
    }

    @Nested
    @DisplayName("execute — 有活跃 subagent")
    class ActiveRuns {

        @Test
        @DisplayName("单个活跃 subagent 显示 runId、status、task、duration")
        void singleActiveRun() {
            String runId = runtime.spawn(SpawnRequest.builder()
                    .parentSessionKey("agent:worker:main:s1")
                    .task("分析数据")
                    .agentId("worker")
                    .build(), e -> {});

            ListSubagentsTool listTool = new ListSubagentsTool(runtime);
            ToolResult result = listTool.execute(Map.of());

            assertFalse(result.isError());
            String content = result.getContent();
            assertTrue(content.contains("Active sub-agents (1)"), "应显示数量: " + content);
            assertTrue(content.contains(runId), "应包含 runId: " + content);
            assertTrue(content.contains("分析数据"), "应包含 task: " + content);
            assertTrue(content.contains("RUNNING"), "应包含状态: " + content);
            assertTrue(content.contains("ms"), "应包含耗时: " + content);
        }

        @Test
        @DisplayName("多个活跃 subagent 全部列出")
        void multipleActiveRuns() {
            runtime.spawn(SpawnRequest.builder()
                    .parentSessionKey("agent:worker:main:s1")
                    .task("任务A")
                    .agentId("worker").build(), e -> {});
            runtime.spawn(SpawnRequest.builder()
                    .parentSessionKey("agent:worker:main:s1")
                    .task("任务B")
                    .agentId("worker").build(), e -> {});
            runtime.spawn(SpawnRequest.builder()
                    .parentSessionKey("agent:worker:main:s1")
                    .task("任务C")
                    .agentId("worker").build(), e -> {});

            ListSubagentsTool listTool = new ListSubagentsTool(runtime);
            ToolResult result = listTool.execute(Map.of());

            assertFalse(result.isError());
            assertTrue(result.getContent().contains("Active sub-agents (3)"));
            assertTrue(result.getContent().contains("任务A"));
            assertTrue(result.getContent().contains("任务B"));
            assertTrue(result.getContent().contains("任务C"));
        }
    }

    // ==================== Helpers ====================

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

    private static class StubMemory implements MemoryProvider {
        @Override public void addMessage(String s, Message m) {}
        @Override public List<Message> getHistory(String s, int l) { return List.of(); }
        @Override public void clearSession(String s) {}
        @Override public void writeEphemeral(String c) {}
        @Override public void writeDurable(String s, String c) {}
        @Override public List<MemorySearchResult> search(String q) { return List.of(); }
    }
}
