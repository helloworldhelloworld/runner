package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import reactor.core.publisher.Flux;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 分发工具 — 同一工具名的多设备/版本变体容器
 *
 * 实现 Tool 接口，对 ToolRegistry 透明。内部持有多个 DeviceToolBinding，
 * 执行时根据 DeviceContext 选择正确的实现。
 *
 * <pre>
 * DispatchingTool("get_position")
 *   ├─ car + >=2.0.0 → CarGetPositionTool
 *   ├─ phone + *     → PhoneGetPositionTool
 *   └─ default        → GenericGetPositionTool
 * </pre>
 */
public class DispatchingTool implements Tool {

    private final String name;
    private final CopyOnWriteArrayList<DeviceToolBinding> bindings;
    private volatile Tool defaultTool;

    public DispatchingTool(String name) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.bindings = new CopyOnWriteArrayList<>();
    }

    public DispatchingTool(String name, Tool defaultTool) {
        this(name);
        this.defaultTool = defaultTool;
    }

    // ==================== Tool 接口（无上下文时走 default）====================

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        Tool representative = getRepresentative();
        return representative != null ? representative.getDescription() : name;
    }

    @Override
    public ToolSchema getSchema() {
        Tool representative = getRepresentative();
        return representative != null ? representative.getSchema() : ToolSchema.empty();
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        Tool resolved = resolve(DeviceContext.DEFAULT);
        if (resolved == null) {
            return ToolResult.error("No tool implementation available for: " + name);
        }
        return resolved.execute(args);
    }

    @Override
    public Flux<ToolResultChunk> executeReactive(Map<String, Object> args) {
        Tool resolved = resolve(DeviceContext.DEFAULT);
        if (resolved == null) {
            return Flux.just(ToolResultChunk.error(name,
                "No tool implementation available for: " + name));
        }
        return resolved.executeReactive(args);
    }

    /**
     * 按所有变体（defaultTool + 各 binding）中的**最高**风险上报。
     *
     * 这是权限闸的保守语义：工具名以其最危险的变体被门禁。否则一个 SAFE 默认实现会让
     * SYSTEM 变体绕过 {@link ToolPolicy#byRiskLevel}（小黄人的运动工具正是 SYSTEM）。
     */
    @Override
    public RiskLevel riskLevel() {
        RiskLevel max = RiskLevel.SAFE;
        if (defaultTool != null && defaultTool.riskLevel() != null
                && defaultTool.riskLevel().ordinal() > max.ordinal()) {
            max = defaultTool.riskLevel();
        }
        for (DeviceToolBinding b : bindings) {
            RiskLevel lvl = b.getTool() != null ? b.getTool().riskLevel() : null;
            if (lvl != null && lvl.ordinal() > max.ordinal()) {
                max = lvl;
            }
        }
        return max;
    }

    // ==================== 设备分发 ====================

    /**
     * 根据设备上下文解析出具体的 Tool 实现
     *
     * 匹配逻辑：
     * 1. 过滤 deviceType 精确匹配（或绑定为通配符 *）的 binding
     * 2. 在匹配中过滤 versionRange.matches(version) 的
     * 3. 按 priority 降序取第一个
     * 4. 无匹配则返回 defaultTool
     *
     * @param context 设备上下文
     * @return 匹配的 Tool，或 defaultTool，或 null
     */
    public Tool resolve(DeviceContext context) {
        if (context == null || context.isDefault()) {
            return defaultTool != null ? defaultTool : getFirstBindingTool();
        }

        Tool matched = bindings.stream()
            .filter(b -> b.matches(context))
            .max(Comparator.comparingInt(DeviceToolBinding::getPriority))
            .map(DeviceToolBinding::getTool)
            .orElse(null);

        if (matched != null) {
            return matched;
        }

        return defaultTool != null ? defaultTool : getFirstBindingTool();
    }

    // ==================== 变体管理 ====================

    public void addBinding(DeviceToolBinding binding) {
        Objects.requireNonNull(binding, "binding cannot be null");
        bindings.add(binding);
    }

    public void setDefaultTool(Tool tool) {
        this.defaultTool = tool;
    }

    public Tool getDefaultTool() {
        return defaultTool;
    }

    /**
     * 移除指定设备类型的所有绑定
     */
    public void removeBindingsForDevice(String deviceType) {
        bindings.removeIf(b -> b.getDeviceType().equals(deviceType));
    }

    /**
     * 检查是否还有绑定或默认工具
     */
    public boolean isEmpty() {
        return bindings.isEmpty() && defaultTool == null;
    }

    public int getBindingCount() {
        return bindings.size();
    }

    // ==================== Internal ====================

    private Tool getRepresentative() {
        if (defaultTool != null) {
            return defaultTool;
        }
        return getFirstBindingTool();
    }

    private Tool getFirstBindingTool() {
        return bindings.isEmpty() ? null : bindings.get(0).getTool();
    }

    @Override
    public String toString() {
        return "DispatchingTool{name='" + name + "'" +
            ", bindings=" + bindings.size() +
            ", hasDefault=" + (defaultTool != null) + '}';
    }
}
