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
