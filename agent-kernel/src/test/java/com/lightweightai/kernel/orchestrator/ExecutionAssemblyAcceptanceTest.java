package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.ToolExecutionContext;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.InMemoryProvider;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionAssemblyAcceptanceTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void advertisedToolImplementationStaysBoundUntilTheNextExecution(boolean reactive) {
        ToolRegistry tools = new ToolRegistry();
        List<String> actuator = new CopyOnWriteArrayList<>();
        tools.register(probe("v1", actuator));
        tools.register(new Tool() {
            public String getName() { return "switch"; }
            public String getDescription() { return "Change tool catalog"; }
            public ToolSchema getSchema() { return ToolSchema.empty(); }
            public ToolResult execute(Map<String, Object> args) {
                tools.register(probe("v2", actuator));
                return ToolResult.success("switched");
            }
        });
        CapturingLLMProvider remote = CapturingLLMProvider.responding((messages, options) -> {
            String input = messages.stream().filter(m -> m.getRole() == ConversationMessage.MessageRole.USER)
                    .map(ConversationMessage::getTextContent).reduce((a, b) -> b).orElseThrow();
            List<String> results = messages.stream().filter(m -> m.getRole() == ConversationMessage.MessageRole.TOOL)
                    .map(ConversationMessage::getTextContent).toList();
            if (results.stream().anyMatch(t -> t.contains("executed"))) return text("done");
            if (input.equals("old") && results.isEmpty()) return call("switch", Map.of());
            return call("probe", Map.of("marker", input));
        });
        var loop = new AgentFactory(remote, new InMemoryProvider(), tools)
                .create(AgentProfile.builder().agentId("root").build());
        if (reactive) loop.runReactive("old", "A").collectList().block(Duration.ofSeconds(5));
        else loop.run("old", "A");
        assertEquals(List.of("v1:old"), actuator, "the actual actuator must match the advertised version");
        assertTrue(remote.lastOptions().getToolDefinitions().stream().anyMatch(d -> "probe-v1".equals(d.get("description"))));
        if (reactive) loop.runReactive("new", "B").collectList().block(Duration.ofSeconds(5));
        else loop.run("new", "B");
        assertEquals(List.of("v1:old", "v2:new"), actuator);
        assertTrue(remote.lastOptions().getToolDefinitions().stream().anyMatch(d -> "probe-v2".equals(d.get("description"))));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void deviceDefinitionAndExecutionUseTheSameSelectedVariant(boolean reactive) {
        var tools = new ToolRegistry();
        var actuator = new CopyOnWriteArrayList<String>();
        tools.register(probe("desktop", actuator));
        tools.register(probe("minion", actuator), "minion", VersionRange.parse("*"));
        var remote = CapturingLLMProvider.toolThenEnd("probe", Map.of("marker", "actual"), "done");
        var loop = new AgentFactory(remote, new InMemoryProvider(), tools)
                .create(AgentProfile.builder().agentId("minion").build());
        var context = new ToolExecutionContext(null, 1000, DeviceContext.of("minion", "1.0.0"));
        if (reactive) loop.runReactive("go", "A", null, context).collectList().block(Duration.ofSeconds(5));
        else loop.run("go", "A", context);
        assertEquals(List.of("minion:actual"), actuator);
        assertTrue(remote.lastOptions().getToolDefinitions().stream().anyMatch(d -> "probe-minion".equals(d.get("description"))),
                "the model must see the schema/description of the selected device variant");
    }

    @org.junit.jupiter.api.Test
    void snapshotPreservesTheToolObjectConsumedByExistingPolicies() {
        var tools = new ToolRegistry();
        var actuator = new CopyOnWriteArrayList<String>();
        Tool allowed = probe("allowed", actuator);
        tools.register(allowed);
        var provider = CapturingLLMProvider.toolThenEnd("probe", Map.of("marker", "actual"), "done");
        var profile = AgentProfile.builder().agentId("root").toolPolicy(request -> request.tool() == allowed
                ? ToolPolicy.Decision.ALLOW : ToolPolicy.Decision.DENY).build();
        new AgentFactory(provider, new InMemoryProvider(), tools).create(profile).run("go", "session");
        assertEquals(List.of("allowed:actual"), actuator);
        assertTrue(provider.lastOptions().getToolDefinitions().stream().anyMatch(d -> "probe".equals(d.get("name"))));
    }

    private static Tool probe(String version, List<String> received) {
        return new Tool() {
            public String getName() { return "probe"; }
            public String getDescription() { return "probe-" + version; }
            public ToolSchema getSchema() { return ToolSchema.withRequired(Map.of("marker", Map.of("type", "string")), "marker"); }
            public ToolResult execute(Map<String, Object> args) {
                received.add(version + ":" + args.get("marker"));
                return ToolResult.success("executed " + version);
            }
        };
    }
    private static LLMResponse call(String name, Map<String, Object> args) {
        return LLMResponse.builder().message(ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT).textContent("").build())
                .toolCalls(List.of(new ToolCall(name + "-call", name, args))).stopReason("tool_use").build();
    }
    private static LLMResponse text(String text) {
        return LLMResponse.builder().message(ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT).textContent(text).build()).stopReason("end_turn").build();
    }
}
