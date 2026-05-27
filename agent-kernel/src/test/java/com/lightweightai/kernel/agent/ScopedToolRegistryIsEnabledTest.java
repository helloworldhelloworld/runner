package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Targeted tests for ScopedToolRegistry methods that were historically buggy.
 *
 * Real bug: ScopedToolRegistry overrode get()/has() but not isEnabled() →
 * ToolExecutor used isEnabled() → tools invisible. These tests guard the fix.
 */
@DisplayName("ScopedToolRegistry — isEnabled / enabledCount / size / getAll / register delegation")
class ScopedToolRegistryIsEnabledTest {

    private ToolRegistry parent;

    @BeforeEach
    void setUp() {
        parent = new ToolRegistry();
        parent.register(simpleTool("search"));
        parent.register(simpleTool("shell_exec"));
        parent.register(simpleTool("read_file"));
    }

    @Test
    @DisplayName("isEnabled() returns false for denied tools")
    void isEnabledReturnsFalseForDenied() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("safe")
                .toolDenyList(Set.of("shell_exec"))
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(parent, profile);

        assertTrue(scoped.isEnabled("search"), "permitted tool should be enabled");
        assertFalse(scoped.isEnabled("shell_exec"), "denied tool must NOT be enabled");
        assertTrue(scoped.isEnabled("read_file"), "permitted tool should be enabled");
    }

    @Test
    @DisplayName("isEnabled() returns false for tools not in allowList")
    void isEnabledReturnsFalseForNotAllowed() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("restricted")
                .toolAllowList(Set.of("search"))
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(parent, profile);

        assertTrue(scoped.isEnabled("search"), "allowed tool should be enabled");
        assertFalse(scoped.isEnabled("shell_exec"), "non-allowed tool must NOT be enabled");
        assertFalse(scoped.isEnabled("read_file"), "non-allowed tool must NOT be enabled");
    }

    @Test
    @DisplayName("isEnabled() deny takes precedence over allow")
    void isEnabledDenyWinsOverAllow() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("conflict")
                .toolAllowList(Set.of("search", "shell_exec"))
                .toolDenyList(Set.of("shell_exec"))
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(parent, profile);

        assertTrue(scoped.isEnabled("search"));
        assertFalse(scoped.isEnabled("shell_exec"), "deny must override allow");
    }

    @Test
    @DisplayName("enabledCount() reflects permission filtering")
    void enabledCountReflectsFiltering() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("safe")
                .toolDenyList(Set.of("shell_exec"))
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(parent, profile);

        assertEquals(2, scoped.enabledCount());
    }

    @Test
    @DisplayName("size() reflects permission filtering")
    void sizeReflectsFiltering() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("limited")
                .toolAllowList(Set.of("search"))
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(parent, profile);

        assertEquals(1, scoped.size());
    }

    @Test
    @DisplayName("getAll() only returns permitted tools")
    void getAllReturnsOnlyPermitted() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("safe")
                .toolDenyList(Set.of("shell_exec"))
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(parent, profile);

        List<Tool> all = scoped.getAll();
        assertEquals(2, all.size());
        List<String> names = all.stream().map(Tool::getName).toList();
        assertTrue(names.contains("search"));
        assertTrue(names.contains("read_file"));
        assertFalse(names.contains("shell_exec"));
    }

    @Test
    @DisplayName("register() delegates to parent registry")
    void registerDelegatesToParent() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("delegator")
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(parent, profile);

        scoped.register(simpleTool("new_tool"));

        assertTrue(parent.has("new_tool"), "tool should appear in parent after scoped register");
        assertTrue(scoped.has("new_tool"), "tool should be visible through scoped registry");
    }

    @Test
    @DisplayName("getToolDefinitions payload contains name field matching permitted tools")
    void toolDefinitionsPayloadContainsCorrectNames() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("payload")
                .toolDenyList(Set.of("shell_exec"))
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(parent, profile);

        List<Map<String, Object>> defs = scoped.getToolDefinitions();
        List<String> names = defs.stream()
                .map(d -> (String) d.get("name"))
                .toList();

        assertEquals(2, names.size());
        assertTrue(names.contains("search"));
        assertTrue(names.contains("read_file"));
        assertFalse(names.contains("shell_exec"));
    }

    @Test
    @DisplayName("isEnabled() returns false for non-existent tool")
    void isEnabledReturnsFalseForNonExistent() {
        AgentProfile profile = AgentProfile.builder().agentId("any").build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(parent, profile);

        assertFalse(scoped.isEnabled("nonexistent"));
    }

    private Tool simpleTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name + " tool"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("ok");
            }
        };
    }
}
