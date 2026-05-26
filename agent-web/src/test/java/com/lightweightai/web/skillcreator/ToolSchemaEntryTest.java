package com.lightweightai.web.skillcreator;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ToolSchemaEntryTest {

    @Test
    void defaultConstructor() {
        assertNull(new ToolSchemaEntry().getId());
    }

    @Test
    void allFieldsRoundTrip() {
        ToolSchemaEntry e = new ToolSchemaEntry();
        e.setId("tool-001"); e.setName("GeoInfo.GetPosition"); e.setDescription("获取位置");
        e.setCategory("地理信息"); e.setStatus("enabled");
        e.setInputSchemaJson("{\"type\":\"object\"}"); e.setOutputSchemaJson("{\"type\":\"string\"}");
        assertEquals("tool-001", e.getId());
        assertEquals("GeoInfo.GetPosition", e.getName());
        assertEquals("enabled", e.getStatus());
    }

    @Test
    void toMapContainsFields() {
        ToolSchemaEntry e = new ToolSchemaEntry();
        e.setId("id-1"); e.setName("tool"); e.setDescription("desc"); e.setStatus("enabled");
        Map<String, Object> map = e.toMap();
        assertEquals("id-1", map.get("id"));
        assertEquals("tool", map.get("name"));
    }

    @Test
    void toMapSkipsNullFields() {
        ToolSchemaEntry e = new ToolSchemaEntry();
        e.setName("minimal");
        Map<String, Object> map = e.toMap();
        assertFalse(map.containsKey("id"));
        assertTrue(map.containsKey("name"));
    }

    @Test
    void toMapDefaultsStatusToEnabled() {
        ToolSchemaEntry e = new ToolSchemaEntry();
        e.setName("test"); e.setStatus(null);
        assertEquals("enabled", e.toMap().get("status"));
    }
}
