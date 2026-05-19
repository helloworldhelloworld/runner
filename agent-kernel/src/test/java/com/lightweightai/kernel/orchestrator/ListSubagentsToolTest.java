package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ListSubagentsTool — 单元测试")
class ListSubagentsToolTest {

    @Test
    @DisplayName("getName 返回 list_subagents")
    void nameIsCorrect() {
        ListSubagentsTool tool = new ListSubagentsTool(dummyRuntime());
        assertEquals("list_subagents", tool.getName());
    }

    @Test
    @DisplayName("getSchema 返回空 schema（无参数）")
    void schemaIsEmpty() {
        ListSubagentsTool tool = new ListSubagentsTool(dummyRuntime());
        Map<String, Object> schema = tool.getSchema().toMap();
        assertEquals("object", schema.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.isEmpty());
    }

    @Test
    @DisplayName("无活跃 subagent 时返回 'No active sub-agents'")
    void noActiveSubagents() {
        SubagentRuntime runtime = dummyRuntime();
        ListSubagentsTool tool = new ListSubagentsTool(runtime);

        ToolResult result = tool.execute(Map.of());
        assertFalse(result.isError());
        assertEquals("No active sub-agents.", result.getContent());
    }

    @Test
    @DisplayName("有活跃 subagent 时列出 task 和 status")
    void listActiveSubagents() {
        AgentRegistry reg = new AgentRegistry();
        reg.register(AgentProfile.builder()
                .agentId("w")
                .maxSpawnDepth(1)
                .build());

        LLMProvider slowProvider = new SlowMockProvider(10000);
        AgentFactory f = new AgentFactory(slowProvider, new StubMemory(), new ToolRegistry());
        SubagentRuntime runtime = new SubagentRuntime(f, reg, 10);

        runtime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:w:main:s1")
                .task("analyze data")
                .agentId("w")
                .build(), e -> {});
        runtime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:w:main:s1")
                .task("search docs")
                .agentId("w")
                .build(), e -> {});

        ListSubagentsTool tool = new ListSubagentsTool(runtime);
        ToolResult result = tool.execute(Map.of());

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("analyze data"),
                "Result should contain first task, got: " + result.getContent());
        assertTrue(result.getContent().contains("search docs"),
                "Result should contain second task, got: " + result.getContent());
        assertTrue(result.getContent().contains("Active sub-agents (2)"));
        assertTrue(result.getContent().contains("RUNNING"));

        runtime.stopAll();
    }

    @Test
    @DisplayName("payload 断言: 结果包含 runId、status、duration 信息")
    void resultContainsPayloadFields() {
        AgentRegistry reg = new AgentRegistry();
        reg.register(AgentProfile.builder().agentId("w").maxSpawnDepth(1).build());

        LLMProvider slow = new SlowMockProvider(10000);
        AgentFactory f = new AgentFactory(slow, new StubMemory(), new ToolRegistry());
        SubagentRuntime rt = new SubagentRuntime(f, reg, 5);

        String runId = rt.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:w:main:s1")
                .task("payload test")
                .agentId("w")
                .build(), e -> {});

        ListSubagentsTool tool = new ListSubagentsTool(rt);
        ToolResult result = tool.execute(Map.of());

        assertFalse(result.isError());
        assertTrue(result.getContent().contains(runId),
                "Result should contain the runId: " + runId);
        assertTrue(result.getContent().contains("duration:"));

        rt.stopAll();
    }

    // ==================== Helpers ====================

    private SubagentRuntime dummyRuntime() {
        AgentRegistry reg = new AgentRegistry();
        reg.register(AgentProfile.builder().agentId("default").maxSpawnDepth(1).build());
        LLMProvider p = new ImmediateMockProvider();
        AgentFactory f = new AgentFactory(p, new StubMemory(), new ToolRegistry());
        return new SubagentRuntime(f, reg, 5);
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
                            .textContent("done").build()).build();
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
