package com.lightweightai.kernel.plugin;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JsonSchemaGenerator - 反射式 JSON Schema 生成")
class JsonSchemaGeneratorTest {

    // ==================== 基础类型映射 ====================

    @Test
    @DisplayName("String 类型映射为 string")
    void stringType() {
        JsonSchema schema = JsonSchemaGenerator.generate(String.class);
        assertEquals("string", schema.getType());
    }

    @Test
    @DisplayName("int/Integer 映射为 integer")
    void integerType() {
        JsonSchema schema = JsonSchemaGenerator.generate(Integer.class);
        assertEquals("integer", schema.getType());
    }

    @Test
    @DisplayName("double/Double 映射为 number")
    void numberType() {
        JsonSchema schema = JsonSchemaGenerator.generate(Double.class);
        assertEquals("number", schema.getType());
    }

    @Test
    @DisplayName("boolean/Boolean 映射为 boolean")
    void booleanType() {
        JsonSchema schema = JsonSchemaGenerator.generate(Boolean.class);
        assertEquals("boolean", schema.getType());
    }

    // ==================== 对象类型 ====================

    static class SimpleBean {
        private String name;
        private int age;
        private boolean active;
    }

    @Test
    @DisplayName("简单 POJO 生成 object schema")
    @SuppressWarnings("unchecked")
    void simpleBean() {
        JsonSchema schema = JsonSchemaGenerator.generate(SimpleBean.class);

        assertEquals("object", schema.getType());
        Map<String, Object> props = schema.getProperties();
        assertNotNull(props);
        assertTrue(props.containsKey("name"));
        assertTrue(props.containsKey("age"));
        assertTrue(props.containsKey("active"));

        Map<String, Object> nameProp = (Map<String, Object>) props.get("name");
        assertEquals("string", nameProp.get("type"));

        Map<String, Object> ageProp = (Map<String, Object>) props.get("age");
        assertEquals("integer", ageProp.get("type"));

        Map<String, Object> activeProp = (Map<String, Object>) props.get("active");
        assertEquals("boolean", activeProp.get("type"));
    }

    // ==================== @JsonProperty 注解 ====================

    static class AnnotatedBean {
        @JsonProperty(value = "user_name", required = true)
        private String userName;

        @JsonProperty("user_age")
        private int userAge;
    }

    @Test
    @DisplayName("@JsonProperty 重命名和 required 标记")
    void jsonPropertyAnnotation() {
        JsonSchema schema = JsonSchemaGenerator.generate(AnnotatedBean.class);

        Map<String, Object> props = schema.getProperties();
        assertTrue(props.containsKey("user_name"));
        assertTrue(props.containsKey("user_age"));
        assertFalse(props.containsKey("userName"));

        assertTrue(schema.isRequired("user_name"));
        assertFalse(schema.isRequired("user_age"));
    }

    // ==================== @JsonPropertyDescription 注解 ====================

    static class DescribedBean {
        @JsonSchemaGenerator.JsonPropertyDescription("The user's full name")
        private String name;
    }

    @Test
    @DisplayName("@JsonPropertyDescription 添加描述")
    @SuppressWarnings("unchecked")
    void propertyDescription() {
        JsonSchema schema = JsonSchemaGenerator.generate(DescribedBean.class);

        Map<String, Object> props = schema.getProperties();
        Map<String, Object> nameProp = (Map<String, Object>) props.get("name");
        assertEquals("The user's full name", nameProp.get("description"));
    }

    // ==================== 集合和数组 ====================

    static class CollectionBean {
        private List<String> tags;
        private int[] scores;
    }

    @Test
    @DisplayName("List 类型映射为 array + items")
    @SuppressWarnings("unchecked")
    void listType() {
        JsonSchema schema = JsonSchemaGenerator.generate(CollectionBean.class);

        Map<String, Object> props = schema.getProperties();
        Map<String, Object> tagsProp = (Map<String, Object>) props.get("tags");
        assertEquals("array", tagsProp.get("type"));

        Map<String, Object> items = (Map<String, Object>) tagsProp.get("items");
        assertNotNull(items);
        assertEquals("string", items.get("type"));
    }

