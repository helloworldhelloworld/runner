package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.memory.InMemoryProvider;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RequestToolScopeAcceptanceTest {
    @Test
    void sessionSpawnBindingIsVisibleToItsFirstLlmCallAndNeverPublishedGlobally() {
        ToolRegistry tools = new ToolRegistry();
        CapturingLLMProvider llm = CapturingLLMProvider.endTurn("done");
        AgentRegistry agents = new AgentRegistry();
        agents.register(AgentProfile.builder().agentId("parent").maxSpawnDepth(2).build());
        agents.setDefault("parent");
        AgentFactory factory = new AgentFactory(llm, new InMemoryProvider(), tools);
        SubagentRuntime runtime = new SubagentRuntime(factory, agents, 2);
        Orchestrator orchestrator = new Orchestrator(agents, factory, new MetadataAgentRouter(agents), runtime);
        try {
            execute(orchestrator, GatewayRequest.builder().sessionId("A").message("A").build(), true);
            assertTrue(llm.lastOptions().getToolDefinitions().stream()
                    .anyMatch(d -> "spawn_subagent".equals(d.get("name"))), "first request must expose its spawn tool");
            assertFalse(tools.has("spawn_subagent"), "session-specific binding must not escape to global registry");
            execute(orchestrator, GatewayRequest.builder().sessionId("B").message("B").build(), true);
            assertFalse(tools.has("spawn_subagent"));
        } finally {
            orchestrator.shutdown();
            runtime.shutdown();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void requestDeviceReachesTheExecutedToolAndItsResultReachesLlm(boolean reactive) {
        ToolRegistry tools = new ToolRegistry();
        tools.register(tool("position", "generic", new AtomicReference<>()));
        AtomicReference<Map<String, Object>> received = new AtomicReference<>();
        tools.register(tool("position", "phone-result", received), "phone", VersionRange.all());
        CapturingLLMProvider llm = CapturingLLMProvider.toolThenEnd("position", Map.of("precision", 7), "done");
        Orchestrator orchestrator = orchestrator(tools, llm, ToolPolicy.allowAll());
        try {
            execute(orchestrator, GatewayRequest.builder().sessionId("phone-session").message("locate")
                    .deviceContext(DeviceContext.of("phone", "2.0")).build(), reactive);
            assertEquals(Map.of("precision", 7), received.get());
            assertTrue(llm.lastMessages().stream().anyMatch(m -> m.getTextContent().contains("phone-result")));
        } finally {
            orchestrator.shutdown();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void actualArgumentsAreCheckedBeforeSideEffectsAndDenialReachesLlm(boolean reactive) {
        AtomicReference<Map<String, Object>> received = new AtomicReference<>();
        ToolRegistry tools = new ToolRegistry();
        tools.register(tool("move", "moved", received));
        CapturingLLMProvider llm = CapturingLLMProvider.toolThenEnd("move", Map.of("distance", 100), "done");
        ToolPolicy policy = req -> req.args() != null && ((Number) req.args().get("distance")).intValue() > 10
                ? ToolPolicy.Decision.DENY : ToolPolicy.Decision.ALLOW;
        Orchestrator orchestrator = orchestrator(tools, llm, policy);
        try {
            execute(orchestrator, GatewayRequest.builder().message("move").build(), reactive);
            assertTrue(llm.optionsHistory().get(0).getToolDefinitions().stream()
                    .anyMatch(d -> "move".equals(d.get("name"))), "tool remains discoverable");
            assertNull(received.get(), "denied arguments must not reach the actuator");
            assertTrue(llm.lastMessages().stream().anyMatch(m -> m.getTextContent().contains("denied")));
        } finally {
            orchestrator.shutdown();
        }
    }

    private static void execute(Orchestrator orchestrator, GatewayRequest request, boolean reactive) {
        if (reactive) orchestrator.chatStreamReactive(request).collectList().block(Duration.ofSeconds(5));
        else orchestrator.chat(request);
    }

    private static Orchestrator orchestrator(ToolRegistry tools, CapturingLLMProvider llm, ToolPolicy policy) {
        AgentRegistry agents = new AgentRegistry();
        agents.register(AgentProfile.builder().agentId("scoped").toolPolicy(policy).build());
        agents.setDefault("scoped");
        return new Orchestrator(agents, new AgentFactory(llm, new InMemoryProvider(), tools),
                new MetadataAgentRouter(agents));
    }

    private static Tool tool(String name, String result, AtomicReference<Map<String, Object>> received) {
        return new Tool() {
            public String getName() { return name; }
            public String getDescription() { return name; }
            public ToolSchema getSchema() { return ToolSchema.empty(); }
            public ToolResult execute(Map<String, Object> args) {
                received.set(Map.copyOf(args));
                return ToolResult.success(result);
            }
        };
    }
}
