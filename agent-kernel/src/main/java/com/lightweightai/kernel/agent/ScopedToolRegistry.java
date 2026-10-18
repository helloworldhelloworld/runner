package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.agent.ToolPolicy.Decision;
import com.lightweightai.kernel.agent.ToolPolicy.ToolPolicyRequest;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * per-Agent 工具权限过滤
 *
 * 继承 ToolRegistry，共享工具通过过滤代理访问；registerLocal 保存当前作用域的绑定。
 * 过滤语义由 {@link AgentProfile#getToolPolicy()} 决定:
 * - {@link Decision#ALLOW} 或 {@link Decision#ABSTAIN} → 放行(默认开放)
 * - {@link Decision#DENY} → 拦截
 *
 * 使用 profile 的 legacy allow/deny list 时会在 AgentProfile 构造时被编译成等价 ToolPolicy,
 * 行为与旧版一致。
 */
public class ScopedToolRegistry extends ToolRegistry {

    private final ToolRegistry local = new ToolRegistry();
    private final ToolRegistry parent;
    private final AgentProfile profile;
    private final ToolPolicy policy;

    public ScopedToolRegistry(ToolRegistry parent, AgentProfile profile) {
        this(parent, profile, profile.getToolPolicy());
    }

    public ScopedToolRegistry(ToolRegistry parent, AgentProfile profile, ToolPolicy effectivePolicy) {
        this.parent = parent;
        this.profile = profile;
        this.policy = java.util.Objects.requireNonNull(effectivePolicy);
    }

    @Override
    public ToolRegistry snapshot(com.lightweightai.kernel.core.ExecutionConfigurationScope scope, DeviceContext device) {
        ScopedToolRegistry catalog = new ScopedToolRegistry(scope.tools(parent, device), profile, policy);
        scope.tools(local, device).getEnabled().forEach(catalog::registerLocal);
        return new SnapshotToolRegistry(this, catalog, device);
    }

    @Override
    public Optional<Tool> get(String name) {
        Optional<Tool> t = local.has(name) ? local.get(name) : parent.get(name);
        if (t.isEmpty()) return t;
        return isPermitted(name, t.get()) ? t : Optional.empty();
    }

    @Override
    public boolean has(String name) {
        Optional<Tool> t = local.has(name) ? local.get(name) : parent.get(name);
        return t.isPresent() && isPermitted(name, t.get());
    }

    @Override
    public List<Tool> getEnabled() {
        return visibleTools(true).stream()
                .filter(t -> isPermitted(t.getName(), t))
                .toList();
    }

    @Override
    public List<Tool> getAll() {
        return visibleTools(false).stream()
                .filter(t -> isPermitted(t.getName(), t))
                .toList();
    }

    @Override
    public List<Map<String, Object>> getToolDefinitions() {
        return super.getToolDefinitions();
    }

    @Override
    public int size() {
        return getAll().size();
    }

    @Override
    public boolean isEnabled(String name) {
        Optional<Tool> t = local.has(name) ? local.get(name) : parent.get(name);
        return t.isPresent() && isPermitted(name, t.get()) && (local.has(name) ? local.isEnabled(name) : parent.isEnabled(name));
    }

    @Override
    public int enabledCount() {
        return getEnabled().size();
    }

    /**
     * 注册操作委托给 parent — 保留兼容语义；会话工具使用 registerLocal。
     */
    @Override
    public void register(Tool tool) {
        parent.register(tool);
    }

    /** Bind a run/session-specific tool without mutating the shared registry. */
    public void registerLocal(Tool tool) {
        local.register(tool);
    }

    private List<Tool> visibleTools(boolean enabledOnly) {
        Map<String, Tool> merged = new LinkedHashMap<>();
        (enabledOnly ? parent.getEnabled() : parent.getAll()).forEach(t -> {
            if (!local.has(t.getName())) merged.put(t.getName(), t);
        });
        (enabledOnly ? local.getEnabled() : local.getAll()).forEach(t -> merged.put(t.getName(), t));
        return List.copyOf(merged.values());
    }

    @Override
    public boolean isExecutionAllowed(String name, Tool resolvedTool, Map<String, Object> args,
                                     com.lightweightai.kernel.core.ToolExecutionContext context) {
        Tool catalogTool = (local.has(name) ? local.get(name) : parent.get(name)).orElse(null);
        // get() may have hidden this tool by metadata; a null resolvedTool must not
        // turn that denial into ABSTAIN and allow a same-name legacy function.
        if (catalogTool != null && !isPermitted(name, catalogTool)) return false;
        return parent.isExecutionAllowed(name, resolvedTool, args, context)
                && policy.evaluate(new ToolPolicyRequest(name, resolvedTool, args, profile, context)) != Decision.DENY;
    }

    private boolean isPermitted(String toolName, Tool tool) {
        Tool catalogTool = tool instanceof SnapshotToolRegistry.FrozenTool frozen ? frozen.catalogTool : tool;
        Decision d = policy.evaluate(new ToolPolicyRequest(toolName, catalogTool, null, profile));
        return d != Decision.DENY;  // ALLOW 和 ABSTAIN 都放行(默认开放语义)
    }
}
