package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Outside-in Acceptance Test: 用 RiskLevel + byRiskLevel 策略做权限过滤
 *
 * 目的:证明 ToolPolicy 能承载"风险分级"这种以前纯字符串名单装不下的语义。
 */
@DisplayName("Acceptance: byRiskLevel 策略能按 Tool.riskLevel() 过滤")
class RiskLevelPolicyAcceptanceTest {

    private ToolRegistry global;

    @BeforeEach
    void setUp() {
        global = new ToolRegistry();
        global.register(toolOf("search", RiskLevel.SAFE));
        global.register(toolOf("write_file", RiskLevel.WRITE));
        global.register(toolOf("shell_exec", RiskLevel.SYSTEM));
    }

    @Test
    @DisplayName("profile 限定 max=WRITE → SYSTEM 工具被过滤,SAFE/WRITE 可见")
    void byRiskLevelWriteFiltersSystem() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("writer")
                .toolPolicy(ToolPolicy.byRiskLevel(RiskLevel.WRITE))
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(global, profile);

        assertTrue(scoped.has("search"), "SAFE 应可见");
        assertTrue(scoped.has("write_file"), "WRITE 应可见(不超过 max)");
        assertFalse(scoped.has("shell_exec"), "SYSTEM 应被过滤");
        assertEquals(2, scoped.getEnabled().size());
    }

    @Test
    @DisplayName("profile 限定 max=SAFE → 只保留 SAFE 工具")
    void byRiskLevelSafeFiltersWriteAndSystem() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("reader")
                .toolPolicy(ToolPolicy.byRiskLevel(RiskLevel.SAFE))
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(global, profile);

        assertEquals(1, scoped.getEnabled().size());
        assertTrue(scoped.has("search"));
    }

    private Tool toolOf(String name, RiskLevel level) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) { return ToolResult.success("ok"); }
            @Override public RiskLevel riskLevel() { return level; }
        };
    }
}
