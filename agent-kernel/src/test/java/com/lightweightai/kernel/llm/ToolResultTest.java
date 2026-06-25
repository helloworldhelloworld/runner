package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolResult - 工具执行结果模型")
class ToolResultTest {

    @Nested
    @DisplayName("成功工厂方法")
    class SuccessFactoryTests {

        @Test
        @DisplayName("success(content) 设置内容且 isError 为 false，toolUseId 为 null")
        void successSetsContentAndNotError() {
            ToolResult result = ToolResult.success("hello");

            assertEquals("hello", result.getContent());
            assertFalse(result.isError());
            assertNull(result.getToolUseId());
        }

        @Test
        @DisplayName("success 带 structuredContent 正确设置结构化内容")
        void successWithStructuredContent() {
            Map<String, Object> structured = Map.of("price", 99.9, "currency", "CNY");

            ToolResult result = ToolResult.success("total", structured);

            assertTrue(result.hasStructuredContent());
            assertEquals(99.9, result.getStructuredContent().get("price"));
            assertEquals("CNY", result.getStructuredContent().get("currency"));
        }

        @Test
        @DisplayName("structuredContent 为 null 时 hasStructuredContent 为 false")
        void structuredContentNullMeansNotPresent() {
            ToolResult result = ToolResult.success("plain text", (Map<String, Object>) null);

            assertFalse(result.hasStructuredContent());
            assertNull(result.getStructuredContent());
        }

        @Test
        @DisplayName("structuredContent 为空 Map 时 hasStructuredContent 为 false")
        void structuredContentEmptyMeansNotPresent() {
            ToolResult result = ToolResult.success("plain text", Map.of());

            assertFalse(result.hasStructuredContent());
        }

        @Test
        @DisplayName("success(toolUseId, Object) 使用 toString 转换")
        void successWithToolUseIdAndObjectUsesToString() {
            ToolResult result = ToolResult.success("call_1", 42);

            assertEquals("call_1", result.getToolUseId());
            assertEquals("42", result.getContent());
            assertFalse(result.isError());
        }

        @Test
        @DisplayName("success(toolUseId, null) 内容为字符串 null")
        void successWithToolUseIdAndNullObjectProducesNullString() {
            ToolResult result = ToolResult.success("call_1", (Object) null);

            assertEquals("null", result.getContent());
            assertFalse(result.isError());
        }
    }

    @Nested
    @DisplayName("错误工厂方法")
    class ErrorFactoryTests {

        @Test
        @DisplayName("error(message) 内容为错误消息且 isError 为 true")
        void errorSetsMessageAndIsError() {
            ToolResult result = ToolResult.error("something failed");

            assertEquals("something failed", result.getContent());
            assertTrue(result.isError());
        }

        @Test
        @DisplayName("error(Throwable) 使用异常消息作为内容")
        void errorFromThrowableUsesMessage() {
            ToolResult result = ToolResult.error(new RuntimeException("connection refused"));

            assertTrue(result.isError());
            assertEquals("connection refused", result.getContent());
        }

        @Test
        @DisplayName("error(Throwable) 当异常消息为 null 时使用类简名")
        void errorFromThrowableWithNullMessageUsesClassName() {
            ToolResult result = ToolResult.error(new NullPointerException());

            assertTrue(result.isError());
            assertEquals("NullPointerException", result.getContent());
        }
    }

    @Nested
    @DisplayName("内容校验")
    class ContentValidationTests {

        @Test
        @DisplayName("null 内容抛出 IllegalArgumentException")
        void nullContentThrows() {
            assertThrows(IllegalArgumentException.class, () -> ToolResult.success(null));
        }
    }

    @Nested
    @DisplayName("withToolUseId")
    class WithToolUseIdTests {

        @Test
        @DisplayName("withToolUseId 返回带 toolUseId 的新实例，原实例不变")
        void returnsNewInstanceWithToolUseIdOriginalUnchanged() {
            ToolResult original = ToolResult.success("content");

            ToolResult bound = original.withToolUseId("tool_use_123");

            assertEquals("tool_use_123", bound.getToolUseId());
            assertEquals("content", bound.getContent());
            assertFalse(bound.isError());

            assertNull(original.getToolUseId());
        }

        @Test
        @DisplayName("withToolUseId 保留 structuredContent")
        void preservesStructuredContentOnWithToolUseId() {
            Map<String, Object> structured = Map.of("key", "value");
            ToolResult original = ToolResult.success("content", structured);

            ToolResult bound = original.withToolUseId("tool_use_123");

            assertTrue(bound.hasStructuredContent());
            assertEquals("value", bound.getStructuredContent().get("key"));
        }

        @Test
        @DisplayName("withToolUseId 对无 structuredContent 的结果也正常工作")
        void preservesNullStructuredContentOnWithToolUseId() {
            ToolResult original = ToolResult.success("content");

            ToolResult bound = original.withToolUseId("tool_use_456");

            assertEquals("tool_use_456", bound.getToolUseId());
            assertFalse(bound.hasStructuredContent());
            assertNull(bound.getStructuredContent());
        }
    }

    @Nested
    @DisplayName("toApiFormat 转换")
    class ToApiFormatTests {

        @Test
        @DisplayName("toApiFormat 返回包含 type、tool_use_id、content、is_error 的正确 Map")
        void returnsCorrectMap() {
            ToolResult result = ToolResult.error("call_1", "timeout");
            Map<String, Object> api = result.toApiFormat();

            assertEquals("tool_result", api.get("type"));
            assertEquals("call_1", api.get("tool_use_id"));
            assertEquals("timeout", api.get("content"));
            assertEquals(true, api.get("is_error"));
        }

        @Test
        @DisplayName("toApiFormat 成功结果不包含 is_error")
        void successOmitsIsError() {
            ToolResult result = ToolResult.success("call_1", "ok");
            Map<String, Object> api = result.toApiFormat();

            assertFalse(api.containsKey("is_error"));
        }

        @Test
        @DisplayName("toApiFormat 无 toolUseId 时抛出 IllegalStateException")
        void withoutToolUseIdThrows() {
            ToolResult result = ToolResult.success("hello");

            assertThrows(IllegalStateException.class, result::toApiFormat);
        }
    }

    @Nested
    @DisplayName("图像相关")
    class ImageTests {

        @Test
        @DisplayName("withImages 传入 null 列表时 images 为空列表且 hasImages 为 false")
        void withImagesNullListResultsInEmptyList() {
            ToolResult result = ToolResult.withImages("captured", null);

            assertNotNull(result.getImages());
            assertTrue(result.getImages().isEmpty());
            assertFalse(result.hasImages());
        }

        @Test
        @DisplayName("withImages 传入空列表时 images 为空列表且 hasImages 为 false")
        void withImagesEmptyListResultsInEmptyList() {
            ToolResult result = ToolResult.withImages("captured", Collections.emptyList());

            assertNotNull(result.getImages());
            assertTrue(result.getImages().isEmpty());
            assertFalse(result.hasImages());
        }

        @Test
        @DisplayName("getImages 返回不可变列表")
        void imagesAreImmutable() {
            ImageContent image = new ImageContent("https://example.com/img.png", "image/png");
            ToolResult result = ToolResult.withImages("captured", List.of(image));

            assertTrue(result.hasImages());
            assertEquals(1, result.getImages().size());

            List<ImageContent> images = result.getImages();
            assertThrows(UnsupportedOperationException.class, () -> images.add(
                    new ImageContent("https://example.com/other.png", "image/png")));
        }
    }
}
