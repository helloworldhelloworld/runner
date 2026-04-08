package com.lightweightai.kernel.agent.directive;

import com.lightweightai.kernel.agent.annotation.ClientTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DirectiveDescriptor 单元测试
 *
 * 覆盖关键路径：
 * - 构造函数字段校验（空字符串/null 抛异常,trim 处理）
 * - timeoutMs <= 0 时回退到默认 60000
 * - from(@ClientTool) 正确提取注解元信息
 * - toToolName / toDownlinkToolName
 */
@DisplayName("DirectiveDescriptor - ClientTool 元信息描述")
class DirectiveDescriptorTest {

    @ClientTool(
        toolName = "GetPosition",
        downAction = "GetPosition",
        upAction = "GetPositionResponse",
        namespace = "GeoInformation",
        timeoutMs = 5000,
        version = "1.0"
    )
    static class AnnotatedSample {
    }

    @ClientTool(
        toolName = "TakePhoto",
        downAction = "Capture",
        upAction = "CaptureResult",
        namespace = "Camera"
    )
    static class DefaultTimeoutSample {
    }

    @Test
    @DisplayName("构造器正常字段应被保留 (trim 空白)")
    void shouldTrimWhitespaceInFields() {
        DirectiveDescriptor d = new DirectiveDescriptor(
            " tool ", " down ", " up ", " ns ", 2000, " 1.0 ");

        assertEquals("tool", d.getTool());
        assertEquals("down", d.getDownAction());
        assertEquals("up", d.getUpAction());
        assertEquals("ns", d.getNamespace());
        assertEquals(2000, d.getTimeoutMs());
        assertEquals("1.0", d.getVersion());
    }

    @Test
    @DisplayName("timeoutMs <= 0 时应回退到默认 60000")
    void shouldFallbackTimeoutWhenNonPositive() {
        DirectiveDescriptor zero = new DirectiveDescriptor(
            "t", "d", "u", "ns", 0, "1.0");
        DirectiveDescriptor negative = new DirectiveDescriptor(
            "t", "d", "u", "ns", -5, "1.0");

        assertEquals(60_000L, zero.getTimeoutMs());
        assertEquals(60_000L, negative.getTimeoutMs());
    }

    @Test
    @DisplayName("空白或空字段应抛 IllegalArgumentException")
    void shouldRejectBlankFields() {
        assertThrows(IllegalArgumentException.class,
            () -> new DirectiveDescriptor("", "d", "u", "ns", 1, "1.0"));
        assertThrows(IllegalArgumentException.class,
            () -> new DirectiveDescriptor("t", "  ", "u", "ns", 1, "1.0"));
        assertThrows(IllegalArgumentException.class,
            () -> new DirectiveDescriptor("t", "d", "", "ns", 1, "1.0"));
        assertThrows(IllegalArgumentException.class,
            () -> new DirectiveDescriptor("t", "d", "u", "", 1, "1.0"));
        assertThrows(IllegalArgumentException.class,
            () -> new DirectiveDescriptor("t", "d", "u", "ns", 1, ""));
    }

    @Test
    @DisplayName("null 字段应抛 NullPointerException")
    void shouldRejectNullFields() {
        assertThrows(NullPointerException.class,
            () -> new DirectiveDescriptor(null, "d", "u", "ns", 1, "1.0"));
        assertThrows(NullPointerException.class,
            () -> new DirectiveDescriptor("t", null, "u", "ns", 1, "1.0"));
        assertThrows(NullPointerException.class,
            () -> new DirectiveDescriptor("t", "d", null, "ns", 1, "1.0"));
        assertThrows(NullPointerException.class,
            () -> new DirectiveDescriptor("t", "d", "u", null, 1, "1.0"));
        assertThrows(NullPointerException.class,
            () -> new DirectiveDescriptor("t", "d", "u", "ns", 1, null));
    }

    @Test
    @DisplayName("from(@ClientTool) 应正确读取注解各字段")
    void shouldBuildFromAnnotation() {
        ClientTool annotation = AnnotatedSample.class.getAnnotation(ClientTool.class);
        assertNotNull(annotation);

        DirectiveDescriptor d = DirectiveDescriptor.from(annotation);

        assertEquals("GetPosition", d.getTool());
        assertEquals("GetPosition", d.getDownAction());
        assertEquals("GetPositionResponse", d.getUpAction());
        assertEquals("GeoInformation", d.getNamespace());
        assertEquals(5000L, d.getTimeoutMs());
        assertEquals("1.0", d.getVersion());
    }

    @Test
    @DisplayName("from(null) 应抛 NullPointerException")
    void shouldRejectNullAnnotation() {
        assertThrows(NullPointerException.class, () -> DirectiveDescriptor.from(null));
    }

    @Test
    @DisplayName("from(@ClientTool) 未指定 timeoutMs 时应使用默认 60000")
    void shouldUseDefaultTimeoutFromAnnotation() {
        ClientTool annotation = DefaultTimeoutSample.class.getAnnotation(ClientTool.class);
        DirectiveDescriptor d = DirectiveDescriptor.from(annotation);

        assertEquals(60_000L, d.getTimeoutMs());
        assertEquals("1.0", d.getVersion());
    }

    @Test
    @DisplayName("toToolName 返回 tool 字段, toDownlinkToolName 返回 downAction")
    void shouldExposeToolAndDownlinkNames() {
        DirectiveDescriptor d = new DirectiveDescriptor(
            "GetPosition", "GetPosition", "GetPositionResponse", "Geo", 1000, "1.0");
        assertEquals("GetPosition", d.toToolName());
        assertEquals("GetPosition", d.toDownlinkToolName());
    }
}
