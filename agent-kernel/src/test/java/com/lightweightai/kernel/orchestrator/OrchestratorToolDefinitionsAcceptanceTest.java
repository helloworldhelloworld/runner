package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Outside-in Acceptance Test: Orchestrator 端到端将 toolDefinitions 传给 LLM
 *
 * 验证 Orchestrator → AgentFactory → AgentLoop → ToolCallingLoop → LLMProvider 的完整链路：
 * 注册在 ScopedToolRegistry 中的工具定义必须出现在 LLM 请求的 LLMOptions.toolDefinitions 中，
 * 否则 LLM 永远不会发出 tool_use — 这正是本分支修复的 bug。
 */
@DisplayName("Acceptance: Orchestrator 将 ScopedToolRegistry 的 toolDefinitions 传给 LLM")
class OrchestratorToolDefinitionsAcceptanceTest {

    private ToolRegistry globalRegistry;

    @BeforeEach
    void setUp() {
        globalRegistry = new ToolRegistry();
        globalRegistry.register(noopTool("safe_search"));
        globalRegistry.register(noopTool("shell_exec"));
        globalRegistry.register(noopTool("read_file"));
    }

    @Test
    @DisplayName("denyList 工具被过滤，其余工具作为 toolDefinitions 传给 LLM")
    void llmReceivesFilteredToolDefinitions() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("safe")
                .systemPrompt("安全助手")
                .toolDenyList(Set.of("shell_exec"))
                .build());
        registry.setDefault("safe");

        CapturingLLMProvider spyProvider = CapturingLLMProvider.endTurn("done");

        TestMemory memory = new TestMemory();
        AgentFactory factory = new AgentFactory(spyProvider, memory, globalRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("sess-acceptance")
                .message("hi")
                .build();

        orchestrator.chatStreamReactive(request).collectList().block();

        LLMOptions received = spyProvider.lastOptions();
        assertNotNull(received, "spy provider should have captured LLMOptions");
        List<Map<String, Object>> defs = received.getToolDefinitions();
        assertNotNull(defs, "toolDefinitions must not be null");
        assertEquals(2, defs.size(),
                "denied tool should be filtered out; got " + toNames(defs));
        List<String> names = toNames(defs);
        assertTrue(names.contains("safe_search"), "safe_search should reach LLM");
        assertTrue(names.contains("read_file"), "read_file should reach LLM");
        assertFalse(names.contains("shell_exec"), "shell_exec must NOT reach LLM");
    }

    @Test
    @DisplayName("无限制 Profile 时所有工具的 toolDefinitions 都传给 LLM")
    void llmReceivesAllToolDefinitionsWhenNoRestrictions() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("open")
                .systemPrompt("开放助手")
                .build());
        registry.setDefault("open");

        CapturingLLMProvider spyProvider = CapturingLLMProvider.endTurn("done");

        TestMemory memory = new TestMemory();
        AgentFactory factory = new AgentFactory(spyProvider, memory, globalRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("sess-open")
                .message("hi")
                .build();

        orchestrator.chatStreamReactive(request).collectList().block();

        LLMOptions received = spyProvider.lastOptions();
        assertNotNull(received);
        List<Map<String, Object>> defs = received.getToolDefinitions();
        assertEquals(3, defs.size(), "got " + toNames(defs));
        assertTrue(toNames(defs).containsAll(List.of("safe_search", "shell_exec", "read_file")));
    }

    private static List<String> toNames(List<Map<String, Object>> defs) {
        return defs.stream().map(d -> (String) d.get("name")).toList();
    }

    private static Tool noopTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name + " tool"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("ok");
            }
        };
    }

    private static class TestMemory implements MemoryProvider {
        @Override public void addMessage(String s, Message m) {}
        @Override public List<Message> getHistory(String s, int l) { return List.of(); }
        @Override public void clearSession(String s) {}
        @Override public void writeEphemeral(String c) {}
        @Override public void writeDurable(String s, String c) {}
        @Override public List<MemorySearchResult> search(String q) { return List.of(); }
    }
}
