package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentProfile - ToolPolicy 编译与优先级")
class AgentProfileToolPolicyTest {

    @Test
    @DisplayName("无 allow/deny/policy 时 toolPolicy 默认 allowAll")
    void defaultPolicyIsAllowAll() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("open")
                .build();

        ToolPolicy policy = profile.getToolPolicy();
        assertNotNull(policy);

        ToolPolicy.Decision decision = policy.evaluate(
                new ToolPolicy.ToolPolicyRequest("any_tool", null, null, profile));
        assertEquals(ToolPolicy.Decision.ALLOW, decision);
    }

    @Test
    @DisplayName("仅 denyList 时: 命中 DENY, 未命中 ABSTAIN(交 chain 继续)")
    void denyListOnlyCompilesCorrectly() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("restricted")
                .toolDenyList(Set.of("dangerous", "shell_exec"))
                .build();

        ToolPolicy policy = profile.getToolPolicy();

        assertEquals(ToolPolicy.Decision.DENY,
                policy.evaluate(new ToolPolicy.ToolPolicyRequest("dangerous", null, null, profile)));
        assertEquals(ToolPolicy.Decision.DENY,
                policy.evaluate(new ToolPolicy.ToolPolicyRequest("shell_exec", null, null, profile)));

        ToolPolicy.Decision safeDecision = policy.evaluate(
                new ToolPolicy.ToolPolicyRequest("search", null, null, profile));
        assertNotEquals(ToolPolicy.Decision.DENY, safeDecision,
                "未在 denyList 中的工具不应被 DENY");
    }

    @Test
    @DisplayName("仅 allowList 时: 成员 ALLOW, 非成员 DENY")
    void allowListOnlyCompilesCorrectly() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("whitelist")
                .toolAllowList(Set.of("read", "write"))
                .build();

        ToolPolicy policy = profile.getToolPolicy();

        assertEquals(ToolPolicy.Decision.ALLOW,
                policy.evaluate(new ToolPolicy.ToolPolicyRequest("read", null, null, profile)));
        assertEquals(ToolPolicy.Decision.ALLOW,
                policy.evaluate(new ToolPolicy.ToolPolicyRequest("write", null, null, profile)));
        assertEquals(ToolPolicy.Decision.DENY,
                policy.evaluate(new ToolPolicy.ToolPolicyRequest("shell_exec", null, null, profile)),
                "不在白名单中的工具应被 DENY");
    }

    @Test
    @DisplayName("denyList + allowList: deny 优先于 allow (chain 中 deny 先评估)")
    void denyListOverridesAllowList() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("combined")
                .toolDenyList(Set.of("shell_exec"))
                .toolAllowList(Set.of("shell_exec", "read"))
                .build();

        ToolPolicy policy = profile.getToolPolicy();

        assertEquals(ToolPolicy.Decision.DENY,
                policy.evaluate(new ToolPolicy.ToolPolicyRequest("shell_exec", null, null, profile)),
                "shell_exec 在 denyList 中,即使同时在 allowList,deny 应优先");
        assertEquals(ToolPolicy.Decision.ALLOW,
                policy.evaluate(new ToolPolicy.ToolPolicyRequest("read", null, null, profile)),
                "read 仅在 allowList 中,应 ALLOW");
    }

    @Test
    @DisplayName("显式 toolPolicy 优先于 allow/deny list")
    void explicitPolicyOverridesLists() {
        ToolPolicy customPolicy = req -> ToolPolicy.Decision.DENY;

        AgentProfile profile = AgentProfile.builder()
                .agentId("custom")
                .toolAllowList(Set.of("read", "write"))
                .toolDenyList(Set.of("dangerous"))
                .toolPolicy(customPolicy)
                .build();

        ToolPolicy actual = profile.getToolPolicy();
        assertSame(customPolicy, actual,
                "显式设置的 toolPolicy 应直接使用,不被 allow/deny list 覆盖");

        assertEquals(ToolPolicy.Decision.DENY,
                actual.evaluate(new ToolPolicy.ToolPolicyRequest("read", null, null, profile)),
                "自定义 policy 全部 DENY, read 也应被拒");
    }

    @Test
    @DisplayName("byRiskLevel 策略: 超过 max 则 DENY")
    void riskLevelPolicyEnforcesMax() {
        ToolPolicy policy = ToolPolicy.byRiskLevel(RiskLevel.WRITE);

        Tool safeTool = stubTool("search", RiskLevel.SAFE);
        Tool writeTool = stubTool("write_file", RiskLevel.WRITE);
        Tool systemTool = stubTool("shell_exec", RiskLevel.SYSTEM);

        AgentProfile profile = AgentProfile.builder().agentId("test").build();

        assertEquals(ToolPolicy.Decision.ABSTAIN,
                policy.evaluate(new ToolPolicy.ToolPolicyRequest("search", safeTool, null, profile)));
        assertEquals(ToolPolicy.Decision.ABSTAIN,
                policy.evaluate(new ToolPolicy.ToolPolicyRequest("write_file", writeTool, null, profile)));
        assertEquals(ToolPolicy.Decision.DENY,
                policy.evaluate(new ToolPolicy.ToolPolicyRequest("shell_exec", systemTool, null, profile)),
                "SYSTEM 级工具超过 WRITE 上限,应被 DENY");
    }

    @Test
    @DisplayName("byRiskLevel 策略: tool 为 null 时 ABSTAIN")
    void riskLevelPolicyAbstainsOnNullTool() {
        ToolPolicy policy = ToolPolicy.byRiskLevel(RiskLevel.SAFE);
        AgentProfile profile = AgentProfile.builder().agentId("test").build();

        assertEquals(ToolPolicy.Decision.ABSTAIN,
                policy.evaluate(new ToolPolicy.ToolPolicyRequest("unknown", null, null, profile)));
    }

    @Test
    @DisplayName("chain 策略: 第一个非 ABSTAIN 胜出")
    void chainFirstNonAbstainWins() {
        ToolPolicy layer1 = req -> ToolPolicy.Decision.ABSTAIN;
        ToolPolicy layer2 = req -> "blocked".equals(req.toolName())
                ? ToolPolicy.Decision.DENY : ToolPolicy.Decision.ABSTAIN;
        ToolPolicy layer3 = req -> ToolPolicy.Decision.ALLOW;

        ToolPolicy chained = ToolPolicy.chain(layer1, layer2, layer3);
        AgentProfile profile = AgentProfile.builder().agentId("test").build();

        assertEquals(ToolPolicy.Decision.DENY,
                chained.evaluate(new ToolPolicy.ToolPolicyRequest("blocked", null, null, profile)),
                "layer2 的 DENY 应胜出");
        assertEquals(ToolPolicy.Decision.ALLOW,
                chained.evaluate(new ToolPolicy.ToolPolicyRequest("other", null, null, profile)),
                "layer1 和 layer2 ABSTAIN 后,layer3 的 ALLOW 应胜出");
    }

    @Test
    @DisplayName("chain 策略: 全员 ABSTAIN 则返回 ABSTAIN")
    void chainAllAbstainReturnsAbstain() {
        ToolPolicy chained = ToolPolicy.chain(
                req -> ToolPolicy.Decision.ABSTAIN,
                req -> ToolPolicy.Decision.ABSTAIN);

        AgentProfile profile = AgentProfile.builder().agentId("test").build();
        assertEquals(ToolPolicy.Decision.ABSTAIN,
                chained.evaluate(new ToolPolicy.ToolPolicyRequest("any", null, null, profile)));
    }

    private static Tool stubTool(String name, RiskLevel riskLevel) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) { return ToolResult.success("ok"); }
            @Override public RiskLevel riskLevel() { return riskLevel; }
        };
    }
}
