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

@DisplayName("SpawnSubagentTool - LLM 可调用的 subagent spawn 工具")
class SpawnSubagentToolTest {

    private SubagentRuntime runtime;
    private SpawnSubagentTool tool;
    private List<StreamEvent> capturedEvents;

    @BeforeEach
    void setUp() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("worker")
                .systemPrompt("worker agent")
                .maxSpawnDepth(2)
                .build());
        registry.setDefault("worker");

        ToolRegistry tools = new ToolRegistry();
        LLMProvider provider = new FastMockProvider();
        AgentFactory factory = new AgentFactory(provider, new StubMemory(), tools);
        runtime = new SubagentRuntime(factory, registry, 5);

        capturedEvents = java.util.Collections.synchronizedList(new ArrayList<>());
        tool = new SpawnSubagentTool(runtime, "agent:orchestrator:main:s1", capturedEvents::add);
    }

    @Test
    @DisplayName("工具名称为 spawn_subagent")
    void toolName() {
        assertEquals("spawn_subagent", tool.getName());
    }

    @Test
    @DisplayName("schema 包含 task 为必填参数")
    void schemaContainsRequiredTask() {
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
    @DisplayName("execute 成功返回 runId")
    void executeReturnsRunId() throws InterruptedException {
        ToolResult result = tool.execute(Map.of("task", "搜索文档"));

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("Run ID:"));
        assertTrue(result.getContent().contains("Subagent spawned successfully"));

        String runId = extractRunId(result.getContent());
        assertTrue(runtime.waitForCompletion(runId, 5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("execute 发出 SUBAGENT_SPAWN 事件")
    void executeEmitsSpawnEvent() throws InterruptedException {
        ToolResult result = tool.execute(Map.of("task", "分析数据"));

        String runId = extractRunId(result.getContent());
        assertTrue(runtime.waitForCompletion(runId, 5, TimeUnit.SECONDS));
        Thread.sleep(100);

        assertTrue(capturedEvents.stream().anyMatch(e ->
                e.getType() == StreamEvent.EventType.SUBAGENT_SPAWN));
    }

    @Test
    @DisplayName("execute 可指定 agentId")
    void executeWithAgentId() throws InterruptedException {
        ToolResult result = tool.execute(Map.of(
                "task", "执行任务",
                "agentId", "worker"
        ));

        assertFalse(result.isError());
        String runId = extractRunId(result.getContent());
        assertTrue(runtime.waitForCompletion(runId, 5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("execute 在 runtime 拒绝时返回错误 ToolResult")
    void executeReturnsErrorOnRejection() {
        AgentRegistry emptyRegistry = new AgentRegistry();
        emptyRegistry.register(AgentProfile.builder()
                .agentId("no-spawn")
                .systemPrompt("no spawn")
                .maxSpawnDepth(0)
                .build());

        LLMProvider provider = new FastMockProvider();
        AgentFactory factory = new AgentFactory(provider, new StubMemory(), new ToolRegistry());
        SubagentRuntime strictRuntime = new SubagentRuntime(factory, emptyRegistry, 5);

        SpawnSubagentTool strictTool = new SpawnSubagentTool(
                strictRuntime, "agent:no-spawn:main:s1", e -> {});

        ToolResult result = strictTool.execute(Map.of(
                "task", "should fail",
                "agentId", "no-spawn"
        ));

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("Failed to spawn"));
    }

    private String extractRunId(String content) {
        int idx = content.indexOf("Run ID: ");
        if (idx < 0) return "";
        String after = content.substring(idx + "Run ID: ".length());
        int end = after.indexOf(' ');
        return end < 0 ? after.trim() : after.substring(0, end).trim();
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

    private static class StubMemory implements MemoryProvider {
        @Override public void addMessage(String sessionId, Message message) {}
        @Override public List<Message> getHistory(String sessionId, int limit) { return List.of(); }
        @Override public void clearSession(String sessionId) {}
        @Override public void writeEphemeral(String content) {}
        @Override public void writeDurable(String section, String content) {}
        @Override public List<MemorySearchResult> search(String query) { return List.of(); }
    }
}
