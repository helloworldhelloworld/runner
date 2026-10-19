package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolResult 全路径覆盖测试。
 *
 * 已有 ToolResultTest 覆盖基本 success/error 创建。
 * 此测试补充：
 * - withToolUseId 不可变性（原对象不变）
 * - structuredContent 完整链路
 * - toApiFormat 正常/异常场景
 * - 异常工厂方法
 * - content null 防御
 */
@DisplayName("ToolResult - 全路径覆盖")
class ToolResultFullTest {

    @Nested
    @DisplayName("withToolUseId")
    class WithToolUseId {

        @Test
        @DisplayName("返回新实例，不修改原对象")
        void returnsNewInstance() {
            ToolResult original = ToolResult.success("hello");
            ToolResult bound = original.withToolUseId("use-1");

            assertNull(original.getToolUseId(), "Original must not be mutated");
            assertEquals("use-1", bound.getToolUseId());
            assertEquals("hello", bound.getContent());
            assertFalse(bound.isError());
        }

        @Test
        @DisplayName("preserves structuredContent through withToolUseId")
        void preservesStructuredContent() {
            Map<String, Object> structured = Map.of("key", "value");
            ToolResult original = ToolResult.success("text", structured);
            ToolResult bound = original.withToolUseId("use-2");

            assertTrue(bound.hasStructuredContent());
            assertEquals("value", bound.getStructuredContent().get("key"));
        }

        @Test
        @DisplayName("preserves error flag through withToolUseId")
        void preservesErrorFlag() {
            ToolResult original = ToolResult.error("something broke");
            ToolResult bound = original.withToolUseId("use-err");

            assertTrue(bound.isError());
            assertEquals("something broke", bound.getContent());
            assertEquals("use-err", bound.getToolUseId());
        }
    }

    @Nested
    @DisplayName("structuredContent")
    class StructuredContent {

        @Test
        @DisplayName("success 带结构化内容")
        void successWithStructuredContent() {
            Map<String, Object> data = Map.of("header", "h1", "payload", Map.of("a", 1));
            ToolResult result = ToolResult.success("raw text", data);

            assertTrue(result.hasStructuredContent());
            assertEquals("h1", result.getStructuredContent().get("header"));
            assertEquals("raw text", result.getContent());
        }

        @Test
        @DisplayName("无结构化内容时 hasStructuredContent 为 false")
        void noStructuredContent() {
            ToolResult result = ToolResult.success("plain");
            assertFalse(result.hasStructuredContent());
            assertNull(result.getStructuredContent());
        }

        @Test
        @DisplayName("空 Map structuredContent 视为无")
        void emptyMapIsNotStructured() {
            ToolResult result = ToolResult.success("text", Map.of());
            assertFalse(result.hasStructuredContent());
        }
    }

    @Nested
    @DisplayName("toApiFormat")
    class ToApiFormat {

        @Test
        @DisplayName("有 toolUseId 时正确生成 API 格式")
        void validApiFormat() {
            ToolResult result = ToolResult.success("use-abc", "weather is sunny");
            Map<String, Object> api = result.toApiFormat();

            assertEquals("tool_result", api.get("type"));
            assertEquals("use-abc", api.get("tool_use_id"));
            assertEquals("weather is sunny", api.get("content"));
            assertFalse(api.containsKey("is_error"));
        }

        @Test
        @DisplayName("error 结果包含 is_error=true")
        void errorApiFormatIncludesFlag() {
            ToolResult result = ToolResult.error("use-err", "boom");
            Map<String, Object> api = result.toApiFormat();

            assertEquals(true, api.get("is_error"));
        }

        @Test
        @DisplayName("无 toolUseId 时 toApiFormat 抛 IllegalStateException")
        void noToolUseIdThrows() {
            ToolResult result = ToolResult.success("hello");
            assertThrows(IllegalStateException.class, result::toApiFormat);
        }

        @Test
        @DisplayName("空白 toolUseId 也抛 IllegalStateException")
        void blankToolUseIdThrows() {
            ToolResult result = ToolResult.success("   ", "hello");
            assertThrows(IllegalStateException.class, result::toApiFormat);
        }
    }

    @Nested
    @DisplayName("异常工厂方法")
    class ErrorFromException {

        @Test
        @DisplayName("error(Throwable) 使用异常 message")
        void errorFromThrowableUsesMessage() {
            ToolResult result = ToolResult.error(new RuntimeException("network timeout"));
            assertTrue(result.isError());
            assertEquals("network timeout", result.getContent());
        }

        @Test
        @DisplayName("error(Throwable) 无 message 时使用类名")
        void errorFromThrowableNoMessageUsesClassName() {
            ToolResult result = ToolResult.error(new NullPointerException());
            assertTrue(result.isError());
            assertEquals("NullPointerException", result.getContent());
        }

        @Test
        @DisplayName("error(toolUseId, Throwable) 保留 toolUseId")
        void errorFromThrowableWithToolUseId() {
            ToolResult result = ToolResult.error("use-1", new IllegalArgumentException("bad input"));
            assertEquals("use-1", result.getToolUseId());
            assertTrue(result.isError());
            assertEquals("bad input", result.getContent());
        }
    }

    @Nested
    @DisplayName("content null 防御")
    class NullContentDefense {

        @Test
        @DisplayName("null content 抛 IllegalArgumentException")
        void nullContentThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> ToolResult.success((String) null));
        }
    }

    @Nested
    @DisplayName("success(toolUseId, Object)")
    class SuccessFromObject {

        @Test
        @DisplayName("Object result 转为 toString")
        void objectResultToString() {
            ToolResult result = ToolResult.success("use-1", 42);
            assertEquals("42", result.getContent());
        }

        @Test
        @DisplayName("null Object result 转为 'null'")
        void nullObjectResultToNullString() {
            ToolResult result = ToolResult.success("use-1", (Object) null);
            assertEquals("null", result.getContent());
        }
    }
}
