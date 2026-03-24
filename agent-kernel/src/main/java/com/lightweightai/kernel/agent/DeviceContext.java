package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.agent.directive.ClientManifest;

import java.util.Objects;

/**
 * 设备上下文 — 标识请求来源的设备类型和版本
 *
 * 不可变值对象，用于工具分发时的设备匹配。
 */
public final class DeviceContext {

    /** 通配符设备类型，匹配所有设备 */
    public static final String WILDCARD = "*";

    /** 默认上下文（无特定设备信息） */
    public static final DeviceContext DEFAULT = new DeviceContext(WILDCARD, null);

    private final String deviceType;
    private final String version;

    private DeviceContext(String deviceType, String version) {
        this.deviceType = deviceType != null ? deviceType : WILDCARD;
        this.version = version;
    }

    public static DeviceContext of(String deviceType, String version) {
        if ((deviceType == null || WILDCARD.equals(deviceType)) && version == null) {
            return DEFAULT;
        }
        return new DeviceContext(deviceType, version);
    }

    public static DeviceContext of(String deviceType) {
        return of(deviceType, null);
    }

    public static DeviceContext fromManifest(ClientManifest manifest) {
        if (manifest == null) {
            return DEFAULT;
        }
        return of(manifest.getClientType(), manifest.getClientVersion());
    }

    public String getDeviceType() {
        return deviceType;
    }

    public String getVersion() {
        return version;
    }

    public boolean isDefault() {
        return WILDCARD.equals(deviceType) && version == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeviceContext that)) return false;
        return Objects.equals(deviceType, that.deviceType)
            && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceType, version);
    }

    @Override
    public String toString() {
        return "DeviceContext{" + deviceType +
            (version != null ? "@" + version : "") + '}';
    }
}
