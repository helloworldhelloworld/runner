package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 传输链验证: Orchestrator chatStreamReactive → AgentFactory → AgentLoop → LLMProvider
 *
 * 证明完整链路把 toolDefinitions、systemPrompt 等载荷正确传递给了 LLM Provider,
 * 且 deny/allow 过滤在链路中生效。
 *
 * 遵循 CLAUDE.md UT 规则 3: 每个 producer→consumer 对至少需要 1 个传输测试。
 */
@DisplayName("Orchestrator → LLMProvider 传输链验证")
class OrchestratorToolTransmissionChainTest {

    private AgentRegistry agentRegistry;
    private ToolRegistry globalRegistry;
    private MemoryProvider memory;
    private CapturingLLMProvider spy;
    private Orchestrator orchestrator;

    @BeforeEach
    void setUp() {
        agentRegistry = new AgentRegistry();
        globalRegistry = new ToolRegistry();
        memory = new StubMemory();

        globalRegistry.register(stubTool("search"));
        globalRegistry.register(stubTool("write_file"));
        globalRegistry.register(stubTool("shell_exec"));
    }

    @AfterEach
    void tearDown() {
        if (orchestrator != null) orchestrator.shutdown();
    }

    @Test
    @DisplayName("deny 过滤的工具不出现在 LLM Provider 收到的 toolDefinitions 中")
    @Timeout(5)
    void deniedToolFilteredFromProviderToolDefinitions() {
        spy = CapturingLLMProvider.endTurn("done");
        AgentProfile profile = AgentProfile.builder()
                .agentId("filtered")
                .systemPrompt("test")
                .toolDenyList(Set.of("shell_exec"))
                .build();
        agentRegistry.register(profile);
        agentRegistry.setDefault("filtered");

        AgentFactory factory = new AgentFactory(spy, memory, globalRegistry);
        orchestrator = new Orchestrator(agentRegistry, factory, new MetadataAgentRouter(agentRegistry));

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("chain-sess")
                .message("hello")
                .build();
        orchestrator.chatStreamReactive(request).blockLast();

        LLMOptions received = spy.lastOptions();
        assertNotNull(received, "LLM Provider 应收到 LLMOptions");

        List<Map<String, Object>> defs = received.getToolDefinitions();
        assertNotNull(defs, "toolDefinitions 不应为 null");
        assertEquals(2, defs.size(), "应有 search 和 write_file, shell_exec 被过滤");

        List<String> names = defs.stream().map(d -> (String) d.get("name")).toList();
        assertTrue(names.contains("search"));
        assertTrue(names.contains("write_file"));
        assertFalse(names.contains("shell_exec"),
                "shell_exec 在 denyList 中,不应出现在 LLM Provider 的 toolDefinitions");
    }

    @Test
    @DisplayName("allowList 限制后 LLM Provider 只看到白名单工具")
    @Timeout(5)
    void allowListRestrictsProviderToolDefinitions() {
        spy = CapturingLLMProvider.endTurn("done");
        AgentProfile profile = AgentProfile.builder()
                .agentId("allow-only")
                .systemPrompt("test")
                .toolAllowList(Set.of("search"))
                .build();
        agentRegistry.register(profile);
        agentRegistry.setDefault("allow-only");

        AgentFactory factory = new AgentFactory(spy, memory, globalRegistry);
        orchestrator = new Orchestrator(agentRegistry, factory, new MetadataAgentRouter(agentRegistry));

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("allow-sess")
                .message("hello")
                .build();
        orchestrator.chatStreamReactive(request).blockLast();

        LLMOptions received = spy.lastOptions();
        List<Map<String, Object>> defs = received.getToolDefinitions();
        assertEquals(1, defs.size());
        assertEquals("search", defs.get(0).get("name"));
    }

    @Test
    @DisplayName("无工具注册时 LLM Provider 收到空或 null toolDefinitions")
    @Timeout(5)
    void emptyRegistryProducesEmptyToolDefinitions() {
        spy = CapturingLLMProvider.endTurn("done");
        ToolRegistry emptyRegistry = new ToolRegistry();
        AgentProfile profile = AgentProfile.builder()
                .agentId("no-tools")
                .systemPrompt("test")
                .build();
        agentRegistry.register(profile);
        agentRegistry.setDefault("no-tools");

        AgentFactory factory = new AgentFactory(spy, memory, emptyRegistry);
        orchestrator = new Orchestrator(agentRegistry, factory, new MetadataAgentRouter(agentRegistry));

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("empty-sess")
                .message("hello")
                .build();
        orchestrator.chatStreamReactive(request).blockLast();

        LLMOptions received = spy.lastOptions();
        assertNotNull(received);
        List<Map<String, Object>> defs = received.getToolDefinitions();
        assertTrue(defs == null || defs.isEmpty(),
                "无工具注册时 toolDefinitions 应为空");
    }

    @Test
    @DisplayName("多个不同 agent 请求各自得到正确过滤的 toolDefinitions")
    @Timeout(5)
    void differentAgentsGetDifferentToolDefinitions() {
        spy = CapturingLLMProvider.endTurn("done");

        agentRegistry.register(AgentProfile.builder()
                .agentId("agent-a")
                .systemPrompt("A")
                .toolAllowList(Set.of("search"))
                .build());
        agentRegistry.register(AgentProfile.builder()
                .agentId("agent-b")
                .systemPrompt("B")
                .toolDenyList(Set.of("search"))
                .build());
        agentRegistry.setDefault("agent-a");

        AgentFactory factory = new AgentFactory(spy, memory, globalRegistry);
        orchestrator = new Orchestrator(agentRegistry, factory, new MetadataAgentRouter(agentRegistry));

        orchestrator.chatStreamReactive(GatewayRequest.builder()
                .sessionId("s-a").message("hi").metadata("agentId", "agent-a").build())
                .blockLast();

        LLMOptions optsA = spy.lastOptions();
        List<String> namesA = optsA.getToolDefinitions().stream()
                .map(d -> (String) d.get("name")).toList();
        assertEquals(1, namesA.size());
        assertTrue(namesA.contains("search"));

        orchestrator.chatStreamReactive(GatewayRequest.builder()
                .sessionId("s-b").message("hi").metadata("agentId", "agent-b").build())
                .blockLast();

        LLMOptions optsB = spy.lastOptions();
        List<String> namesB = optsB.getToolDefinitions().stream()
                .map(d -> (String) d.get("name")).toList();
        assertEquals(2, namesB.size());
        assertFalse(namesB.contains("search"), "agent-b deny 了 search");
        assertTrue(namesB.contains("write_file"));
        assertTrue(namesB.contains("shell_exec"));
    }

    private static Tool stubTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name + " tool"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) { return ToolResult.success("ok"); }
        };
    }

    private static class StubMemory implements MemoryProvider {
        private final List<Message> messages = new ArrayList<>();
        @Override public void addMessage(String sid, Message msg) { messages.add(msg); }
        @Override public List<Message> getHistory(String sid, int limit) {
            int start = Math.max(0, messages.size() - limit);
            return new ArrayList<>(messages.subList(start, messages.size()));
        }
        @Override public void clearSession(String sid) { messages.clear(); }
        @Override public void writeEphemeral(String c) {}
        @Override public void writeDurable(String s, String c) {}
        @Override public List<MemorySearchResult> search(String q) { return List.of(); }
    }
}
