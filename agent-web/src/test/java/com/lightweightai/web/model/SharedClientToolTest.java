package com.lightweightai.web.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SharedClientTool - Client tool definition model")
class SharedClientToolTest {

    @Test
    @DisplayName("all fields round-trip via getters/setters")
    void allFieldsRoundTrip() {
        SharedClientTool tool = new SharedClientTool();
        tool.setKey("ns:get_position");
        tool.setNamespace("device");
        tool.setName("get_position");
        tool.setDescription("Get device position");
        tool.setOwner("system");
        tool.setVersion("1.0.0");
        tool.setInputSchema(Map.of("type", "object"));
        tool.setEnabled(true);
        tool.setMockResponse(Map.of("lat", 1.0, "lng", 2.0));
        tool.setCreatedBy("admin");
        tool.setUpdatedBy("admin");
        tool.setCreatedAt(1000L);
        tool.setUpdatedAt(2000L);

        assertEquals("ns:get_position", tool.getKey());
        assertEquals("device", tool.getNamespace());
        assertEquals("get_position", tool.getName());
        assertEquals("Get device position", tool.getDescription());
        assertEquals("system", tool.getOwner());
        assertEquals("1.0.0", tool.getVersion());
        assertEquals("object", tool.getInputSchema().get("type"));
        assertTrue(tool.isEnabled());
        assertEquals(1.0, tool.getMockResponse().get("lat"));
        assertEquals("admin", tool.getCreatedBy());
        assertEquals("admin", tool.getUpdatedBy());
        assertEquals(1000L, tool.getCreatedAt());
        assertEquals(2000L, tool.getUpdatedAt());
    }

    @Test
    @DisplayName("default boolean enabled is false")
    void defaultEnabled() {
        SharedClientTool tool = new SharedClientTool();
        assertFalse(tool.isEnabled());
    }
}
