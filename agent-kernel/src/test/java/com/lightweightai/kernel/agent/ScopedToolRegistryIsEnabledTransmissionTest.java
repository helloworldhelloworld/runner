package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.core.ToolExecutionContext;
import com.lightweightai.kernel.core.ToolExecutor;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Transmission-chain test: ScopedToolRegistry → ToolExecutor.
 *
 * Validates the exact bug CLAUDE.md documents:
 *   "ScopedToolRegistry overrode get() and has() but not isEnabled()
 *    → ToolExecutor used isEnabled() → tools invisible"
 *
 * This test asserts that isEnabled(), has(), get(), getEnabled(),
 * getToolDefinitions(), and ToolExecutor.executeToolCall() all
 * consistently respect the deny/allow lists.
 */
@DisplayName("ScopedToolRegistry → ToolExecutor transmission chain")
class ScopedToolRegistryIsEnabledTransmissionTest {

    private ToolRegistry globalRegistry;

    @BeforeEach
    void setUp() {
        globalRegistry = new ToolRegistry();
        globalRegistry.register(simpleTool("search", "search results"));
        globalRegistry.register(simpleTool("shell_exec", "executed"));
        globalRegistry.register(simpleTool("read_file", "file content"));
    }

    @Test
    @DisplayName("isEnabled() returns false for denied tools — ToolExecutor refuses to run them")
    void isEnabledRespectseDenyList() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("safe")
                .toolDenyList(Set.of("shell_exec"))
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(globalRegistry, profile);

        assertFalse(scoped.isEnabled("shell_exec"),
                "isEnabled must return false for denied tool");
        assertTrue(scoped.isEnabled("search"),
                "isEnabled must return true for permitted tool");

        ToolExecutor executor = new ToolExecutor(scoped);
        ToolResult result = executor.executeToolCall(
                new ToolCall("call-1", "shell_exec", Map.of()));
        assertTrue(result.isError(), "ToolExecutor must refuse denied tool");
        assertTrue(result.getContent().contains("not found"),
                "Error message should indicate tool is not available");
    }

    @Test
    @DisplayName("isEnabled() returns false for tools outside allow list — ToolExecutor refuses them")
    void isEnabledRespectsAllowList() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("restricted")
                .toolAllowList(Set.of("search"))
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(globalRegistry, profile);

        assertTrue(scoped.isEnabled("search"));
        assertFalse(scoped.isEnabled("shell_exec"));
        assertFalse(scoped.isEnabled("read_file"));

        ToolExecutor executor = new ToolExecutor(scoped);

        ToolResult allowed = executor.executeToolCall(
                new ToolCall("call-1", "search", Map.of()));
        assertFalse(allowed.isError());
        assertEquals("search results", allowed.getContent());

        ToolResult denied = executor.executeToolCall(
                new ToolCall("call-2", "read_file", Map.of()));
        assertTrue(denied.isError());
    }

    @Test
    @DisplayName("getToolDefinitions() filters denied tools — LLM never sees them")
    void toolDefinitionsExcludeDeniedTools() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("safe")
                .toolDenyList(Set.of("shell_exec"))
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(globalRegistry, profile);

        List<Map<String, Object>> defs = scoped.getToolDefinitions();
        List<String> names = defs.stream()
                .map(d -> (String) d.get("name"))
                .toList();

        assertEquals(2, defs.size());
        assertTrue(names.contains("search"));
        assertTrue(names.contains("read_file"));
        assertFalse(names.contains("shell_exec"));
    }

    @Test
    @DisplayName("all five gates agree: get, has, isEnabled, getEnabled, getToolDefinitions")
    void allFiveGatesConsistent() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("check")
                .toolDenyList(Set.of("shell_exec"))
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(globalRegistry, profile);

        assertFalse(scoped.get("shell_exec").isPresent(), "get() must filter");
        assertFalse(scoped.has("shell_exec"), "has() must filter");
        assertFalse(scoped.isEnabled("shell_exec"), "isEnabled() must filter");

        List<String> enabledNames = scoped.getEnabled().stream()
                .map(Tool::getName).toList();
        assertFalse(enabledNames.contains("shell_exec"), "getEnabled() must filter");

        List<String> defNames = scoped.getToolDefinitions().stream()
                .map(d -> (String) d.get("name")).toList();
        assertFalse(defNames.contains("shell_exec"), "getToolDefinitions() must filter");
    }

    @Test
    @DisplayName("ToolExecutor with context also respects scoped registry")
    void toolExecutorWithContextRespectsScope() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("ctx-test")
                .toolDenyList(Set.of("shell_exec"))
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(globalRegistry, profile);
        ToolExecutor executor = new ToolExecutor(scoped);
        ToolExecutionContext ctx = ToolExecutionContext.serverOnly();

        ToolResult denied = executor.executeToolCall(
                new ToolCall("call-1", "shell_exec", Map.of()), ctx);
        assertTrue(denied.isError());

        ToolResult allowed = executor.executeToolCall(
                new ToolCall("call-2", "search", Map.of()), ctx);
        assertFalse(allowed.isError());
        assertEquals("search results", allowed.getContent());
    }

    private Tool simpleTool(String name, String result) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name + " tool"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success(result);
            }
        };
    }
}
