package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ScopedToolRegistry - per-Agent 工具权限过滤")
class ScopedToolRegistryTest {

    @Test
    void riskDeniedToolCannotReappearThroughLegacyFallback() {
        Tool risky = new Tool() {
            public String getName() { return "dangerous"; }
            public String getDescription() { return "dangerous"; }
            public ToolSchema getSchema() { return ToolSchema.empty(); }
            public RiskLevel riskLevel() { return RiskLevel.SYSTEM; }
            public ToolResult execute(Map<String, Object> args) { return ToolResult.success("native side effect"); }
        };
        parent.register(risky);
        ScopedToolRegistry scoped = new ScopedToolRegistry(parent,
                AgentProfile.builder().agentId("safe").toolPolicy(ToolPolicy.byRiskLevel(RiskLevel.SAFE)).build());
        assertFalse(scoped.has("dangerous"));
        var executor = new com.lightweightai.kernel.core.ToolExecutor(scoped);
        var received = new java.util.concurrent.atomic.AtomicReference<Map<String, Object>>();
        executor.registerFunction("dangerous", new com.lightweightai.kernel.plugin.PluginFunction() {
            public String getName() { return "dangerous"; }
            public String getDescription() { return "legacy"; }
            public java.util.List<com.lightweightai.kernel.plugin.FunctionParameter> getParameters() { return java.util.List.of(); }
            public Map<String, Object> toJsonSchema() { return Map.of(); }
            public com.lightweightai.kernel.plugin.FunctionResult execute(Map<String, Object> args) {
                received.set(args);
                return com.lightweightai.kernel.plugin.FunctionResult.success("legacy side effect");
            }
        });
        ToolResult result = executor.executeToolCall(new com.lightweightai.kernel.llm.ToolCall("call", "dangerous", Map.of("target", "device")));
        assertTrue(result.isError(), "filtered tool must not reach a legacy side effect");
        assertTrue(result.getContent().contains("denied"));
        assertNull(received.get());
    }

    @Test
    void localBindingShadowsOnlyItsOwnScopeAndAppearsOnceInDefinitions() {
        AgentProfile profile = AgentProfile.builder().agentId("parent").build();
        ScopedToolRegistry a = new ScopedToolRegistry(parent, profile);
        ScopedToolRegistry b = new ScopedToolRegistry(parent, profile);
        Tool original = parent.get("spawn_subagent").orElseThrow();
        Tool local = simpleTool("spawn_subagent");
        a.registerLocal(local);
        assertSame(local, a.get("spawn_subagent").orElseThrow());
        assertSame(original, b.get("spawn_subagent").orElseThrow());
        assertSame(original, parent.get("spawn_subagent").orElseThrow());
        assertEquals(1, a.getToolDefinitions().stream().filter(d -> "spawn_subagent".equals(d.get("name"))).count());
        assertEquals(5, a.enabledCount());
    }

    @Test
    void localBindingsRemainSubjectToProfileDenial() {
        ScopedToolRegistry scoped = new ScopedToolRegistry(parent,
                AgentProfile.builder().agentId("restricted").toolDenyList(Set.of("private_tool")).build());
        scoped.registerLocal(simpleTool("private_tool"));
        assertFalse(scoped.has("private_tool"));
        assertFalse(scoped.isEnabled("private_tool"));
        assertTrue(scoped.get("private_tool").isEmpty());
        assertTrue(scoped.getToolDefinitions().stream().noneMatch(d -> "private_tool".equals(d.get("name"))));
        assertFalse(scoped.isExecutionAllowed("private_tool", null, Map.of(), null));
    }

    private ToolRegistry parent;

    @BeforeEach
    void setUp() {
        parent = new ToolRegistry();
        parent.register(simpleTool("search"));
        parent.register(simpleTool("read_email"));
        parent.register(simpleTool("send_email"));
        parent.register(simpleTool("spawn_subagent"));
        parent.register(simpleTool("shell_exec"));
    }

    @Test
    @DisplayName("无 allow/deny 时透传全部工具")
    void noFilterPassesAll() {
        AgentProfile profile = AgentProfile.builder().agentId("open").build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(parent, profile);

        assertEquals(5, scoped.getEnabled().size());
        assertTrue(scoped.get("search").isPresent());
        assertTrue(scoped.get("shell_exec").isPresent());
    }

