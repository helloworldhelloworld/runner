package com.lightweightai.kernel.plugin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JsonSchema - JSON Schema representation and builder")
class JsonSchemaTest {

    @Nested
    @DisplayName("Static factories")
    class FactoryTests {

        @Test
        @DisplayName("object() creates schema with type=object")
        void objectFactory() {
            JsonSchema schema = JsonSchema.object();
            assertEquals("object", schema.getType());
            assertTrue(schema.getProperties().isEmpty());
        }

        @Test
        @DisplayName("string() creates schema with type=string and description")
        void stringFactory() {
            JsonSchema schema = JsonSchema.string("a name");
            assertEquals("string", schema.getType());
            Map<String, Object> map = schema.toMap();
            assertEquals("a name", map.get("description"));
        }

        @Test
        @DisplayName("string(null) creates schema without description")
        void stringNullDescription() {
            JsonSchema schema = JsonSchema.string(null);
            assertEquals("string", schema.getType());
            assertNull(schema.toMap().get("description"));
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("builds object schema with properties and required")
        void buildsWithPropertiesAndRequired() {
            JsonSchema schema = JsonSchema.builder()
                    .type("object")
                    .addProperty("name", "string", "person name")
                    .addProperty("age", "integer", "person age")
                    .required("name")
                    .build();

            assertEquals("object", schema.getType());

            Map<String, Object> props = schema.getProperties();
            assertEquals(2, props.size());
            assertTrue(props.containsKey("name"));
            assertTrue(props.containsKey("age"));

            assertTrue(schema.isRequired("name"));
            assertFalse(schema.isRequired("age"));
        }

        @Test
        @DisplayName("addProperty with Map schema")
        void addPropertyWithMap() {
            Map<String, Object> enumSchema = Map.of(
                    "type", "string",
                    "enum", java.util.List.of("a", "b", "c"));

            JsonSchema schema = JsonSchema.builder()
                    .type("object")
                    .addProperty("choice", enumSchema)
                    .build();

            Map<String, Object> props = schema.getProperties();
            assertTrue(props.containsKey("choice"));
        }

        @Test
        @DisplayName("multiple required fields")
        void multipleRequired() {
            JsonSchema schema = JsonSchema.builder()
                    .type("object")
                    .addProperty("a", "string", null)
                    .addProperty("b", "string", null)
                    .required("a", "b")
                    .build();

            assertTrue(schema.isRequired("a"));
            assertTrue(schema.isRequired("b"));
        }

        @Test
        @DisplayName("no properties yields schema without properties key")
        void noProperties() {
            JsonSchema schema = JsonSchema.builder()
                    .type("object")
                    .build();

            assertTrue(schema.getProperties().isEmpty());
        }
    }

    @Nested
    @DisplayName("toMap and isRequired")
    class MapTests {

        @Test
        @DisplayName("toMap returns unmodifiable view")
        void toMapUnmodifiable() {
            JsonSchema schema = JsonSchema.object();
            assertThrows(UnsupportedOperationException.class, () ->
                    schema.toMap().put("extra", "value"));
        }

        @Test
        @DisplayName("isRequired returns false when no required field exists")
        void isRequiredNoRequiredField() {
            JsonSchema schema = JsonSchema.object();
            assertFalse(schema.isRequired("anything"));
        }

        @Test
        @DisplayName("constructor copies input map defensively")
        void defensiveCopy() {
            java.util.Map<String, Object> input = new java.util.HashMap<>();
            input.put("type", "string");

            JsonSchema schema = new JsonSchema(input);
            input.put("type", "integer");

            assertEquals("string", schema.getType());
        }
    }

    @Test
    @DisplayName("toString includes schema content")
    void toStringContent() {
        JsonSchema schema = JsonSchema.object();
        String str = schema.toString();
        assertTrue(str.contains("object"));
    }
}
