package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentFactory - 按 Profile 构建 AgentLoop")
class AgentFactoryTest {

    private ToolRegistry globalRegistry;
    private MemoryProvider memory;
    private LLMProvider defaultProvider;

    @BeforeEach
    void setUp() {
        globalRegistry = new ToolRegistry();
        globalRegistry.register(simpleTool("search"));
        globalRegistry.register(simpleTool("shell_exec"));
        globalRegistry.register(simpleTool("read_file"));

        memory = new StubMemoryProvider();
        defaultProvider = new StubLLMProvider();
    }

    // 注：原 createsAgentWithScopedTools 删除（L-1）——它以 assertNotNull(agent) + "通过执行间接验证"
    // 注释收尾，是 CLAUDE.md UT 规则 4 点名的 ceremony-only 反模式。其载荷断言已由紧邻的
    // createdAgentExposesScopedToolDefinitions（断言 toolDefinitions 不含 shell_exec）完整覆盖。

    @Test
    @DisplayName("创建的 AgentLoop 暴露受 deny/allow 过滤的 toolDefinitions")
    void createdAgentExposesScopedToolDefinitions() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("safe-agent")
                .systemPrompt("安全助手")
                .toolDenyList(Set.of("shell_exec"))
                .build();

        AgentFactory factory = new AgentFactory(defaultProvider, memory, globalRegistry);
        AgentLoop agent = factory.create(profile);

