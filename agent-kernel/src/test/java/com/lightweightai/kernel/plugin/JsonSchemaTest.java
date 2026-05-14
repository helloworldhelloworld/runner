package com.lightweightai.kernel.plugin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JsonSchema")
class JsonSchemaTest {

    private Map<String, Object> sampleSchemaMap;

    @BeforeEach
    void setUp() {
        sampleSchemaMap = new HashMap<>();
        sampleSchemaMap.put("type", "object");
        Map<String, Object> props = new HashMap<>();
        props.put("name", Map.of("type", "string"));
        sampleSchemaMap.put("properties", props);
        sampleSchemaMap.put("required", List.of("name"));
    }

    @Test
    @DisplayName("Constructor stores a defensive copy - modifying original does not affect schema")
    void constructorStoresDefensiveCopy() {
        Map<String, Object> original = new HashMap<>();
        original.put("type", "string");

        JsonSchema schema = new JsonSchema(original);

        // Mutate the original after construction
        original.put("type", "number");
        original.put("extra", "value");

        assertEquals("string", schema.getType(), "Schema type should not change when original map is mutated");
        assertFalse(schema.toMap().containsKey("extra"), "Schema should not contain keys added after construction");
    }

    @Test
    @DisplayName("toMap returns unmodifiable map")
    void toMapReturnsUnmodifiable() {
        JsonSchema schema = new JsonSchema(sampleSchemaMap);
        Map<String, Object> result = schema.toMap();

        assertThrows(UnsupportedOperationException.class, () -> result.put("extra", "val"));
    }

    @Test
    @DisplayName("getType returns the correct type value")
    void getTypeReturnsCorrectType() {
        JsonSchema schema = new JsonSchema(sampleSchemaMap);
        assertEquals("object", schema.getType());
    }

    @Test
    @DisplayName("getProperties returns properties map when present")
    void getPropertiesReturnsProperties() {
        JsonSchema schema = new JsonSchema(sampleSchemaMap);
        Map<String, Object> props = schema.getProperties();

        assertTrue(props.containsKey("name"));
        @SuppressWarnings("unchecked")
        Map<String, Object> nameProp = (Map<String, Object>) props.get("name");
        assertEquals("string", nameProp.get("type"));
    }

    @Test
    @DisplayName("getProperties returns empty map when no properties key")
    void getPropertiesReturnsEmptyWhenNone() {
        Map<String, Object> simple = Map.of("type", "string");
        JsonSchema schema = new JsonSchema(simple);

        Map<String, Object> props = schema.getProperties();
        assertNotNull(props);
        assertTrue(props.isEmpty());
    }

    @Test
    @DisplayName("isRequired returns true for a required property")
    void isRequiredReturnsTrueForRequiredProp() {
        JsonSchema schema = new JsonSchema(sampleSchemaMap);
        assertTrue(schema.isRequired("name"));
    }

    @Test
    @DisplayName("isRequired returns false for a non-required property")
    void isRequiredReturnsFalseForNonRequiredProp() {
        JsonSchema schema = new JsonSchema(sampleSchemaMap);
        assertFalse(schema.isRequired("age"));
    }

    @Test
    @DisplayName("isRequired returns false when no required list exists")
    void isRequiredReturnsFalseWhenNoRequiredList() {
        Map<String, Object> noRequired = Map.of("type", "object");
        JsonSchema schema = new JsonSchema(noRequired);

        assertFalse(schema.isRequired("anything"));
    }

    @Test
    @DisplayName("Builder builds schema with type and properties")
    void builderBuildsWithTypeAndProperties() {
        JsonSchema schema = JsonSchema.builder()
            .type("object")
            .addProperty("query", "string", "Search query")
            .addProperty("limit", "integer", "Max results")
            .build();

        assertEquals("object", schema.getType());
        Map<String, Object> props = schema.getProperties();
        assertEquals(2, props.size());
        assertTrue(props.containsKey("query"));
        assertTrue(props.containsKey("limit"));

        @SuppressWarnings("unchecked")
        Map<String, Object> queryProp = (Map<String, Object>) props.get("query");
        assertEquals("string", queryProp.get("type"));
        assertEquals("Search query", queryProp.get("description"));
    }

    @Test
    @DisplayName("Builder with required properties marks them correctly")
    void builderWithRequiredProperties() {
        JsonSchema schema = JsonSchema.builder()
            .type("object")
            .addProperty("name", "string", "Name")
            .addProperty("age", "integer", "Age")
            .required("name", "age")
            .build();

        assertTrue(schema.isRequired("name"));
        assertTrue(schema.isRequired("age"));
        assertFalse(schema.isRequired("email"));
    }

    @Test
    @DisplayName("string(description) factory creates string schema with description")
    void stringFactoryWithDescription() {
        JsonSchema schema = JsonSchema.string("A color value");

        assertEquals("string", schema.getType());
        assertEquals("A color value", schema.toMap().get("description"));
    }

    @Test
    @DisplayName("string(null) factory creates string schema without description key")
    void stringFactoryWithNull() {
        JsonSchema schema = JsonSchema.string(null);

        assertEquals("string", schema.getType());
        assertFalse(schema.toMap().containsKey("description"),
            "description key should be absent when null is passed");
    }

    @Test
    @DisplayName("object() factory creates minimal object schema")
    void objectFactory() {
        JsonSchema schema = JsonSchema.object();

        assertEquals("object", schema.getType());
        assertTrue(schema.getProperties().isEmpty());
    }

    @Test
    @DisplayName("Builder addProperty with Map stores the property schema verbatim")
    void builderAddPropertyWithMap() {
        Map<String, Object> enumProp = new HashMap<>();
        enumProp.put("type", "string");
        enumProp.put("enum", List.of("red", "green", "blue"));

        JsonSchema schema = JsonSchema.builder()
            .type("object")
            .addProperty("color", enumProp)
            .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> colorProp = (Map<String, Object>) schema.getProperties().get("color");
        assertEquals("string", colorProp.get("type"));
        @SuppressWarnings("unchecked")
        List<String> enumValues = (List<String>) colorProp.get("enum");
        assertEquals(List.of("red", "green", "blue"), enumValues);
    }
}
