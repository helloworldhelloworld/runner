package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ListSubagentsTool - 列出活跃子 agent 状态")
class ListSubagentsToolTest {

    private SubagentRuntime runtime;
    private ListSubagentsTool tool;

    @BeforeEach
    void setUp() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("worker")
                .systemPrompt("I work")
                .maxSpawnDepth(3)
                .build());
        registry.setDefault("worker");

        AgentFactory factory = new AgentFactory(
                new StubLLMProvider(),
                new StubMemoryProvider(),
                new com.lightweightai.kernel.agent.ToolRegistry()
        );
        runtime = new SubagentRuntime(factory, registry, 10);
        tool = new ListSubagentsTool(runtime);
    }

    @Test
    @DisplayName("getName 返回 list_subagents")
    void nameIsListSubagents() {
        assertEquals("list_subagents", tool.getName());
    }

    @Test
    @DisplayName("无活跃 subagent 时返回 'No active sub-agents.'")
    void noActiveSubagents() {
        ToolResult result = tool.execute(Map.of());

        assertFalse(result.isError());
        assertEquals("No active sub-agents.", result.getContent());
    }

    @Test
    @DisplayName("getSchema 返回 empty schema（无参数）")
    void schemaIsEmpty() {
        assertNotNull(tool.getSchema());
    }

    // Stubs
    private static class StubLLMProvider implements com.lightweightai.kernel.llm.LLMProvider {
        @Override public com.lightweightai.kernel.llm.LLMResponse complete(List<com.lightweightai.kernel.llm.ConversationMessage> m, com.lightweightai.kernel.llm.LLMOptions o) {
            return com.lightweightai.kernel.llm.LLMResponse.builder()
                    .message(com.lightweightai.kernel.llm.ConversationMessage.builder()
                            .role(com.lightweightai.kernel.llm.ConversationMessage.MessageRole.ASSISTANT)
                            .textContent("done")
                            .build())
                    .build();
        }
        @Override public java.util.concurrent.CompletableFuture<com.lightweightai.kernel.llm.LLMResponse> completeAsync(List<com.lightweightai.kernel.llm.ConversationMessage> m, com.lightweightai.kernel.llm.LLMOptions o) {
            return java.util.concurrent.CompletableFuture.completedFuture(complete(m, o));
        }
        @Override public java.util.concurrent.CompletableFuture<com.lightweightai.kernel.llm.LLMResponse> completeStream(List<com.lightweightai.kernel.llm.ConversationMessage> m, com.lightweightai.kernel.llm.LLMOptions o, com.lightweightai.kernel.llm.LLMProvider.StreamEventHandler h) {
            throw new UnsupportedOperationException();
        }
        @Override public com.lightweightai.kernel.llm.ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return "stub"; }
    }

    private static class StubMemoryProvider implements com.lightweightai.kernel.memory.MemoryProvider {
        @Override public void addMessage(String sessionId, com.lightweightai.kernel.memory.Message message) {}
        @Override public List<com.lightweightai.kernel.memory.Message> getHistory(String sessionId, int limit) { return List.of(); }
        @Override public void clearSession(String sessionId) {}
        @Override public void writeEphemeral(String content) {}
        @Override public void writeDurable(String section, String content) {}
        @Override public List<com.lightweightai.kernel.memory.MemorySearchResult> search(String query) { return List.of(); }
    }
}
