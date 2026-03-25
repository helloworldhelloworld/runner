package com.lightweightai.kernel.plugin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FunctionResult - 插件函数执行结果")
class FunctionResultTest {

    @Nested
    @DisplayName("success() 工厂方法")
    class SuccessFactory {

        @Test
        @DisplayName("创建成功结果")
        void createSuccess() {
            FunctionResult result = FunctionResult.success("hello");

            assertTrue(result.isSuccess());
            assertEquals("hello", result.getValue());
            assertNull(result.getError());
        }

        @Test
        @DisplayName("成功结果可以包含 null 值")
        void successWithNullValue() {
            FunctionResult result = FunctionResult.success(null);

            assertTrue(result.isSuccess());
            assertNull(result.getValue());
        }

        @Test
        @DisplayName("成功结果可以包含复杂对象")
        void successWithComplexObject() {
            Object value = java.util.Map.of("key", "value");
            FunctionResult result = FunctionResult.success(value);

            assertTrue(result.isSuccess());
            assertEquals(value, result.getValue());
        }
    }

    @Nested
    @DisplayName("error(String) 工厂方法")
    class ErrorStringFactory {

        @Test
        @DisplayName("创建错误结果")
        void createError() {
            FunctionResult result = FunctionResult.error("something went wrong");

            assertFalse(result.isSuccess());
            assertNull(result.getValue());
            assertEquals("something went wrong", result.getError());
        }

        @Test
        @DisplayName("错误消息可以为 null")
        void errorWithNullMessage() {
            FunctionResult result = FunctionResult.error((String) null);

            assertFalse(result.isSuccess());
            assertNull(result.getError());
        }
    }

    @Nested
    @DisplayName("error(Throwable) 工厂方法")
    class ErrorThrowableFactory {

        @Test
        @DisplayName("从异常创建错误结果")
        void createFromException() {
            RuntimeException ex = new RuntimeException("boom");
            FunctionResult result = FunctionResult.error(ex);

            assertFalse(result.isSuccess());
            assertNull(result.getValue());
            assertEquals("boom", result.getError());
        }

        @Test
        @DisplayName("从无消息的异常创建错误结果")
        void createFromExceptionWithNoMessage() {
            RuntimeException ex = new RuntimeException();
            FunctionResult result = FunctionResult.error(ex);

            assertFalse(result.isSuccess());
            assertNull(result.getError());
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("成功结果的 toString 包含 success=true 和值")
        void successToString() {
            FunctionResult result = FunctionResult.success("data");
            String str = result.toString();

            assertTrue(str.contains("success=true"));
            assertTrue(str.contains("data"));
        }

        @Test
        @DisplayName("错误结果的 toString 包含 success=false 和错误信息")
        void errorToString() {
            FunctionResult result = FunctionResult.error("fail");
            String str = result.toString();

            assertTrue(str.contains("success=false"));
            assertTrue(str.contains("fail"));
        }
    }
}
