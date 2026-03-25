package com.lightweightai.kernel.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DeviceContext 单元测试 — 设备上下文值对象
 *
 * 覆盖：工厂方法、DEFAULT 单例、equals/hashCode、isDefault
 */
@DisplayName("DeviceContext - 设备上下文")
class DeviceContextTest {

    @Test
    @DisplayName("DEFAULT 实例为通配符设备类型，无版本")
    void defaultInstance() {
        DeviceContext ctx = DeviceContext.DEFAULT;
        assertEquals("*", ctx.getDeviceType());
        assertNull(ctx.getVersion());
        assertTrue(ctx.isDefault());
    }

    @Test
    @DisplayName("of(null, null) 返回 DEFAULT")
    void ofNullReturnsDefault() {
        assertSame(DeviceContext.DEFAULT, DeviceContext.of(null, null));
    }

    @Test
    @DisplayName("of('*', null) 返回 DEFAULT")
    void ofWildcardReturnsDefault() {
        assertSame(DeviceContext.DEFAULT, DeviceContext.of("*", null));
    }

    @Test
    @DisplayName("of 创建新实例")
    void ofCreatesNew() {
        DeviceContext ctx = DeviceContext.of("phone", "2.1.0");
        assertEquals("phone", ctx.getDeviceType());
        assertEquals("2.1.0", ctx.getVersion());
        assertFalse(ctx.isDefault());
    }

    @Test
    @DisplayName("of(deviceType) 无版本")
    void ofDeviceOnly() {
        DeviceContext ctx = DeviceContext.of("car");
        assertEquals("car", ctx.getDeviceType());
        assertNull(ctx.getVersion());
        assertFalse(ctx.isDefault());
    }

    @Test
    @DisplayName("null deviceType 映射为通配符")
    void nullDeviceTypeBecomesWildcard() {
        DeviceContext ctx = DeviceContext.of(null, "1.0");
        assertEquals("*", ctx.getDeviceType());
    }

    @Test
    @DisplayName("equals 和 hashCode 正确")
    void equalsAndHashCode() {
        DeviceContext a = DeviceContext.of("phone", "1.0");
        DeviceContext b = DeviceContext.of("phone", "1.0");
        DeviceContext c = DeviceContext.of("car", "1.0");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    @DisplayName("toString 包含设备类型和版本")
    void toStringFormat() {
        DeviceContext ctx = DeviceContext.of("phone", "2.0");
        String str = ctx.toString();
        assertTrue(str.contains("phone"));
        assertTrue(str.contains("2.0"));
    }

    @Test
    @DisplayName("toString 无版本时不显示 @")
    void toStringNoVersion() {
        DeviceContext ctx = DeviceContext.of("car");
        String str = ctx.toString();
        assertTrue(str.contains("car"));
        assertFalse(str.contains("@"));
    }
}
