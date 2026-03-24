package com.lightweightai.kernel.agent;

import java.util.Objects;

/**
 * 设备工具绑定 — 描述一个工具变体的适用条件
 *
 * 将一个 Tool 实现绑定到特定的设备类型和版本范围。
 * DispatchingTool 持有多个 DeviceToolBinding，执行时按条件选择。
 */
public final class DeviceToolBinding {

    private final String deviceType;
    private final VersionRange versionRange;
    private final Tool tool;
    private final int priority;

    public DeviceToolBinding(String deviceType, VersionRange versionRange, Tool tool, int priority) {
        this.deviceType = Objects.requireNonNull(deviceType, "deviceType cannot be null");
        this.versionRange = Objects.requireNonNull(versionRange, "versionRange cannot be null");
        this.tool = Objects.requireNonNull(tool, "tool cannot be null");
        this.priority = priority;
    }

    public DeviceToolBinding(String deviceType, VersionRange versionRange, Tool tool) {
        this(deviceType, versionRange, tool, 0);
    }

    /**
     * 检查此绑定是否匹配给定的设备上下文
     */
    public boolean matches(DeviceContext context) {
        if (context == null) {
            return false;
        }
        // 设备类型必须精确匹配（或绑定为通配符）
        if (!DeviceContext.WILDCARD.equals(deviceType)
                && !deviceType.equals(context.getDeviceType())) {
            return false;
        }
        return versionRange.matches(context.getVersion());
    }

    public String getDeviceType() {
        return deviceType;
    }

    public VersionRange getVersionRange() {
        return versionRange;
    }

    public Tool getTool() {
        return tool;
    }

    public int getPriority() {
        return priority;
    }

    @Override
    public String toString() {
        return "DeviceToolBinding{" +
            "deviceType='" + deviceType + '\'' +
            ", versionRange=" + versionRange +
            ", tool=" + tool.getName() +
            ", priority=" + priority +
            '}';
    }
}
