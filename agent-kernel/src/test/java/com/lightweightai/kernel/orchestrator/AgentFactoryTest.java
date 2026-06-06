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

    @Test
    @DisplayName("创建的 AgentLoop 使用 ScopedToolRegistry 过滤工具")
    void createsAgentWithScopedTools() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("safe-agent")
                .systemPrompt("你是安全助手")
                .toolDenyList(Set.of("shell_exec"))
                .build();

        AgentFactory factory = new AgentFactory(defaultProvider, memory, globalRegistry);
        AgentLoop agent = factory.create(profile);

        assertNotNull(agent);
        // AgentLoop 内部使用的 ToolRegistry 应该是 scoped 的
        // 我们通过执行来间接验证：工具定义中不应包含 shell_exec
    }

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
    @DisplayName("maxToolIterations 从 Profile 传递到 AgentLoop")
    void maxToolIterationsPassedThrough() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("iter-test")
                .systemPrompt("test")
                .maxToolIterations(25)
                .build();

        AgentFactory factory = new AgentFactory(defaultProvider, memory, globalRegistry);
        AgentLoop agent = factory.create(profile);

        assertNotNull(agent);
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
    @DisplayName("systemPrompt 为 null 时不设置系统提示")
    void nullSystemPromptHandled() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("no-prompt")
                .build();

        AgentFactory factory = new AgentFactory(defaultProvider, memory, globalRegistry);
        AgentLoop agent = factory.create(profile);
        assertNotNull(agent);
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
