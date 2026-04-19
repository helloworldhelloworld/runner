package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.agent.ToolPolicy.Decision;
import com.lightweightai.kernel.agent.ToolPolicy.ToolPolicyRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * per-Agent 工具权限过滤
 *
 * 继承 ToolRegistry,不复制工具实例,只做过滤代理。
 * 过滤语义由 {@link AgentProfile#getToolPolicy()} 决定:
 * - {@link Decision#ALLOW} 或 {@link Decision#ABSTAIN} → 放行(默认开放)
 * - {@link Decision#DENY} → 拦截
 *
 * 使用 profile 的 legacy allow/deny list 时会在 AgentProfile 构造时被编译成等价 ToolPolicy,
 * 行为与旧版一致。
 */
public class ScopedToolRegistry extends ToolRegistry {

    private final ToolRegistry parent;
    private final AgentProfile profile;
    private final ToolPolicy policy;

    public ScopedToolRegistry(ToolRegistry parent, AgentProfile profile) {
        this.parent = parent;
        this.profile = profile;
        this.policy = profile.getToolPolicy();
    }

    @Override
    public Optional<Tool> get(String name) {
        Optional<Tool> t = parent.get(name);
        if (t.isEmpty()) return t;
        return isPermitted(name, t.get()) ? t : Optional.empty();
    }

    @Override
    public boolean has(String name) {
        Optional<Tool> t = parent.get(name);
        return t.isPresent() && isPermitted(name, t.get());
    }

    @Override
    public List<Tool> getEnabled() {
        return parent.getEnabled().stream()
                .filter(t -> isPermitted(t.getName(), t))
                .toList();
    }

    @Override
    public List<Tool> getAll() {
        return parent.getAll().stream()
                .filter(t -> isPermitted(t.getName(), t))
                .toList();
    }

    @Override
    public List<Map<String, Object>> getToolDefinitions() {
        return parent.getToolDefinitions().stream()
                .filter(def -> {
                    Object name = def.get("name");
                    if (name == null) return true;
                    String toolName = name.toString();
                    return isPermitted(toolName, parent.get(toolName).orElse(null));
                })
                .toList();
    }

    @Override
    public int size() {
        return getAll().size();
    }

    @Override
    public boolean isEnabled(String name) {
        Optional<Tool> t = parent.get(name);
        return t.isPresent() && isPermitted(name, t.get()) && parent.isEnabled(name);
    }

    @Override
    public int enabledCount() {
        return getEnabled().size();
    }

    /**
     * 注册操作委托给 parent — ScopedToolRegistry 只做过滤,不持有独立工具。
     */
    @Override
    public void register(Tool tool) {
        parent.register(tool);
    }

    private boolean isPermitted(String toolName, Tool tool) {
        Decision d = policy.evaluate(new ToolPolicyRequest(toolName, tool, null, profile));
        return d != Decision.DENY;  // ALLOW 和 ABSTAIN 都放行(默认开放语义)
    }
}
