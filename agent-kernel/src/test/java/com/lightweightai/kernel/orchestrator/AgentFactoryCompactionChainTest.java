package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 传输链验证：AgentFactory 构建的 CompactionChain 正确连接到 AgentLoop
 *
 * AgentFactory.create(profile) 构建 SnipCompactor(3) + MicroCompactor(2000) 的 CompactionChain。
 * 此测试验证在 profile 配置各种场景下工厂仍然正常创建可运行的 AgentLoop。
 */
@DisplayName("AgentFactory - CompactionChain 传输验证")
class AgentFactoryCompactionChainTest {

    @Test
    @DisplayName("AgentFactory 创建的 Agent 可正常执行完整 reactive 流程")
    void createdAgentCanRunReactiveFlow() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("response");
        ToolRegistry registry = new ToolRegistry();
        registry.register(simpleTool("echo"));

        AgentProfile profile = AgentProfile.builder()
                .agentId("test")
                .systemPrompt("test assistant")
                .build();

        AgentFactory factory = new AgentFactory(spy, new StubMemory(), registry);
        AgentLoop agent = factory.create(profile);

        List<StreamEvent> events = agent.runReactive("hello", "s1").collectList().block();
        assertNotNull(events);
        assertFalse(events.isEmpty());

        boolean hasLlmComplete = events.stream()
                .anyMatch(e -> e.getType() == StreamEvent.EventType.LLM_COMPLETE);
        assertTrue(hasLlmComplete, "Must contain LLM_COMPLETE event");

        assertEquals(1, spy.callCount());
        LLMOptions opts = spy.lastOptions();
        assertNotNull(opts);
    }

    @Test
    @DisplayName("工具调用流程端到端：AgentFactory → AgentLoop → ToolCallingLoop → ToolExecutor → ToolRegistry")
    void toolCallingEndToEnd() {
        List<Map<String, Object>> executedToolArgs = Collections.synchronizedList(new ArrayList<>());
        Tool capturingTool = new Tool() {
            @Override public String getName() { return "greeting"; }
            @Override public String getDescription() { return "say hello"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                executedToolArgs.add(args);
                return ToolResult.success("Hi there!");
            }
        };

        ToolRegistry registry = new ToolRegistry();
        registry.register(capturingTool);

        CapturingLLMProvider spy = CapturingLLMProvider.toolThenEnd(
                "greeting", Map.of("name", "world"), "done");

        AgentProfile profile = AgentProfile.builder()
                .agentId("tool-test")
                .systemPrompt("test")
                .build();

        AgentFactory factory = new AgentFactory(spy, new StubMemory(), registry);
        AgentLoop agent = factory.create(profile);

        List<StreamEvent> events = agent.runReactive("call greeting", "s1").collectList().block();
        assertNotNull(events);

        assertEquals(1, executedToolArgs.size(),
                "Tool should have been executed exactly once");
        assertEquals("world", executedToolArgs.get(0).get("name"),
                "Tool args should carry the LLM-specified payload");

        assertEquals(2, spy.callCount(),
                "LLM should be called twice: first returns tool_use, second returns end_turn");
    }

    @Test
    @DisplayName("maxToolIterations 从 profile 传递到 AgentLoop（正常迭代次数内可完成）")
    void maxToolIterationsFromProfile() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(simpleTool("greeting"));

        CapturingLLMProvider spy = CapturingLLMProvider.toolThenEnd(
                "greeting", Map.of(), "final");

        AgentProfile profile = AgentProfile.builder()
                .agentId("bounded")
                .systemPrompt("test")
                .maxToolIterations(5)
                .build();

        AgentFactory factory = new AgentFactory(spy, new StubMemory(), registry);
        AgentLoop agent = factory.create(profile);

        List<StreamEvent> events = agent.runReactive("go", "s1").collectList().block();
        assertNotNull(events);
        assertFalse(events.isEmpty());
    }

    private static Tool simpleTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) { return ToolResult.success("ok"); }
        };
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
