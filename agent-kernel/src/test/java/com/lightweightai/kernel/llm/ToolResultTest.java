package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolResult 测试
 *
 * 验证工厂方法、structuredContent 支持、API 格式转换
 */
@DisplayName("ToolResult - Tool execution result model")
class ToolResultTest {

    @Test
    @DisplayName("success() creates non-error result")
    void shouldCreateSuccessResult() {
        ToolResult result = ToolResult.success("hello");

        assertFalse(result.isError());
        assertEquals("hello", result.getContent());
        assertNull(result.getToolUseId());
        assertNull(result.getStructuredContent());
        assertFalse(result.hasStructuredContent());
    }

    @Test
    @DisplayName("error() creates error result")
    void shouldCreateErrorResult() {
        ToolResult result = ToolResult.error("something failed");

        assertTrue(result.isError());
        assertEquals("something failed", result.getContent());
        assertNull(result.getStructuredContent());
        assertFalse(result.hasStructuredContent());
    }

    @Test
    @DisplayName("success() with structuredContent carries structured data")
    void shouldCarryStructuredContent() {
        Map<String, Object> structured = Map.of(
            "price", 2350.5,
            "currency", "USD",
            "items", 3
        );

        ToolResult result = ToolResult.success("Total: $2350.50", structured);

        assertFalse(result.isError());
        assertEquals("Total: $2350.50", result.getContent());
        assertTrue(result.hasStructuredContent());
        assertNotNull(result.getStructuredContent());
        assertEquals(2350.5, result.getStructuredContent().get("price"));
        assertEquals("USD", result.getStructuredContent().get("currency"));
        assertEquals(3, result.getStructuredContent().get("items"));
    }

    @Test
    @DisplayName("structuredContent null means hasStructuredContent() is false")
    void shouldReportNoStructuredContentWhenNull() {
        ToolResult result = ToolResult.success("plain text", (java.util.Map<String, Object>) null);

        assertFalse(result.hasStructuredContent());
        assertNull(result.getStructuredContent());
    }

    @Test
    @DisplayName("structuredContent empty map means hasStructuredContent() is false")
    void shouldReportNoStructuredContentWhenEmpty() {
        ToolResult result = ToolResult.success("plain text", Map.of());

        assertFalse(result.hasStructuredContent());
    }

    @Test
    @DisplayName("withToolUseId preserves structuredContent")
    void shouldPreserveStructuredContentOnWithToolUseId() {
        Map<String, Object> structured = Map.of("key", "value");
        ToolResult original = ToolResult.success("content", structured);

        ToolResult bound = original.withToolUseId("tool_use_123");

        assertEquals("tool_use_123", bound.getToolUseId());
        assertEquals("content", bound.getContent());
        assertFalse(bound.isError());
        assertTrue(bound.hasStructuredContent());
        assertEquals("value", bound.getStructuredContent().get("key"));
    }

    @Test
    @DisplayName("withToolUseId works for result without structuredContent")
    void shouldPreserveNullStructuredContentOnWithToolUseId() {
        ToolResult original = ToolResult.success("content");

        ToolResult bound = original.withToolUseId("tool_use_456");

        assertEquals("tool_use_456", bound.getToolUseId());
        assertFalse(bound.hasStructuredContent());
        assertNull(bound.getStructuredContent());
    }

    @Test
    @DisplayName("toApiFormat includes basic fields")
    void shouldConvertToApiFormat() {
        ToolResult result = ToolResult.success("result text").withToolUseId("id_1");
        Map<String, Object> api = result.toApiFormat();

        assertEquals("tool_result", api.get("type"));
        assertEquals("id_1", api.get("tool_use_id"));
        assertEquals("result text", api.get("content"));
    }

    @Test
    @DisplayName("Null content throws IllegalArgumentException")
    void shouldRejectNullContent() {
        assertThrows(IllegalArgumentException.class, () -> ToolResult.success(null));
    }
}