    @Test
    @DisplayName("deny list 过滤掉指定工具")
    void denyListFilters() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("safe")
                .toolDenyList(Set.of("shell_exec", "spawn_subagent"))
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(parent, profile);

        assertEquals(3, scoped.getEnabled().size());
        assertTrue(scoped.get("search").isPresent());
        assertTrue(scoped.get("shell_exec").isEmpty());
        assertTrue(scoped.get("spawn_subagent").isEmpty());
    }

    @Test
    @DisplayName("allow list 只允许指定工具")
    void allowListRestricts() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("restricted")
                .toolAllowList(Set.of("search", "read_email"))
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(parent, profile);

        assertEquals(2, scoped.getEnabled().size());
        assertTrue(scoped.get("search").isPresent());
        assertTrue(scoped.get("read_email").isPresent());
        assertTrue(scoped.get("send_email").isEmpty());
    }

    @Test
    @DisplayName("deny 优先于 allow")
    void denyOverridesAllow() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("conflict")
                .toolAllowList(Set.of("search", "shell_exec"))
                .toolDenyList(Set.of("shell_exec"))
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(parent, profile);

        assertEquals(1, scoped.getEnabled().size());
        assertTrue(scoped.get("search").isPresent());
        assertTrue(scoped.get("shell_exec").isEmpty());  // deny wins
    }

    @Test
    @DisplayName("getToolDefinitions 也受过滤影响")
    void toolDefinitionsFiltered() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("limited")
                .toolAllowList(Set.of("search"))
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(parent, profile);

        assertEquals(1, scoped.getToolDefinitions().size());
    }

    @Test
    @DisplayName("has() 受过滤影响")
    void hasRespectsDeny() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("check")
                .toolDenyList(Set.of("shell_exec"))
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(parent, profile);

        assertTrue(scoped.has("search"));
        assertFalse(scoped.has("shell_exec"));
    }

    @Test
    @DisplayName("isEnabled() respects deny list — critical: ToolExecutor uses isEnabled()")
    void isEnabledRespectsDeny() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("exec-check")
                .toolDenyList(Set.of("shell_exec"))
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(parent, profile);

        assertTrue(scoped.isEnabled("search"), "Allowed tool should be enabled");
        assertFalse(scoped.isEnabled("shell_exec"), "Denied tool should NOT be enabled");
    }

    @Test
    @DisplayName("isEnabled() returns false for tools not in parent registry")
    void isEnabledFalseForUnknownTool() {
        AgentProfile profile = AgentProfile.builder().agentId("check").build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(parent, profile);

        assertFalse(scoped.isEnabled("nonexistent_tool"));
    }

    @Test
    @DisplayName("getAll() respects allow list filtering")
    void getAllRespectsAllowList() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("restricted")
                .toolAllowList(Set.of("search", "read_email"))
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(parent, profile);

        assertEquals(2, scoped.getAll().size());
        assertTrue(scoped.getAll().stream().allMatch(t ->
                "search".equals(t.getName()) || "read_email".equals(t.getName())));
    }

    @Test
    @DisplayName("size() reflects filtered count")
    void sizeReflectsFilteredCount() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("sized")
                .toolAllowList(Set.of("search"))
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(parent, profile);

        assertEquals(1, scoped.size());
    }

    @Test
    @DisplayName("enabledCount() reflects filtered count")
    void enabledCountReflectsFilteredCount() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("counted")
                .toolDenyList(Set.of("shell_exec", "send_email"))
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(parent, profile);

        assertEquals(3, scoped.enabledCount());
    }

    @Test
    @DisplayName("register() delegates to parent and is immediately visible through scoped")
    void registerDelegatesToParent() {
        AgentProfile profile = AgentProfile.builder().agentId("registering").build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(parent, profile);

        scoped.register(simpleTool("new_tool"));

        assertTrue(scoped.has("new_tool"), "Newly registered tool should be visible");
        assertTrue(parent.has("new_tool"), "Tool should be registered in parent");
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
