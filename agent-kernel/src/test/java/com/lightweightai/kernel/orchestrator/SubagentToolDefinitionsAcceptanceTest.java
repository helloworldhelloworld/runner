package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Outside-in Acceptance Test: Subagent 走同一 AgentFactory 路径，
 * 必须也继承 toolDefinitions 注入 + ScopedToolRegistry 过滤。
 *
 * 这补上 CLAUDE.md "transmission chain" 规则里 SubagentRuntime 分支的断言，
 * 防止主 agent 通过而子 agent 静默丢失工具定义。
 */
@DisplayName("Acceptance: Subagent 链路也把 toolDefinitions 传给 LLM")
class SubagentToolDefinitionsAcceptanceTest {

    private ToolRegistry globalRegistry;
    private AgentRegistry agentRegistry;
    private TestMemory memory;

    @BeforeEach
    void setUp() {
        globalRegistry = new ToolRegistry();
        globalRegistry.register(noopTool("safe_search"));
        globalRegistry.register(noopTool("shell_exec"));
        globalRegistry.register(noopTool("read_file"));

        agentRegistry = new AgentRegistry();
        agentRegistry.register(AgentProfile.builder()
                .agentId("worker")
                .systemPrompt("我是 worker")
                .toolDenyList(Set.of("shell_exec"))
                .maxSpawnDepth(1)
                .build());
        agentRegistry.setDefault("worker");

        memory = new TestMemory();
    }

    @Test
    @DisplayName("Subagent 收到的 LLMOptions.toolDefinitions 也经过 deny/allow 过滤")
    void subagentLLMReceivesFilteredToolDefinitions() throws InterruptedException {
        CapturingLLMProvider spyProvider = CapturingLLMProvider.endTurn("subagent done");
        AgentFactory factory = new AgentFactory(spyProvider, memory, globalRegistry);
        SubagentRuntime runtime = new SubagentRuntime(factory, agentRegistry, 2);

        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("search docs")
                .agentId("worker")
                .build();

        List<StreamEvent> events = new ArrayList<>();
        String runId = runtime.spawn(request, events::add);
        assertTrue(runtime.waitForCompletion(runId, 5, TimeUnit.SECONDS),
                "subagent should finish within 5s");

        LLMOptions received = spyProvider.lastOptions();
        assertNotNull(received, "subagent 的 LLM 调用应被捕获");
        List<Map<String, Object>> defs = received.getToolDefinitions();
        assertNotNull(defs, "subagent 的 toolDefinitions 不应为 null");
        assertEquals(2, defs.size(),
                "subagent 应按 profile deny list 过滤工具；实际收到: " + toNames(defs));
        List<String> names = toNames(defs);
        assertTrue(names.contains("safe_search"));
        assertTrue(names.contains("read_file"));
        assertFalse(names.contains("shell_exec"),
                "denied 工具 shell_exec 绝不应到达 subagent 的 LLM");
    }

    private static List<String> toNames(List<Map<String, Object>> defs) {
        return defs.stream().map(d -> (String) d.get("name")).toList();
    }

    private static Tool noopTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name + " tool"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public com.lightweightai.kernel.llm.ToolResult execute(Map<String, Object> args) {
                return com.lightweightai.kernel.llm.ToolResult.success("ok");
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