        List<Map<String, Object>> defs = agent.getLlmOptions().getToolDefinitions();
        assertNotNull(defs);
        assertEquals(2, defs.size());
        List<String> names = defs.stream().map(d -> (String) d.get("name")).toList();
        assertTrue(names.contains("search"));
        assertTrue(names.contains("read_file"));
        assertFalse(names.contains("shell_exec"));
    }

    @Test
    @DisplayName("AllowList 模式：只暴露白名单工具的 toolDefinitions")
    void createdAgentRespectsAllowList() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("narrow-agent")
                .systemPrompt("只读助手")
                .toolAllowList(Set.of("read_file"))
                .build();

        AgentFactory factory = new AgentFactory(defaultProvider, memory, globalRegistry);
        AgentLoop agent = factory.create(profile);

        List<Map<String, Object>> defs = agent.getLlmOptions().getToolDefinitions();
        assertEquals(1, defs.size());
        assertEquals("read_file", defs.get(0).get("name"));
    }

    @Test
    @DisplayName("不同 Profile 创建独立的 AgentLoop 实例")
    void createsIndependentInstances() {
        AgentProfile p1 = AgentProfile.builder().agentId("a").systemPrompt("A").build();
        AgentProfile p2 = AgentProfile.builder().agentId("b").systemPrompt("B").build();

        AgentFactory factory = new AgentFactory(defaultProvider, memory, globalRegistry);
        AgentLoop a1 = factory.create(p1);
        AgentLoop a2 = factory.create(p2);

        assertNotNull(a1);
        assertNotNull(a2);
        assertNotSame(a1, a2);
    }

    @Test
    @DisplayName("getSharedMemory 返回构造时传入的 MemoryProvider")
    void sharedMemoryIsPreserved() {
        AgentFactory factory = new AgentFactory(defaultProvider, memory, globalRegistry);
        assertSame(memory, factory.getSharedMemory());
    }

    @Test
    @DisplayName("传输链验证：工具定义从 globalRegistry → ScopedRegistry → AgentLoop.llmOptions.toolDefinitions")
    void toolDefinitionTransmissionChain() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("chain-test")
                .systemPrompt("test")
                .toolDenyList(Set.of("shell_exec"))
                .build();

        AgentFactory factory = new AgentFactory(defaultProvider, memory, globalRegistry);
        AgentLoop agent = factory.create(profile);

        List<Map<String, Object>> defs = agent.getLlmOptions().getToolDefinitions();
        assertNotNull(defs, "toolDefinitions must not be null");
        assertFalse(defs.isEmpty(), "toolDefinitions must not be empty");

        List<String> names = defs.stream().map(d -> (String) d.get("name")).toList();
        assertEquals(2, names.size());
        assertTrue(names.contains("search"));
        assertTrue(names.contains("read_file"));
        assertFalse(names.contains("shell_exec"),
                "Denied tool should be filtered out of toolDefinitions");

        for (Map<String, Object> def : defs) {
            assertNotNull(def.get("name"), "Each tool definition must have a name");
            assertNotNull(def.get("description"), "Each tool definition must have a description");
        }
    }

    @Test
    @DisplayName("maxToolIterations 从 Profile 传递到 AgentLoop — 用 CapturingLLMProvider 证明循环次数受限")
    void maxToolIterationsPassedThrough() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("iter-test")
                .systemPrompt("test")
                .maxToolIterations(2)
                .build();

        var spy = com.lightweightai.kernel.testsupport.CapturingLLMProvider.endTurn("done");
        AgentFactory factory = new AgentFactory(spy, memory, globalRegistry);
        AgentLoop agent = factory.create(profile);

        agent.run("hi", "sess-iter");

        assertTrue(spy.callCount() >= 1, "LLM should have been called at least once");

        List<Map<String, Object>> defs = agent.getLlmOptions().getToolDefinitions();
        assertNotNull(defs, "toolDefinitions must be present even when maxIterations is customized");
        assertEquals(3, defs.size(), "all 3 global tools should be visible (no deny/allow filter)");
    }

    @Test
    @DisplayName("空 ToolRegistry 创建的 AgentLoop 工具定义为空列表")
    void emptyRegistryProducesEmptyToolDefinitions() {
        ToolRegistry emptyRegistry = new ToolRegistry();
        AgentProfile profile = AgentProfile.builder()
                .agentId("empty-tools")
                .systemPrompt("test")
                .build();

        AgentFactory factory = new AgentFactory(defaultProvider, memory, emptyRegistry);
        AgentLoop agent = factory.create(profile);

        List<Map<String, Object>> defs = agent.getLlmOptions().getToolDefinitions();
        assertTrue(defs == null || defs.isEmpty());
    }

    @Test
    @DisplayName("systemPrompt 为 null 时 LLM 收到的 SYSTEM 消息不含 profile systemPrompt 内容")
    void nullSystemPromptHandled() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("no-prompt")
                .build();

        var spy = com.lightweightai.kernel.testsupport.CapturingLLMProvider.endTurn("done");
        AgentFactory factory = new AgentFactory(spy, memory, globalRegistry);
        AgentLoop agent = factory.create(profile);

        agent.run("test", "sess-null-prompt");

        List<ConversationMessage> msgs = spy.lastMessages();
        assertNotNull(msgs, "LLM should have received messages");
        assertFalse(msgs.isEmpty(), "messages must not be empty");

        ConversationMessage systemMsg = msgs.stream()
                .filter(m -> m.getRole() == ConversationMessage.MessageRole.SYSTEM)
                .findFirst().orElse(null);
        assertNotNull(systemMsg, "A SYSTEM message should still be present");
    }

    private Tool simpleTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("ok");
            }
        };
    }

    private static class StubMemoryProvider implements MemoryProvider {
        @Override public void addMessage(String sessionId, Message message) {}
        @Override public List<Message> getHistory(String sessionId, int limit) { return List.of(); }
        @Override public void clearSession(String sessionId) {}
        @Override public void writeEphemeral(String content) {}
        @Override public void writeDurable(String section, String content) {}
        @Override public List<com.lightweightai.kernel.memory.MemorySearchResult> search(String query) { return List.of(); }
    }

    private static class StubLLMProvider implements LLMProvider {
        @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            return LLMResponse.builder().stopReason("end_turn").build();
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
        @Override public String getProviderName() { return "stub"; }
    }
}
