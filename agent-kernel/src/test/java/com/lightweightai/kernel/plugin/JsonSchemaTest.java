package com.lightweightai.kernel.plugin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JsonSchema — Builder/Factory/Query")
class JsonSchemaTest {

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("builder 构建带属性和 required 的 object schema")
        @SuppressWarnings("unchecked")
        void buildsObjectSchemaWithPropertiesAndRequired() {
            JsonSchema schema = JsonSchema.builder()
                    .type("object")
                    .addProperty("name", "string", "The user's name")
                    .addProperty("age", "integer", "The user's age")
                    .required("name")
                    .build();

            assertEquals("object", schema.getType());

            Map<String, Object> props = schema.getProperties();
            assertEquals(2, props.size());

            Map<String, Object> nameProp = (Map<String, Object>) props.get("name");
            assertEquals("string", nameProp.get("type"));
            assertEquals("The user's name", nameProp.get("description"));

            assertTrue(schema.isRequired("name"));
            assertFalse(schema.isRequired("age"));
        }

        @Test
        @DisplayName("builder 添加自定义 property schema map")
        @SuppressWarnings("unchecked")
        void buildsWithCustomPropertySchema() {
            Map<String, Object> enumProp = Map.of(
                    "type", "string",
                    "enum", java.util.List.of("low", "medium", "high")
            );

            JsonSchema schema = JsonSchema.builder()
                    .type("object")
                    .addProperty("priority", enumProp)
                    .build();

            Map<String, Object> props = schema.getProperties();
            Map<String, Object> priorityProp = (Map<String, Object>) props.get("priority");
            assertEquals("string", priorityProp.get("type"));
            assertNotNull(priorityProp.get("enum"));
        }

        @Test
        @DisplayName("builder 无属性时生成无 properties 的 schema")
        void buildsEmptyObjectSchema() {
            JsonSchema schema = JsonSchema.builder()
                    .type("object")
                    .build();

            assertEquals("object", schema.getType());
            assertTrue(schema.getProperties().isEmpty());
        }

        @Test
        @DisplayName("builder 多个 required 字段")
        void buildsWithMultipleRequired() {
            JsonSchema schema = JsonSchema.builder()
                    .type("object")
                    .addProperty("a", "string", null)
                    .addProperty("b", "string", null)
                    .addProperty("c", "string", null)
                    .required("a", "b")
                    .build();

            assertTrue(schema.isRequired("a"));
            assertTrue(schema.isRequired("b"));
            assertFalse(schema.isRequired("c"));
        }
    }

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethodTests {

        @Test
        @DisplayName("string() 创建 string schema 带描述")
        void stringSchemaWithDescription() {
            JsonSchema schema = JsonSchema.string("A search query");

            assertEquals("string", schema.getType());
            assertEquals("A search query", schema.toMap().get("description"));
        }

        @Test
        @DisplayName("string(null) 创建无描述的 string schema")
        void stringSchemaWithoutDescription() {
            JsonSchema schema = JsonSchema.string(null);

            assertEquals("string", schema.getType());
            assertFalse(schema.toMap().containsKey("description"));
        }

        @Test
        @DisplayName("object() 创建空 object schema")
        void objectSchema() {
            JsonSchema schema = JsonSchema.object();

            assertEquals("object", schema.getType());
        }
    }

    @Nested
    @DisplayName("Query Methods")
    class QueryTests {

        @Test
        @DisplayName("toMap 返回不可变拷贝")
        void toMapReturnsUnmodifiable() {
            JsonSchema schema = JsonSchema.builder()
                    .type("object")
                    .addProperty("x", "string", null)
                    .build();

            Map<String, Object> map = schema.toMap();
            assertThrows(UnsupportedOperationException.class, () ->
                    map.put("injected", "evil"));
        }

        @Test
        @DisplayName("getType 返回 schema type")
        void getTypeReturnsCorrectly() {
            JsonSchema stringSchema = new JsonSchema(Map.of("type", "string"));
            assertEquals("string", stringSchema.getType());

            JsonSchema noType = new JsonSchema(Map.of());
            assertNull(noType.getType());
        }

        @Test
        @DisplayName("getProperties 无 properties 时返回空 map")
        void getPropertiesReturnsEmptyWhenAbsent() {
            JsonSchema schema = new JsonSchema(Map.of("type", "string"));
            assertTrue(schema.getProperties().isEmpty());
        }

        @Test
        @DisplayName("isRequired 无 required 列表时返回 false")
        void isRequiredReturnsFalseWhenNoRequiredList() {
            JsonSchema schema = JsonSchema.builder()
                    .type("object")
                    .addProperty("x", "string", null)
                    .build();

            assertFalse(schema.isRequired("x"));
        }

        @Test
        @DisplayName("toString 包含 schema 内容")
        void toStringContainsSchema() {
            JsonSchema schema = JsonSchema.string("test");
            assertTrue(schema.toString().contains("JsonSchema"));
        }
    }

    @Nested
    @DisplayName("构造函数")
    class ConstructorTests {

        @Test
        @DisplayName("构造函数进行防御性拷贝")
        void constructorMakesDefensiveCopy() {
            var mutable = new java.util.HashMap<String, Object>();
            mutable.put("type", "string");

            JsonSchema schema = new JsonSchema(mutable);
            mutable.put("injected", "evil");

            assertFalse(schema.toMap().containsKey("injected"));
        }
    }
}
