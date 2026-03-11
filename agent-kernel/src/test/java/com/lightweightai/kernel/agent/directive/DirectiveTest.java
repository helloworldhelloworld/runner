package com.lightweightai.kernel.agent.directive;

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
}
