package com.lightweightai.kernel.plugin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FunctionParameter - 函数参数定义")
class FunctionParameterTest {

    @Nested
    @DisplayName("Builder 构建")
    class BuilderTests {

        @Test
        @DisplayName("构建包含所有字段的参数")
        void buildWithAllFields() {
            FunctionParameter param = FunctionParameter.builder("age", FunctionParameter.ParameterType.INTEGER)
                .description("User age")
                .required(false)
                .defaultValue(18)
                .build();

            assertEquals("age", param.getName());
            assertEquals(FunctionParameter.ParameterType.INTEGER, param.getType());
            assertEquals("User age", param.getDescription());
            assertFalse(param.isRequired());
            assertEquals(18, param.getDefaultValue());
        }

        @Test
        @DisplayName("默认 required 为 true")
        void defaultRequiredIsTrue() {
            FunctionParameter param = FunctionParameter.builder("name", FunctionParameter.ParameterType.STRING)
                .build();

            assertTrue(param.isRequired());
        }

        @Test
        @DisplayName("未设置的可选字段为 null")
        void optionalFieldsAreNull() {
            FunctionParameter param = FunctionParameter.builder("key", FunctionParameter.ParameterType.STRING)
                .build();

            assertNull(param.getDescription());
            assertNull(param.getDefaultValue());
        }

        @Test
        @DisplayName("不同类型的参数构建")
        void buildWithDifferentTypes() {
            FunctionParameter stringParam = FunctionParameter.builder("s", FunctionParameter.ParameterType.STRING).build();
            FunctionParameter numberParam = FunctionParameter.builder("n", FunctionParameter.ParameterType.NUMBER).build();
            FunctionParameter boolParam = FunctionParameter.builder("b", FunctionParameter.ParameterType.BOOLEAN).build();
            FunctionParameter objParam = FunctionParameter.builder("o", FunctionParameter.ParameterType.OBJECT).build();
            FunctionParameter arrParam = FunctionParameter.builder("a", FunctionParameter.ParameterType.ARRAY).build();

            assertEquals(FunctionParameter.ParameterType.STRING, stringParam.getType());
            assertEquals(FunctionParameter.ParameterType.NUMBER, numberParam.getType());
            assertEquals(FunctionParameter.ParameterType.BOOLEAN, boolParam.getType());
            assertEquals(FunctionParameter.ParameterType.OBJECT, objParam.getType());
            assertEquals(FunctionParameter.ParameterType.ARRAY, arrParam.getType());
        }
    }

    @Nested
    @DisplayName("ParameterType 枚举")
    class ParameterTypeTests {

        @Test
        @DisplayName("jsonType 值正确映射")
        void jsonTypeValues() {
            assertEquals("string", FunctionParameter.ParameterType.STRING.getJsonType());
            assertEquals("number", FunctionParameter.ParameterType.NUMBER.getJsonType());
            assertEquals("integer", FunctionParameter.ParameterType.INTEGER.getJsonType());
            assertEquals("boolean", FunctionParameter.ParameterType.BOOLEAN.getJsonType());
            assertEquals("object", FunctionParameter.ParameterType.OBJECT.getJsonType());
            assertEquals("array", FunctionParameter.ParameterType.ARRAY.getJsonType());
        }

        @Test
        @DisplayName("包含所有预期的枚举值")
        void allEnumValues() {
            FunctionParameter.ParameterType[] values = FunctionParameter.ParameterType.values();
            assertEquals(6, values.length);
        }

        @Test
        @DisplayName("valueOf 可以解析枚举名称")
        void valueOfWorks() {
            assertEquals(FunctionParameter.ParameterType.STRING, FunctionParameter.ParameterType.valueOf("STRING"));
            assertEquals(FunctionParameter.ParameterType.INTEGER, FunctionParameter.ParameterType.valueOf("INTEGER"));
        }
    }
}
