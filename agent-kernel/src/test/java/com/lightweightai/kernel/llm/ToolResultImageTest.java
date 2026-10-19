package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolResult — image support and extended factory methods")
class ToolResultImageTest {

    @Test
    @DisplayName("default images list is empty, not null")
    void defaultImagesEmpty() {
        ToolResult r = ToolResult.success("text");
        assertNotNull(r.getImages());
        assertTrue(r.getImages().isEmpty());
        assertFalse(r.hasImages());
    }

    @Test
    @DisplayName("withImages creates result with image list")
    void withImages() {
        ImageContent img = new ImageContent("https://example.com/img.png", "image/png");
        ToolResult r = ToolResult.withImages("captured frame", List.of(img));

        assertFalse(r.isError());
        assertEquals("captured frame", r.getContent());
        assertTrue(r.hasImages());
        assertEquals(1, r.getImages().size());
        assertEquals("https://example.com/img.png", r.getImages().get(0).getUrl());
    }

    @Test
    @DisplayName("withImages with multiple images")
    void withMultipleImages() {
        ImageContent img1 = new ImageContent("https://example.com/1.png", "image/png");
        ImageContent img2 = new ImageContent("https://example.com/2.png", "image/png");
        ToolResult r = ToolResult.withImages("screenshots", List.of(img1, img2));

        assertEquals(2, r.getImages().size());
    }

    @Test
    @DisplayName("withImages with null list produces empty images")
    void withImagesNull() {
        ToolResult r = ToolResult.withImages("no images", null);
        assertFalse(r.hasImages());
        assertTrue(r.getImages().isEmpty());
    }

    @Test
    @DisplayName("withImages with empty list produces empty images")
    void withImagesEmpty() {
        ToolResult r = ToolResult.withImages("no images", List.of());
        assertFalse(r.hasImages());
    }

    @Test
    @DisplayName("image() convenience creates single-image result")
    void singleImage() {
        ImageContent img = new ImageContent("https://example.com/shot.png", "image/png");
        ToolResult r = ToolResult.image("screenshot", img);

        assertTrue(r.hasImages());
        assertEquals(1, r.getImages().size());
        assertEquals("screenshot", r.getContent());
    }

    @Test
    @DisplayName("image() with null ImageContent produces no images")
    void imageNull() {
        ToolResult r = ToolResult.image("no image", null);
        assertFalse(r.hasImages());
        assertTrue(r.getImages().isEmpty());
    }

    @Test
    @DisplayName("images list is immutable")
    void imagesImmutable() {
        ImageContent img = new ImageContent("https://example.com/img.png", "image/png");
        ToolResult r = ToolResult.withImages("frame", List.of(img));

        assertThrows(UnsupportedOperationException.class,
                () -> r.getImages().add(new ImageContent("injected", "image/png")));
    }

    @Test
    @DisplayName("withToolUseId preserves images")
    void withToolUseIdPreservesImages() {
        ImageContent img = new ImageContent("https://example.com/img.png", "image/png");
        ToolResult original = ToolResult.withImages("frame", List.of(img));

        ToolResult bound = original.withToolUseId("tu_1");

        assertEquals("tu_1", bound.getToolUseId());
        assertTrue(bound.hasImages());
        assertEquals(1, bound.getImages().size());
    }

    @Test
    @DisplayName("error(Throwable) extracts exception message")
    void errorFromThrowable() {
        ToolResult r = ToolResult.error(new RuntimeException("connection refused"));
        assertTrue(r.isError());
        assertEquals("connection refused", r.getContent());
        assertNull(r.getToolUseId());
    }

    @Test
    @DisplayName("error(Throwable) uses class name when message is null")
    void errorFromThrowableNoMessage() {
        ToolResult r = ToolResult.error(new NullPointerException());
        assertTrue(r.isError());
        assertEquals("NullPointerException", r.getContent());
    }

    @Test
    @DisplayName("error(toolUseId, Throwable) binds id and extracts message")
    void errorWithIdFromThrowable() {
        ToolResult r = ToolResult.error("tu_1", new IllegalStateException("bad"));
        assertEquals("tu_1", r.getToolUseId());
        assertEquals("bad", r.getContent());
        assertTrue(r.isError());
    }

    @Test
    @DisplayName("success(toolUseId, Object) converts object to string")
    void successWithObject() {
        ToolResult r = ToolResult.success("tu_1", 42);
        assertEquals("42", r.getContent());
        assertEquals("tu_1", r.getToolUseId());
        assertFalse(r.isError());
    }

    @Test
    @DisplayName("success(toolUseId, null Object) produces 'null' string")
    void successWithNullObject() {
        ToolResult r = ToolResult.success("tu_1", (Object) null);
        assertEquals("null", r.getContent());
    }

    @Test
    @DisplayName("toApiFormat without toolUseId throws IllegalStateException")
    void toApiFormatNoId() {
        ToolResult r = ToolResult.success("data");
        assertThrows(IllegalStateException.class, r::toApiFormat);
    }

    @Test
    @DisplayName("toApiFormat for error result includes is_error=true")
    void toApiFormatError() {
        ToolResult r = ToolResult.error("tu_1", "failed");
        Map<String, Object> api = r.toApiFormat();

        assertEquals(true, api.get("is_error"));
        assertEquals("failed", api.get("content"));
        assertEquals("tu_1", api.get("tool_use_id"));
        assertEquals("tool_result", api.get("type"));
    }

    @Test
    @DisplayName("toString contains key fields")
    void toStringTest() {
        ToolResult r = ToolResult.success("tu_1", "result data");
        String str = r.toString();
        assertTrue(str.contains("tu_1"));
        assertTrue(str.contains("result data"));
    }
}