    @Test
    @DisplayName("数组类型映射为 array + items")
    @SuppressWarnings("unchecked")
    void arrayType() {
        JsonSchema schema = JsonSchemaGenerator.generate(CollectionBean.class);

        Map<String, Object> props = schema.getProperties();
        Map<String, Object> scoresProp = (Map<String, Object>) props.get("scores");
        assertEquals("array", scoresProp.get("type"));

        Map<String, Object> items = (Map<String, Object>) scoresProp.get("items");
        assertNotNull(items);
        assertEquals("integer", items.get("type"));
    }

    // ==================== 嵌套对象 ====================

    static class Address {
        private String city;
        private String street;
    }

    static class Person {
        private String name;
        private Address address;
    }

    @Test
    @DisplayName("嵌套对象生成嵌套 schema")
    @SuppressWarnings("unchecked")
    void nestedObject() {
        JsonSchema schema = JsonSchemaGenerator.generate(Person.class);

        Map<String, Object> props = schema.getProperties();
        Map<String, Object> addressProp = (Map<String, Object>) props.get("address");
        assertEquals("object", addressProp.get("type"));

        Map<String, Object> addrProps = (Map<String, Object>) addressProp.get("properties");
        assertNotNull(addrProps);
        assertTrue(addrProps.containsKey("city"));
        assertTrue(addrProps.containsKey("street"));
    }

    // ==================== 循环引用 ====================

    static class SelfRef {
        private String value;
        private SelfRef next;
    }

    @Test
    @DisplayName("循环引用不导致栈溢出")
    void circularReference() {
        assertDoesNotThrow(() -> JsonSchemaGenerator.generate(SelfRef.class));

        JsonSchema schema = JsonSchemaGenerator.generate(SelfRef.class);
        assertEquals("object", schema.getType());
    }

    // ==================== 继承 ====================

    static class Base {
        private String baseField;
    }

    static class Child extends Base {
        private String childField;
    }

    @Test
    @DisplayName("继承类包含父类字段")
    void inheritance() {
        JsonSchema schema = JsonSchemaGenerator.generate(Child.class);

        Map<String, Object> props = schema.getProperties();
        assertTrue(props.containsKey("baseField"));
        assertTrue(props.containsKey("childField"));
    }

    // ==================== 带描述的 generate ====================

    @Test
    @DisplayName("generate 带描述参数")
    void generateWithDescription() {
        JsonSchema schema = JsonSchemaGenerator.generate(SimpleBean.class, "A simple bean");
        assertEquals("A simple bean", schema.toMap().get("description"));
    }

    @Test
    @DisplayName("generate 描述为 null 时不添加")
    void generateWithNullDescription() {
        JsonSchema schema = JsonSchemaGenerator.generate(SimpleBean.class, null);
        assertFalse(schema.toMap().containsKey("description"));
    }

    // ==================== 边界情况 ====================

    @Test
    @DisplayName("null 类型抛异常")
    void nullTypeThrows() {
        assertThrows(IllegalArgumentException.class, () -> JsonSchemaGenerator.generate(null));
    }

    // ==================== 更多基础类型覆盖 ====================

    @Test
    @DisplayName("Long 映射为 integer")
    void longType() {
        assertEquals("integer", JsonSchemaGenerator.generate(Long.class).getType());
    }

    @Test
    @DisplayName("Float 映射为 number")
    void floatType() {
        assertEquals("number", JsonSchemaGenerator.generate(Float.class).getType());
    }

    @Test
    @DisplayName("Short 映射为 integer")
    void shortType() {
        assertEquals("integer", JsonSchemaGenerator.generate(Short.class).getType());
    }

    @Test
    @DisplayName("Byte 映射为 integer")
    void byteType() {
        assertEquals("integer", JsonSchemaGenerator.generate(Byte.class).getType());
    }

    @Test
    @DisplayName("Character 映射为 string")
    void charType() {
        assertEquals("string", JsonSchemaGenerator.generate(Character.class).getType());
    }
}
