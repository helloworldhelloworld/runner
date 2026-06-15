package com.lightweightai.kernel.agent;

import java.util.Map;
import java.util.Set;

/**
 * 工具权限策略 — 替代原来的 {@code Set&lt;String&gt;} 允许/拒绝名单。
 *
 * 设计要点:
 * - 三值决策 {ALLOW, DENY, ABSTAIN}。ABSTAIN 让 {@link #chain} 继续到下一层;
 *   最终仍 ABSTAIN 时由调用方(ScopedToolRegistry)决定默认动作。
 * - 传入 {@link Tool} 实例 → 支持按 riskLevel 等元数据判断,而非仅靠名字。
 * - 预留 args 参数(可为 null):列举阶段未知,执行前已知,同一接口兼顾两段。
 *
 * 多层组合示例:
 * <pre>
 * ToolPolicy policy = ToolPolicy.chain(
 *     ToolPolicy.denyList(Set.of("dangerous_tool")),  // 全局硬拒
 *     ToolPolicy.byRiskLevel(RiskLevel.WRITE),        // 风险上限
 *     ToolPolicy.allowList(agentWhitelist)            // per-agent 白名单
 * );
 * </pre>
 */
public interface ToolPolicy {

    Decision evaluate(ToolPolicyRequest req);

    enum Decision { ALLOW, DENY, ABSTAIN }

    /**
     * @param toolName 工具名
     * @param tool     工具实例,可为 null(如 registry 中尚未注册,或仅按名字判断的场景)
     * @param args     工具实际调用参数,仅执行前已知;列举阶段传 null
     * @param profile  发起调用的 AgentProfile
     */
    record ToolPolicyRequest(
            String toolName,
            Tool tool,
            Map<String, Object> args,
            AgentProfile profile
    ) {}

    // ---- 工厂 ----

    /** 总是 ALLOW — 用作 chain 的兜底或占位 */
    static ToolPolicy allowAll() {
        return req -> Decision.ALLOW;
    }

    /** 命中名单 → DENY,未命中 → ABSTAIN(让 chain 继续)*/
    static ToolPolicy denyList(Set<String> names) {
        Set<String> copy = Set.copyOf(names);
        return req -> copy.contains(req.toolName()) ? Decision.DENY : Decision.ABSTAIN;
    }

    /** 白名单语义:成员 → ALLOW,非成员 → DENY */
    static ToolPolicy allowList(Set<String> names) {
        Set<String> copy = Set.copyOf(names);
        return req -> copy.contains(req.toolName()) ? Decision.ALLOW : Decision.DENY;
    }

    /** 工具的 riskLevel 超过 max → DENY,其余 ABSTAIN(不表态,让 chain 继续)*/
    static ToolPolicy byRiskLevel(RiskLevel max) {
        return req -> {
            Tool t = req.tool();
            if (t == null) return Decision.ABSTAIN;
            RiskLevel lvl = t.riskLevel();
            if (lvl == null) return Decision.ABSTAIN;
            return lvl.ordinal() > max.ordinal() ? Decision.DENY : Decision.ABSTAIN;
        };
    }

    /** 依次求值,第一个非 ABSTAIN 胜出;全员 ABSTAIN → ABSTAIN */
    static ToolPolicy chain(ToolPolicy... layers) {
        ToolPolicy[] copy = layers.clone();
        return req -> {
            for (ToolPolicy layer : copy) {
                Decision d = layer.evaluate(req);
                if (d != Decision.ABSTAIN) return d;
            }
            return Decision.ABSTAIN;
        };
    }
}
