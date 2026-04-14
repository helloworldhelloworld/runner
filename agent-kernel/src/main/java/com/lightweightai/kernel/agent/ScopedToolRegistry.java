package com.lightweightai.kernel.agent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * per-Agent 工具权限过滤
 *
 * 继承 ToolRegistry，不复制工具实例，只做过滤代理。
 * deny 优先于 allow（与 OpenClaw 一致）。
 *
 * 用法：
 *   ScopedToolRegistry scoped = new ScopedToolRegistry(globalRegistry, agentProfile);
 *   // scoped.getEnabled() 只返回该 agent 被允许使用的工具
 */
public class ScopedToolRegistry extends ToolRegistry {

    private final ToolRegistry parent;
    private final Set<String> allowList;
    private final Set<String> denyList;

    public ScopedToolRegistry(ToolRegistry parent, AgentProfile profile) {
        this.parent = parent;
        this.allowList = profile.getToolAllowList();
        this.denyList = profile.getToolDenyList();
    }

    @Override
    public Optional<Tool> get(String name) {
        if (!isPermitted(name)) return Optional.empty();
        return parent.get(name);
    }

    @Override
    public boolean has(String name) {
        return isPermitted(name) && parent.has(name);
    }

    @Override
    public List<Tool> getEnabled() {
        return parent.getEnabled().stream()
                .filter(t -> isPermitted(t.getName()))
                .toList();
    }

    @Override
    public List<Tool> getAll() {
        return parent.getAll().stream()
                .filter(t -> isPermitted(t.getName()))
                .toList();
    }

    @Override
    public List<Map<String, Object>> getToolDefinitions() {
        return parent.getToolDefinitions().stream()
                .filter(def -> {
                    Object name = def.get("name");
                    return name == null || isPermitted(name.toString());
                })
                .toList();
    }

    @Override
    public int size() {
        return (int) parent.getAll().stream()
                .filter(t -> isPermitted(t.getName()))
                .count();
    }

    @Override
    public boolean isEnabled(String name) {
        return isPermitted(name) && parent.isEnabled(name);
    }

    @Override
    public int enabledCount() {
        return getEnabled().size();
    }

    /**
     * 注册操作委托给 parent — ScopedToolRegistry 只做过滤，不持有独立工具
     */
    @Override
    public void register(Tool tool) {
        parent.register(tool);
    }

    private boolean isPermitted(String toolName) {
        // deny 优先
        if (denyList != null && denyList.contains(toolName)) return false;
        // 有 allow list 时，只允许列表中的
        if (allowList != null) return allowList.contains(toolName);
        // 无限制
        return true;
    }
}
