package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DirectiveDescriptor - 校验与字段提取")
class DirectiveDescriptorTest {

    @Test
    @DisplayName("正常构造保留所有字段")
    void constructorPreservesAllFields() {
        DirectiveDescriptor d = new DirectiveDescriptor(
                "TakePhoto", "camera.takePhoto", "camera.photoResult", "Camera", 30000, "1.0");

        assertEquals("TakePhoto", d.getTool());
        assertEquals("camera.takePhoto", d.getDownAction());
        assertEquals("camera.photoResult", d.getUpAction());
        assertEquals("Camera", d.getNamespace());
        assertEquals(30000, d.getTimeoutMs());
        assertEquals("1.0", d.getVersion());
    }

    @Test
    @DisplayName("toToolName 返回 tool 字段")
    void toToolNameReturnsTool() {
        DirectiveDescriptor d = new DirectiveDescriptor(
                "GetLocation", "gps.get", "gps.result", "GPS", 10000, "2.0");

        assertEquals("GetLocation", d.toToolName());
    }

    @Test
    @DisplayName("toDownlinkToolName 返回 downAction")
    void toDownlinkToolNameReturnsDownAction() {
        DirectiveDescriptor d = new DirectiveDescriptor(
                "ReadNFC", "nfc.read", "nfc.data", "NFC", 5000, "1.0");

        assertEquals("nfc.read", d.toDownlinkToolName());
    }

    @Test
    @DisplayName("timeoutMs <= 0 时使用默认 60000")
    void negativeTimeoutDefaultsTo60s() {
        DirectiveDescriptor d = new DirectiveDescriptor(
                "Tool", "down", "up", "NS", -1, "1.0");

        assertEquals(60_000, d.getTimeoutMs());
    }

    @Test
    @DisplayName("timeoutMs == 0 时使用默认 60000")
    void zeroTimeoutDefaultsTo60s() {
        DirectiveDescriptor d = new DirectiveDescriptor(
                "Tool", "down", "up", "NS", 0, "1.0");

        assertEquals(60_000, d.getTimeoutMs());
    }

    @Test
    @DisplayName("tool 为 null 时抛出 NullPointerException")
    void nullToolThrows() {
        assertThrows(NullPointerException.class, () ->
                new DirectiveDescriptor(null, "down", "up", "NS", 1000, "1.0"));
    }

    @Test
    @DisplayName("tool 为空白字符串时抛出 IllegalArgumentException")
    void blankToolThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new DirectiveDescriptor("  ", "down", "up", "NS", 1000, "1.0"));
    }

    @Test
    @DisplayName("downAction 为 null 时抛出 NullPointerException")
    void nullDownActionThrows() {
        assertThrows(NullPointerException.class, () ->
                new DirectiveDescriptor("Tool", null, "up", "NS", 1000, "1.0"));
    }

    @Test
    @DisplayName("namespace 为空白时抛出 IllegalArgumentException")
    void blankNamespaceThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new DirectiveDescriptor("Tool", "down", "up", "  ", 1000, "1.0"));
    }

    @Test
    @DisplayName("version 为空白时���出 IllegalArgumentException")
    void blankVersionThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new DirectiveDescriptor("Tool", "down", "up", "NS", 1000, ""));
    }

    @Test
    @DisplayName("字段前后空白会被 trim")
    void fieldsAreTrimmed() {
        DirectiveDescriptor d = new DirectiveDescriptor(
                " Photo ", " cam.down ", " cam.up ", " Cam ", 5000, " 1.0 ");

        assertEquals("Photo", d.getTool());
        assertEquals("cam.down", d.getDownAction());
        assertEquals("cam.up", d.getUpAction());
        assertEquals("Cam", d.getNamespace());
        assertEquals("1.0", d.getVersion());
    }
}
