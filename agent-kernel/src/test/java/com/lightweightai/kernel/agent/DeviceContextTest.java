package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.agent.directive.ClientManifest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DeviceContext - 设备上下文与匹配")
class DeviceContextTest {

    @Test
    @DisplayName("DEFAULT 实例使用通配符")
    void defaultInstanceIsWildcard() {
        DeviceContext ctx = DeviceContext.DEFAULT;

        assertEquals("*", ctx.getDeviceType());
        assertNull(ctx.getVersion());
        assertTrue(ctx.isDefault());
    }

    @Test
    @DisplayName("of(type, version) 创建指定设备")
    void createWithTypeAndVersion() {
        DeviceContext ctx = DeviceContext.of("ios", "1.2.0");

        assertEquals("ios", ctx.getDeviceType());
        assertEquals("1.2.0", ctx.getVersion());
        assertFalse(ctx.isDefault());
    }

    @Test
    @DisplayName("of(type) 创建无版本设备")
    void createWithTypeOnly() {
        DeviceContext ctx = DeviceContext.of("android");

        assertEquals("android", ctx.getDeviceType());
        assertNull(ctx.getVersion());
        assertFalse(ctx.isDefault());
    }

    @Test
    @DisplayName("of(null, null) 返回 DEFAULT")
    void nullTypeAndVersionReturnDefault() {
        DeviceContext ctx = DeviceContext.of(null, null);
        assertSame(DeviceContext.DEFAULT, ctx);
    }

    @Test
    @DisplayName("of(*, null) 返回 DEFAULT")
    void wildcardTypeReturnDefault() {
        DeviceContext ctx = DeviceContext.of("*", null);
        assertSame(DeviceContext.DEFAULT, ctx);
    }

    @Test
    @DisplayName("of(null) 使用通配符作为 deviceType")
    void nullTypeDefaultsToWildcard() {
        DeviceContext ctx = DeviceContext.of(null);
        assertEquals("*", ctx.getDeviceType());
    }

    @Test
    @DisplayName("fromManifest 正常转换")
    void fromManifestNormal() {
        ClientManifest manifest = new ClientManifest("harmonyos", "3.0", null);

        DeviceContext ctx = DeviceContext.fromManifest(manifest);

        assertEquals("harmonyos", ctx.getDeviceType());
        assertEquals("3.0", ctx.getVersion());
    }

    @Test
    @DisplayName("fromManifest null 返回 DEFAULT")
    void fromManifestNullReturnDefault() {
        DeviceContext ctx = DeviceContext.fromManifest(null);
        assertSame(DeviceContext.DEFAULT, ctx);
    }

    @Test
    @DisplayName("equals 和 hashCode 一致性")
    void equalsAndHashCode() {
        DeviceContext a = DeviceContext.of("ios", "1.0");
        DeviceContext b = DeviceContext.of("ios", "1.0");
        DeviceContext c = DeviceContext.of("ios", "2.0");
        DeviceContext d = DeviceContext.of("android", "1.0");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        assertNotEquals(a, c);
        assertNotEquals(a, d);
        assertNotEquals(a, null);
    }

    @Test
    @DisplayName("toString 包含设备信息")
    void toStringFormat() {
        DeviceContext withVersion = DeviceContext.of("ios", "2.0");
        assertTrue(withVersion.toString().contains("ios"));
        assertTrue(withVersion.toString().contains("2.0"));

        DeviceContext noVersion = DeviceContext.of("web");
        assertTrue(noVersion.toString().contains("web"));
        assertFalse(noVersion.toString().contains("@"));
    }
}
