package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.agent.ToolPolicy.Decision;
import com.lightweightai.kernel.agent.ToolPolicy.ToolPolicyRequest;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolPolicy — 5 个静态工厂各自行为")
class ToolPolicyTest {

    private final AgentProfile anyProfile = AgentProfile.builder().agentId("x").build();

    @Test
    @DisplayName("allowAll → ALLOW")
    void allowAllReturnsAllow() {
        ToolPolicy p = ToolPolicy.allowAll();
        assertEquals(Decision.ALLOW, p.evaluate(req("any", tool("any", RiskLevel.SAFE))));
    }

    @Test
    @DisplayName("denyList:命中 → DENY,未命中 → ABSTAIN(让 chain 继续)")
    void denyListHitsDenyMissAbstain() {
        ToolPolicy p = ToolPolicy.denyList(Set.of("shell"));
        assertEquals(Decision.DENY, p.evaluate(req("shell", tool("shell", RiskLevel.SYSTEM))));
        assertEquals(Decision.ABSTAIN, p.evaluate(req("search", tool("search", RiskLevel.SAFE))));
    }

    @Test
    @DisplayName("allowList:成员 → ALLOW,非成员 → DENY(白名单语义)")
    void allowListMembersAllowedOthersDenied() {
        ToolPolicy p = ToolPolicy.allowList(Set.of("search", "read"));
        assertEquals(Decision.ALLOW, p.evaluate(req("search", tool("search", RiskLevel.SAFE))));
        assertEquals(Decision.ALLOW, p.evaluate(req("read", tool("read", RiskLevel.SAFE))));
        assertEquals(Decision.DENY, p.evaluate(req("shell", tool("shell", RiskLevel.SYSTEM))));
    }

    @Test
    @DisplayName("byRiskLevel:超过 max → DENY,否则 ABSTAIN")
    void byRiskLevelDeniesAboveMax() {
        ToolPolicy p = ToolPolicy.byRiskLevel(RiskLevel.WRITE);
        assertEquals(Decision.ABSTAIN, p.evaluate(req("read", tool("read", RiskLevel.SAFE))));
        assertEquals(Decision.ABSTAIN, p.evaluate(req("write", tool("write", RiskLevel.WRITE))));
        assertEquals(Decision.DENY, p.evaluate(req("shell", tool("shell", RiskLevel.SYSTEM))));
    }

    @Test
    @DisplayName("byRiskLevel:tool 为 null → ABSTAIN(无元数据时不表态)")
    void byRiskLevelAbstainsWhenNoTool() {
        ToolPolicy p = ToolPolicy.byRiskLevel(RiskLevel.SAFE);
        assertEquals(Decision.ABSTAIN, p.evaluate(new ToolPolicyRequest("x", null, null, anyProfile)));
    }

    @Test
    @DisplayName("chain:第一个非 ABSTAIN 决定胜出")
    void chainFirstNonAbstainWins() {
        ToolPolicy denyShell = ToolPolicy.denyList(Set.of("shell"));
        ToolPolicy allowSearch = ToolPolicy.allowList(Set.of("search"));
        ToolPolicy chain = ToolPolicy.chain(denyShell, allowSearch);

        // shell: denyShell=DENY → chain=DENY
        assertEquals(Decision.DENY, chain.evaluate(req("shell", tool("shell", RiskLevel.SYSTEM))));
        // search: denyShell=ABSTAIN → allowSearch=ALLOW → chain=ALLOW
        assertEquals(Decision.ALLOW, chain.evaluate(req("search", tool("search", RiskLevel.SAFE))));
        // other: denyShell=ABSTAIN → allowSearch=DENY(白名单未命中)→ chain=DENY
        assertEquals(Decision.DENY, chain.evaluate(req("foo", tool("foo", RiskLevel.SAFE))));
    }

    @Test
    @DisplayName("chain:全员 ABSTAIN → ABSTAIN(交由调用方做默认决策)")
    void chainAllAbstainReturnsAbstain() {
        ToolPolicy p = ToolPolicy.chain(
                ToolPolicy.denyList(Set.of("nope")),
                ToolPolicy.byRiskLevel(RiskLevel.SYSTEM)
        );
        assertEquals(Decision.ABSTAIN, p.evaluate(req("search", tool("search", RiskLevel.SAFE))));
    }

    private ToolPolicyRequest req(String name, Tool tool) {
        return new ToolPolicyRequest(name, tool, null, anyProfile);
    }

    private Tool tool(String name, RiskLevel level) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return ""; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) { return ToolResult.success("ok"); }
            @Override public RiskLevel riskLevel() { return level; }
        };
    }
}
