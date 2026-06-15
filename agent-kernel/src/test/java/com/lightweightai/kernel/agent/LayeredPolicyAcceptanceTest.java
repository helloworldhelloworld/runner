package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Outside-in Acceptance Test: chain(globalDeny, perAgentAllow)
 *
 * 验证 ABSTAIN 在链式策略中的传递语义,以及 global 级与 per-agent 级的组合。
 */
@DisplayName("Acceptance: 多层策略 chain + ABSTAIN 传递")
class LayeredPolicyAcceptanceTest {

    private ToolRegistry global;

    @BeforeEach
    void setUp() {
        global = new ToolRegistry();
        global.register(simpleTool("search"));
        global.register(simpleTool("read_email"));
        global.register(simpleTool("send_email"));
        global.register(simpleTool("shell_exec"));
    }

    @Test
    @DisplayName("global deny(shell) + per-agent allow(search, read) → 只能用 search/read,其余含 shell 被禁")
    void globalDenyThenPerAgentAllow() {
        ToolPolicy policy = ToolPolicy.chain(
                ToolPolicy.denyList(Set.of("shell_exec")),       // global 层
                ToolPolicy.allowList(Set.of("search", "read_email"))  // per-agent 层
        );
        AgentProfile profile = AgentProfile.builder()
                .agentId("layered")
                .toolPolicy(policy)
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(global, profile);

        assertTrue(scoped.has("search"));
        assertTrue(scoped.has("read_email"));
        assertFalse(scoped.has("send_email"), "per-agent allowList 未含 → DENY");
        assertFalse(scoped.has("shell_exec"), "global deny 命中 → DENY");
    }

    @Test
    @DisplayName("global deny + 下游全 ABSTAIN → 非 deny 工具按默认开放")
    void globalDenyOnlyRestWithDefaults() {
        ToolPolicy policy = ToolPolicy.chain(
                ToolPolicy.denyList(Set.of("shell_exec"))
        );
        AgentProfile profile = AgentProfile.builder()
                .agentId("default-open")
                .toolPolicy(policy)
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(global, profile);

        assertTrue(scoped.has("search"));
        assertTrue(scoped.has("read_email"));
        assertTrue(scoped.has("send_email"));
        assertFalse(scoped.has("shell_exec"));
    }

    private Tool simpleTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) { return ToolResult.success("ok"); }
        };
    }
}
