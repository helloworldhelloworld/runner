package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DirectiveDescriptor - Directive metadata extraction")
class DirectiveDescriptorTest {

    @Test
    @DisplayName("constructs with all valid fields")
    void shouldConstructWithValidFields() {
        DirectiveDescriptor desc = new DirectiveDescriptor(
                "camera_photo", "TakePhoto", "PhotoTaken",
                "Camera", 30_000, "1.0");

        assertEquals("camera_photo", desc.getTool());
        assertEquals("TakePhoto", desc.getDownAction());
        assertEquals("PhotoTaken", desc.getUpAction());
        assertEquals("Camera", desc.getNamespace());
        assertEquals(30_000, desc.getTimeoutMs());
        assertEquals("1.0", desc.getVersion());
    }

    @Test
    @DisplayName("toToolName returns tool field")
    void toToolNameReturnsTool() {
        DirectiveDescriptor desc = new DirectiveDescriptor(
                "my_tool", "Down", "Up", "NS", 5000, "v1");
        assertEquals("my_tool", desc.toToolName());
    }

    @Test
    @DisplayName("toDownlinkToolName returns downAction")
    void toDownlinkToolNameReturnsDownAction() {
        DirectiveDescriptor desc = new DirectiveDescriptor(
                "my_tool", "ExecuteCmd", "CmdResult", "Device", 5000, "v1");
        assertEquals("ExecuteCmd", desc.toDownlinkToolName());
    }

    @Test
    @DisplayName("defaults timeout to 60000 when zero or negative")
    void shouldDefaultTimeoutWhenZeroOrNegative() {
        DirectiveDescriptor zero = new DirectiveDescriptor(
                "t", "d", "u", "ns", 0, "v1");
        assertEquals(60_000, zero.getTimeoutMs());

        DirectiveDescriptor negative = new DirectiveDescriptor(
                "t", "d", "u", "ns", -1, "v1");
        assertEquals(60_000, negative.getTimeoutMs());
    }

    @Test
    @DisplayName("trims whitespace from text fields")
    void shouldTrimWhitespace() {
        DirectiveDescriptor desc = new DirectiveDescriptor(
                "  my_tool  ", "  Down  ", "  Up  ", "  NS  ", 5000, "  v1  ");
        assertEquals("my_tool", desc.getTool());
        assertEquals("Down", desc.getDownAction());
        assertEquals("Up", desc.getUpAction());
        assertEquals("NS", desc.getNamespace());
        assertEquals("v1", desc.getVersion());
    }

    @Test
    @DisplayName("rejects null tool name")
    void shouldRejectNullTool() {
        assertThrows(NullPointerException.class, () ->
                new DirectiveDescriptor(null, "d", "u", "ns", 5000, "v1"));
    }

    @Test
    @DisplayName("rejects blank downAction")
    void shouldRejectBlankDownAction() {
        assertThrows(IllegalArgumentException.class, () ->
                new DirectiveDescriptor("tool", "  ", "u", "ns", 5000, "v1"));
    }

    @Test
    @DisplayName("rejects blank namespace")
    void shouldRejectBlankNamespace() {
        assertThrows(IllegalArgumentException.class, () ->
                new DirectiveDescriptor("tool", "d", "u", "", 5000, "v1"));
    }

    @Test
    @DisplayName("rejects null version")
    void shouldRejectNullVersion() {
        assertThrows(NullPointerException.class, () ->
                new DirectiveDescriptor("tool", "d", "u", "ns", 5000, null));
    }
}
