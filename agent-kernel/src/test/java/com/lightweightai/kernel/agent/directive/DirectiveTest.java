package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DirectiveTest {

    @Test
    void fromToolName_withNamespace() {
        Directive d = Directive.fromToolName("call-1", "Camera.TakePhoto",
            Map.of("resolution", "1080p"), 30000);

        assertEquals("call-1", d.getDirectiveId());
        assertEquals("Camera", d.getNamespace());
        assertEquals("TakePhoto", d.getName());
        assertEquals("1080p", d.getPayload().get("resolution"));
        assertEquals(30000, d.getTimeoutMs());
    }

    @Test
    void fromToolName_withoutNamespace() {
        Directive d = Directive.fromToolName("call-2", "simple_tool",
            Map.of("key", "val"), 0);

        assertEquals("Default", d.getNamespace());
        assertEquals("simple_tool", d.getName());
    }

    @Test
    void toToolName() {
        Directive d = new Directive("id", "GPS", "GetLocation", Map.of(), 5000);
        assertEquals("GPS.GetLocation", d.toToolName());
    }

    @Test
    void constructor_nullArgs() {
        Directive d = new Directive("id", "ns", "name", null, 0);
        assertNotNull(d.getPayload());
        assertTrue(d.getPayload().isEmpty());
    }

    @Test
    void constructor_nullDirectiveId_throws() {
        assertThrows(NullPointerException.class, () ->
            new Directive(null, "ns", "name", null, 0));
    }

    // ==================== 补充测试 ====================

    @Test
    @DisplayName("构造器 null namespace 抛出 NullPointerException")
    void constructor_nullNamespace_throws() {
        assertThrows(NullPointerException.class,
                () -> new Directive("id", null, "name", Map.of(), 1000));
    }

    @Test
    @DisplayName("构造器 null name 抛出 NullPointerException")
    void constructor_nullName_throws() {
        assertThrows(NullPointerException.class,
                () -> new Directive("id", "ns", null, Map.of(), 1000));
    }

    @Test
    @DisplayName("fromToolName 多个点号时以第一个点分割")
    void fromToolName_multipleDots_splitsAtFirst() {
        Directive d = Directive.fromToolName("call-3", "Geo.Info.GetPos", Map.of(), 1000);
        assertEquals("Geo", d.getNamespace());
        assertEquals("Info.GetPos", d.getName());
    }

    @Test
    @DisplayName("fromToolName → toToolName 往返一致")
    void roundTrip_fromToolName_toToolName() {
        String original = "Navigation.StartRoute";
        Directive d = Directive.fromToolName("id", original, Map.of(), 1000);
        assertEquals(original, d.toToolName());
    }

    @Test
    @DisplayName("无 namespace 时 toToolName 带 Default 前缀")
    void toToolName_addsDefaultPrefix() {
        Directive d = Directive.fromToolName("id", "simpleCmd", Map.of(), 1000);
        assertEquals("Default.simpleCmd", d.toToolName());
    }

    @Test
    @DisplayName("无参构造器 + setter 正确设置所有字段")
    void shouldWorkWithSetters() {
        Directive d = new Directive();
        d.setDirectiveId("new-id");
        d.setNamespace("NewNS");
        d.setName("NewCmd");
        d.setPayload(Map.of("key", "val"));
        d.setTimeoutMs(9999);

        assertEquals("new-id", d.getDirectiveId());
        assertEquals("NewNS", d.getNamespace());
        assertEquals("NewCmd", d.getName());
        assertEquals("val", d.getPayload().get("key"));
        assertEquals(9999, d.getTimeoutMs());
    }

    @Test
    @DisplayName("toString 包含关键字段信息")
    void toString_containsFields() {
        Directive d = new Directive("id-1", "Camera", "TakePhoto", Map.of(), 5000);
        String str = d.toString();
        assertTrue(str.contains("id-1"));
        assertTrue(str.contains("Camera"));
        assertTrue(str.contains("TakePhoto"));
    }
}
