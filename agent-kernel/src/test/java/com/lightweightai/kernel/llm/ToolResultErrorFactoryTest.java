package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Additional ToolResult tests covering error factory methods, toApiFormat edge cases,
 * and toolUseId-bound variants not covered by the existing ToolResultTest.
 */
@DisplayName("ToolResult - Error factories and API format")
class ToolResultErrorFactoryTest {

    @Nested
    @DisplayName("error from exception")
    class ErrorFromExceptionTests {

        @Test
        @DisplayName("uses exception message as content")
        void usesExceptionMessage() {
            ToolResult result = ToolResult.error(new RuntimeException("connection refused"));
            assertTrue(result.isError());
            assertEquals("connection refused", result.getContent());
        }

        @Test
        @DisplayName("uses class name when message is null")
        void usesClassNameWhenNullMessage() {
            ToolResult result = ToolResult.error(new NullPointerException());
            assertTrue(result.isError());
            assertEquals("NullPointerException", result.getContent());
        }

        @Test
        @DisplayName("error with toolUseId from exception")
        void errorWithToolUseIdFromException() {
            ToolResult result = ToolResult.error("call_1", new IllegalStateException("bad state"));
            assertTrue(result.isError());
            assertEquals("call_1", result.getToolUseId());
            assertEquals("bad state", result.getContent());
        }
    }

    @Nested
    @DisplayName("success with toolUseId")
    class SuccessWithIdTests {

        @Test
        @DisplayName("success(toolUseId, content) preserves both")
        void successWithId() {
            ToolResult result = ToolResult.success("call_1", "42");
            assertFalse(result.isError());
            assertEquals("call_1", result.getToolUseId());
            assertEquals("42", result.getContent());
        }

        @Test
        @DisplayName("success(toolUseId, Object) converts to string")
        void successWithIdAndObject() {
            ToolResult result = ToolResult.success("call_1", 42);
            assertEquals("42", result.getContent());
        }

        @Test
        @DisplayName("success(toolUseId, null) produces 'null' string")
        void successWithIdAndNull() {
            ToolResult result = ToolResult.success("call_1", (Object) null);
            assertEquals("null", result.getContent());
        }
    }

    @Nested
    @DisplayName("toApiFormat")
    class ApiFormatTests {

        @Test
        @DisplayName("error result includes is_error flag")
        void errorIncludesIsErrorFlag() {
            ToolResult result = ToolResult.error("call_1", "timeout");
            Map<String, Object> api = result.toApiFormat();

            assertEquals(true, api.get("is_error"));
            assertEquals("tool_result", api.get("type"));
            assertEquals("call_1", api.get("tool_use_id"));
        }

        @Test
        @DisplayName("success result does not include is_error flag")
        void successOmitsIsErrorFlag() {
            ToolResult result = ToolResult.success("call_1", "ok");
            Map<String, Object> api = result.toApiFormat();

            assertFalse(api.containsKey("is_error"));
        }

        @Test
        @DisplayName("toApiFormat without toolUseId throws IllegalStateException")
        void missingToolUseIdThrows() {
            ToolResult result = ToolResult.success("hello");
            assertThrows(IllegalStateException.class, result::toApiFormat);
        }

        @Test
        @DisplayName("toApiFormat with blank toolUseId throws IllegalStateException")
        void blankToolUseIdThrows() {
            ToolResult result = ToolResult.success("  ", "hello");
            assertThrows(IllegalStateException.class, result::toApiFormat);
        }
    }

    @Test
    @DisplayName("toString contains key fields")
    void toStringContainsFields() {
        ToolResult result = ToolResult.success("call_1", "42");
        String str = result.toString();
        assertTrue(str.contains("call_1"));
        assertTrue(str.contains("42"));
        assertTrue(str.contains("isError=false"));
    }
}
