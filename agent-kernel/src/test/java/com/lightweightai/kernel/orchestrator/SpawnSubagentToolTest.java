package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
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

@DisplayName("SpawnSubagentTool — spawn_subagent 工具单元测试")
class SpawnSubagentToolTest {

    private SubagentRuntime runtime;
    private List<StreamEvent> events;
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
        LLMProvider provider = new InstantProvider();
        AgentFactory factory = new AgentFactory(provider, new StubMemory(), tools);
        runtime = new SubagentRuntime(factory, registry, 10);
        events = java.util.Collections.synchronizedList(new ArrayList<>());
        tool = new SpawnSubagentTool(runtime, "agent:worker:main:s1", events::add);
    }

    @AfterEach
    void tearDown() {
        runtime.shutdown();
    }

    @Test
    @DisplayName("getName 返回 spawn_subagent")
    void nameIsSpawnSubagent() {
        assertEquals("spawn_subagent", tool.getName());
    }

    @Test
    @DisplayName("getSchema 包含 task 必填参数和可选的 agentId/model")
    void schemaContainsExpectedProperties() {
        ToolSchema schema = tool.getSchema();
        Map<String, Object> schemaMap = schema.toMap();
        assertEquals("object", schemaMap.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schemaMap.get("properties");
        assertTrue(props.containsKey("task"));
        assertTrue(props.containsKey("agentId"));
        assertTrue(props.containsKey("model"));
    }

    @Test
    @DisplayName("execute 成功返回包含 Run ID 的结果且非阻塞")
    void executeReturnsRunIdNonBlocking() {
        long start = System.currentTimeMillis();
        ToolResult result = tool.execute(Map.of("task", "搜索文档"));
        long elapsed = System.currentTimeMillis() - start;

        assertFalse(result.isError(), "spawn should succeed");
        assertTrue(result.getContent().contains("Run ID:"),
                "result content should contain Run ID, actual: " + result.getContent());
        assertTrue(elapsed < 200, "spawn should be non-blocking, took " + elapsed + "ms");
    }

    @Test
    @DisplayName("execute 产生 SUBAGENT_SPAWN 事件")
    void executeEmitsSpawnEvent() {
        tool.execute(Map.of("task", "some task"));

        assertTrue(events.stream().anyMatch(
                e -> e.getType() == StreamEvent.EventType.SUBAGENT_SPAWN),
                "should emit SUBAGENT_SPAWN event");
    }

    @Test
    @DisplayName("execute 带 agentId 参数时正常工作")
    void executeWithExplicitAgentId() {
        ToolResult result = tool.execute(Map.of("task", "do work", "agentId", "worker"));
        assertFalse(result.isError());
        assertTrue(result.getContent().contains("Run ID:"));
    }

    @Test
    @DisplayName("并发超限时 execute 返回错误")
    void executeConcurrencyLimitReturnsError() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("w").systemPrompt("w").maxSpawnDepth(2).build());
        LLMProvider slowProvider = new SlowProvider(10000);
        AgentFactory factory = new AgentFactory(slowProvider, new StubMemory(), new ToolRegistry());
        SubagentRuntime limitedRuntime = new SubagentRuntime(factory, registry, 1);

        SpawnSubagentTool limitedTool = new SpawnSubagentTool(
                limitedRuntime, "agent:w:main:s1", events::add);

        ToolResult first = limitedTool.execute(Map.of("task", "task1"));
        assertFalse(first.isError());

        ToolResult second = limitedTool.execute(Map.of("task", "task2"));
        assertTrue(second.isError(), "second spawn should fail due to concurrency limit");
        assertTrue(second.getContent().contains("Failed to spawn"));

        limitedRuntime.stopAll();
        limitedRuntime.shutdown();
    }

    @Test
    @DisplayName("getDescription 返回非空描述")
    void descriptionNonEmpty() {
        assertNotNull(tool.getDescription());
        assertFalse(tool.getDescription().isEmpty());
    }

    // ==================== Helpers ====================

    static class InstantProvider implements LLMProvider {
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
        @Override public String getProviderName() { return "instant"; }
    }

    static class SlowProvider implements LLMProvider {
        private final long delayMs;
        SlowProvider(long delayMs) { this.delayMs = delayMs; }
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
        @Override public String getProviderName() { return "slow"; }
    }

    static class StubMemory implements MemoryProvider {
        @Override public void addMessage(String s, Message m) {}
        @Override public List<Message> getHistory(String s, int l) { return List.of(); }
        @Override public void clearSession(String s) {}
        @Override public void writeEphemeral(String c) {}
        @Override public void writeDurable(String s, String c) {}
        @Override public List<MemorySearchResult> search(String q) { return List.of(); }
    }
}
