package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Transmission test: ScopedToolRegistry → ToolExecutor
 *
 * Verifies that ToolExecutor.executeToolCall() respects isEnabled() from
 * ScopedToolRegistry. This is the exact code path where the historic bug
 * manifested: ToolExecutor calls toolRegistry.isEnabled() and ScopedToolRegistry
 * must return false for denied tools.
 */
@DisplayName("Transmission: ToolExecutor respects ScopedToolRegistry.isEnabled()")
class ToolExecutorScopedRegistryTransmissionTest {

    private ToolRegistry parent;

    @BeforeEach
    void setUp() {
        parent = new ToolRegistry();
        parent.register(echoTool("allowed_tool"));
        parent.register(echoTool("denied_tool"));
    }

    @Test
    @DisplayName("ToolExecutor executes tool permitted by ScopedToolRegistry")
    void executesPermittedTool() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("safe")
                .toolDenyList(Set.of("denied_tool"))
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(parent, profile);
        ToolExecutor executor = new ToolExecutor(scoped);

        ToolCall call = new ToolCall("call-1", "allowed_tool", Map.of("input", "hello"));
        ToolResult result = executor.executeToolCall(call);

        assertFalse(result.isError(), "permitted tool should execute successfully");
        assertTrue(result.getContent().contains("echo:"), "result should contain tool output");
    }

    @Test
    @DisplayName("ToolExecutor rejects tool denied by ScopedToolRegistry via isEnabled()")
    void rejectsDeniedTool() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("safe")
                .toolDenyList(Set.of("denied_tool"))
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(parent, profile);
        ToolExecutor executor = new ToolExecutor(scoped);

        ToolCall call = new ToolCall("call-2", "denied_tool", Map.of("input", "hello"));
        ToolResult result = executor.executeToolCall(call);

        assertTrue(result.isError(), "denied tool must return error");
        assertTrue(result.getContent().contains("not found") || result.getContent().contains("denied"),
                "error message should indicate tool is not available, got: " + result.getContent());
    }

    @Test
    @DisplayName("ToolExecutor rejects tool not in allowList via isEnabled()")
    void rejectsToolNotInAllowList() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("restricted")
                .toolAllowList(Set.of("allowed_tool"))
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(parent, profile);
        ToolExecutor executor = new ToolExecutor(scoped);

        ToolCall call = new ToolCall("call-3", "denied_tool", Map.of());
        ToolResult result = executor.executeToolCall(call);

        assertTrue(result.isError(), "tool not in allowList must return error");
    }

    @Test
    @DisplayName("getToolDefinitions from ToolExecutor backed by ScopedToolRegistry is filtered")
    void toolDefinitionsFromExecutorAreFiltered() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("scoped")
                .toolDenyList(Set.of("denied_tool"))
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(parent, profile);
        ToolExecutor executor = new ToolExecutor(scoped);

        var defs = executor.getToolRegistry().getToolDefinitions();
        assertEquals(1, defs.size(), "only permitted tool definitions should be visible");
        assertEquals("allowed_tool", defs.get(0).get("name"));
    }

    private Tool echoTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name + " echo tool"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("echo: " + args);
            }
        };
    }
}
