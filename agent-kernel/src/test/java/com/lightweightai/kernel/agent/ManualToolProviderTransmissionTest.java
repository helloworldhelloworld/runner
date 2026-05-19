package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.orchestrator.AgentFactory;
import com.lightweightai.kernel.orchestrator.MetadataAgentRouter;
import com.lightweightai.kernel.orchestrator.Orchestrator;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Acceptance/Transmission test:
 * ManualToolProvider → ToolRegistry → ScopedToolRegistry → AgentLoop → LLMProvider
 *
 * Verifies that tools registered via ManualToolProvider are visible to the LLM
 * in the Orchestrator pipeline's toolDefinitions.
 */
@DisplayName("Acceptance: ManualToolProvider → ToolRegistry → LLM toolDefinitions 传导")
class ManualToolProviderTransmissionTest {

    @Test
    @DisplayName("ManualToolProvider 注册的工具出现在 LLM 收到的 toolDefinitions 中")
    void manuallyRegisteredToolsReachLLM() {
        ToolRegistry globalRegistry = new ToolRegistry();

        ManualToolProvider provider = new ManualToolProvider()
                .addTool(noopTool("manual_search"))
                .addTool(noopTool("manual_calc"));
        provider.start(globalRegistry);

        AgentRegistry agentRegistry = new AgentRegistry();
        agentRegistry.register(AgentProfile.builder()
                .agentId("default")
                .systemPrompt("test")
                .build());
        agentRegistry.setDefault("default");

        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("done");
        AgentFactory factory = new AgentFactory(spy, new StubMemory(), globalRegistry);
        Orchestrator orchestrator = new Orchestrator(agentRegistry, factory, new MetadataAgentRouter(agentRegistry));

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("s1")
                .message("hello")
                .build();

        orchestrator.chatStreamReactive(request).collectList().block();

        LLMOptions received = spy.lastOptions();
        assertNotNull(received, "LLMOptions should be captured");
        List<Map<String, Object>> defs = received.getToolDefinitions();
        assertNotNull(defs, "toolDefinitions must not be null");
        assertEquals(2, defs.size(), "Both manual tools should reach LLM");

        List<String> names = defs.stream().map(d -> (String) d.get("name")).toList();
        assertTrue(names.contains("manual_search"), "manual_search should reach LLM");
        assertTrue(names.contains("manual_calc"), "manual_calc should reach LLM");
    }

    @Test
    @DisplayName("ManualToolProvider stop 后工具不再出现在 LLM toolDefinitions 中")
    void stoppedProviderToolsDisappearFromLLM() {
        ToolRegistry globalRegistry = new ToolRegistry();

        ManualToolProvider provider = new ManualToolProvider()
                .addTool(noopTool("ephemeral_tool"));
        provider.start(globalRegistry);
        provider.stop(globalRegistry);

        globalRegistry.register(noopTool("persistent_tool"));

        AgentRegistry agentRegistry = new AgentRegistry();
        agentRegistry.register(AgentProfile.builder()
                .agentId("default")
                .systemPrompt("test")
                .build());
        agentRegistry.setDefault("default");

        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("done");
        AgentFactory factory = new AgentFactory(spy, new StubMemory(), globalRegistry);
        Orchestrator orchestrator = new Orchestrator(agentRegistry, factory, new MetadataAgentRouter(agentRegistry));

        orchestrator.chatStreamReactive(GatewayRequest.builder()
                .sessionId("s2").message("hi").build()).collectList().block();

        LLMOptions received = spy.lastOptions();
        List<Map<String, Object>> defs = received.getToolDefinitions();
        assertEquals(1, defs.size());
        assertEquals("persistent_tool", defs.get(0).get("name"));
    }

    private static Tool noopTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name + " desc"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public com.lightweightai.kernel.llm.ToolResult execute(Map<String, Object> args) {
                return com.lightweightai.kernel.llm.ToolResult.success("ok");
            }
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
