package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.InMemoryProvider;
import com.lightweightai.kernel.prompt.*;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionAssemblySnapshotTest {
    @Test void nestedOptionsAreCopiedAndTheObserverDescribesTheActualRequest() {
        Map<String, Object> property = new HashMap<>(Map.of("type", "string"));
        Map<String, Object> definition = new HashMap<>(Map.of("name", "visible", "input_schema",
                Map.of("type", "object", "properties", Map.of("value", property))));
        LLMOptions options = LLMOptions.builder().temperature(0.2).maxTokens(128)
                .toolDefinitions(List.of(definition)).build();
        var provider = CapturingLLMProvider.endTurn("done");
        var observed = new AtomicReference<ExecutionAssembly>();
        var loop = AgentLoop.builder().llmProvider(provider).memoryProvider(new InMemoryProvider())
                .systemPrompt("profile instructions").llmOptions(options).addObserver(new AgentObserver() {
                    public void onExecutionAssembly(String session, ExecutionAssembly assembly) {
                        observed.set(assembly);
                        property.put("type", "number");
                    }
                }).build();
        loop.run("input", "session");
        assertEquals("string", ((Map<?, ?>) ((Map<?, ?>) ((Map<?, ?>) provider.lastOptions()
                .getToolDefinitions().get(0).get("input_schema")).get("properties")).get("value")).get("type"));
        assertEquals(provider.lastOptions().getToolDefinitions(), observed.get().getConfiguration().get("toolDefinitions"));
        assertEquals(128, ((Map<?, ?>) observed.get().getConfiguration().get("parameters")).get("maxTokens"));
        assertEquals("profile instructions", provider.lastMessages().get(0).getTextContent());
        assertThrows(UnsupportedOperationException.class, () -> provider.lastOptions().getToolDefinitions().clear());
    }

    @Test void snapshotDoesNotPreventLiveToolRevocation() {
        ToolRegistry registry = new ToolRegistry();
        var received = new ArrayList<Map<String, Object>>();
        registry.register(new Tool() {
            public String getName() { return "act"; }
            public String getDescription() { return "Actuator"; }
            public ToolSchema getSchema() { return ToolSchema.empty(); }
            public ToolResult execute(Map<String, Object> args) { received.add(args); return ToolResult.success("acted"); }
        });
        var provider = CapturingLLMProvider.toolThenEnd("act", Map.of("target", "forbidden"), "denied");
        var loop = AgentLoop.builder().llmProvider(provider).memoryProvider(new InMemoryProvider()).toolRegistry(registry)
                .addObserver(new AgentObserver() {
                    public void onExecutionAssembly(String session, ExecutionAssembly assembly) { registry.disable("act"); }
                }).build();
        loop.run("request", "session");
        assertTrue(received.isEmpty());
        assertTrue(provider.lastMessages().stream().anyMatch(m -> m.getRole() == ConversationMessage.MessageRole.TOOL
                && m.getTextContent().contains("not found")));
    }

    @Test void configurationCopiesKeepCancellationAndSourcesIndependent() {
        var token = new CancellationToken();
        var models = new ExecutionModelScope();
        var configurations = new ExecutionConfigurationScope();
        var context = ToolExecutionContext.serverOnly().withConfigurationScope(configurations)
                .withModelScope(models).withCancellationToken(token);
        assertSame(configurations, context.getConfigurationScope());
        assertSame(models, context.getModelScope());
        token.cancel();
        assertTrue(context.getCancellationToken().isCancelled());
        assertThrows(IllegalArgumentException.class, () -> ConfigurationContent.digest(Map.of("value", Double.NaN)));
        assertThrows(IllegalArgumentException.class, () -> ConfigurationContent.freeze(new Object()));
        assertEquals(ConfigurationContent.digest(Map.of("b", 2, "a", 1)),
                ConfigurationContent.digest(new LinkedHashMap<>(Map.of("a", 1, "b", 2))));
    }
}
