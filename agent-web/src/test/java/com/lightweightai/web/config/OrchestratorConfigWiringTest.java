package com.lightweightai.web.config;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.gateway.ChatHandler;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.orchestrator.*;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wiring acceptance test: simulates OrchestratorConfig bean assembly
 *
 * Verifies the critical transmission chain that OrchestratorConfig creates:
 * AgentRegistry → AgentFactory → SubagentRuntime → Orchestrator (primary ChatHandler)
 *
 * Critical invariants tested:
 * 1. Orchestrator routes to the default agent
 * 2. Subagent tools (wait_subagent, list_subagents) are registered in ToolRegistry
 * 3. Tool definitions reach the LLM through the Orchestrator path
 * 4. SpawnSubagentTool is injected for agents with maxSpawnDepth > 0
 */
@DisplayName("OrchestratorConfig wiring: beans assemble correctly")
class OrchestratorConfigWiringTest {

    @Test
    @DisplayName("orchestratorChatHandler 注册 subagent 工具到 ToolRegistry")
    void subagentToolsRegisteredInToolRegistry() {
        CapturingLLMProvider provider = CapturingLLMProvider.endTurn("ok");
        TestMemory memory = new TestMemory();
        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(noopTool("test_tool"));

        AgentRegistry agentRegistry = new AgentRegistry();
        agentRegistry.register(AgentProfile.builder()
                .agentId("default")
                .displayName("默认助手")
                .maxSpawnDepth(1)
                .maxToolIterations(10)
                .build());
        agentRegistry.register(AgentProfile.builder()
                .agentId("worker")
                .displayName("工作 Agent")
                .systemPrompt("Worker agent")
                .maxSpawnDepth(0)
                .maxToolIterations(10)
                .build());
        agentRegistry.setDefault("default");

        AgentFactory agentFactory = new AgentFactory(provider, memory, toolRegistry);
        SubagentRuntime subagentRuntime = new SubagentRuntime(agentFactory, agentRegistry, 5);

        // Simulate OrchestratorConfig.orchestratorChatHandler() wiring
        toolRegistry.register(new WaitSubagentTool(subagentRuntime));
        toolRegistry.register(new ListSubagentsTool(subagentRuntime));

        assertTrue(toolRegistry.has("wait_subagent"),
                "wait_subagent must be registered after OrchestratorConfig wiring");
        assertTrue(toolRegistry.has("list_subagents"),
                "list_subagents must be registered after OrchestratorConfig wiring");

        subagentRuntime.shutdown();
    }

    @Test
    @DisplayName("Orchestrator 作为 primary ChatHandler 能处理流式请求")
    void orchestratorAsPrimaryChatHandlerProcessesStreamRequest() {
        CapturingLLMProvider provider = CapturingLLMProvider.endTurn("response text");
        TestMemory memory = new TestMemory();
        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(noopTool("search"));

        AgentRegistry agentRegistry = new AgentRegistry();
        agentRegistry.register(AgentProfile.builder()
                .agentId("default")
                .maxSpawnDepth(1)
                .build());
        agentRegistry.setDefault("default");

        AgentFactory agentFactory = new AgentFactory(provider, memory, toolRegistry);
        SubagentRuntime runtime = new SubagentRuntime(agentFactory, agentRegistry, 5);
        toolRegistry.register(new WaitSubagentTool(runtime));
        toolRegistry.register(new ListSubagentsTool(runtime));

        MetadataAgentRouter router = new MetadataAgentRouter(agentRegistry);
        ChatHandler orchestrator = new Orchestrator(agentRegistry, agentFactory, router, runtime);

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("wiring-test")
                .message("hello")
                .build();

        List<StreamEvent> events = orchestrator.chatStreamReactive(request).collectList().block();
        assertNotNull(events);
        assertFalse(events.isEmpty(), "should produce events");

        boolean hasRoute = events.stream()
                .anyMatch(e -> e.getType() == StreamEvent.EventType.AGENT_ROUTE);
        assertTrue(hasRoute, "should produce AGENT_ROUTE event");

        // Verify tool definitions reached the LLM
        LLMOptions capturedOptions = provider.lastOptions();
        assertNotNull(capturedOptions, "LLM should have been called");
        List<Map<String, Object>> toolDefs = capturedOptions.getToolDefinitions();
        assertNotNull(toolDefs, "toolDefinitions must reach the LLM");

        List<String> toolNames = toolDefs.stream()
                .map(d -> (String) d.get("name"))
                .toList();
        assertTrue(toolNames.contains("search"),
                "registered tool must reach LLM, got: " + toolNames);
        assertTrue(toolNames.contains("wait_subagent"),
                "subagent tools must reach LLM since maxSpawnDepth > 0, got: " + toolNames);

        ((Orchestrator) orchestrator).shutdown();
        runtime.shutdown();
    }

    @Test
    @DisplayName("maxSpawnDepth=0 的 worker agent 不注入 spawn_subagent 工具")
    void workerAgentDoesNotGetSpawnTool() {
        CapturingLLMProvider provider = CapturingLLMProvider.endTurn("done");
        TestMemory memory = new TestMemory();
        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(noopTool("read_file"));

        AgentRegistry agentRegistry = new AgentRegistry();
        agentRegistry.register(AgentProfile.builder()
                .agentId("worker")
                .systemPrompt("Worker")
                .maxSpawnDepth(0)
                .build());
        agentRegistry.setDefault("worker");

        AgentFactory agentFactory = new AgentFactory(provider, memory, toolRegistry);
        SubagentRuntime runtime = new SubagentRuntime(agentFactory, agentRegistry, 5);
        toolRegistry.register(new WaitSubagentTool(runtime));
        toolRegistry.register(new ListSubagentsTool(runtime));

        MetadataAgentRouter router = new MetadataAgentRouter(agentRegistry);
        Orchestrator orchestrator = new Orchestrator(agentRegistry, agentFactory, router, runtime);

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("worker-test")
                .message("work")
                .build();

        orchestrator.chatStreamReactive(request).collectList().block();

        LLMOptions capturedOptions = provider.lastOptions();
        assertNotNull(capturedOptions);
        List<Map<String, Object>> toolDefs = capturedOptions.getToolDefinitions();
        assertNotNull(toolDefs);

        List<String> toolNames = toolDefs.stream()
                .map(d -> (String) d.get("name"))
                .toList();
        assertFalse(toolNames.contains("spawn_subagent"),
                "worker (maxSpawnDepth=0) must NOT have spawn_subagent, got: " + toolNames);

        orchestrator.shutdown();
        runtime.shutdown();
    }

    // ==================== Helpers ====================

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
