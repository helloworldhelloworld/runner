package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ToolResult image chain (ADR-010 visual feedback).
 *
 * The image chain is a critical path: tool returns images → ToolCallingLoop
 * converts them to multimodal USER messages → LLM sees the images in next round.
 * Existing ToolResultTest covers text/structured content but not images.
 */
@DisplayName("ToolResult - Image chain (visual feedback)")
class ToolResultImageChainTest {

    @Nested
    @DisplayName("withImages factory")
    class WithImagesTests {

        @Test
        @DisplayName("creates result with image list")
        void createsWithImageList() {
            ImageContent img1 = new ImageContent("https://example.com/a.png", "image/png");
            ImageContent img2 = new ImageContent(new byte[]{1, 2, 3}, "image/jpeg");

            ToolResult result = ToolResult.withImages("captured 2 frames", List.of(img1, img2));

            assertFalse(result.isError());
            assertEquals("captured 2 frames", result.getContent());
            assertTrue(result.hasImages());
            assertEquals(2, result.getImages().size());
            assertEquals("https://example.com/a.png", result.getImages().get(0).getUrl());
            assertFalse(result.getImages().get(1).isUrl());
        }

        @Test
        @DisplayName("null images results in empty list")
        void nullImagesResultsInEmptyList() {
            ToolResult result = ToolResult.withImages("no frames", null);
            assertFalse(result.hasImages());
            assertNotNull(result.getImages());
            assertTrue(result.getImages().isEmpty());
        }

        @Test
        @DisplayName("empty images list results in empty")
        void emptyImagesResultsInEmpty() {
            ToolResult result = ToolResult.withImages("no frames", List.of());
            assertFalse(result.hasImages());
        }
    }

    @Nested
    @DisplayName("image() single-frame factory")
    class SingleImageTests {

        @Test
        @DisplayName("creates result with single image")
        void createsSingleImage() {
            ImageContent img = new ImageContent("https://example.com/shot.png", "image/png");
            ToolResult result = ToolResult.image("1 frame captured", img);

            assertTrue(result.hasImages());
            assertEquals(1, result.getImages().size());
            assertEquals("https://example.com/shot.png", result.getImages().get(0).getUrl());
        }

        @Test
        @DisplayName("null image results in empty list")
        void nullImageResultsInEmpty() {
            ToolResult result = ToolResult.image("no capture", null);
            assertFalse(result.hasImages());
        }
    }

    @Nested
    @DisplayName("withToolUseId preserves images")
    class BindingTests {

        @Test
        @DisplayName("withToolUseId carries images through to bound result")
        void withToolUseIdPreservesImages() {
            ImageContent img = new ImageContent("https://example.com/x.jpg", "image/jpeg");
            ToolResult original = ToolResult.withImages("frame", List.of(img));

            ToolResult bound = original.withToolUseId("call_abc");

            assertEquals("call_abc", bound.getToolUseId());
            assertTrue(bound.hasImages(), "images must survive withToolUseId binding");
            assertEquals(1, bound.getImages().size());
            assertEquals("https://example.com/x.jpg", bound.getImages().get(0).getUrl());
        }
    }

    @Nested
    @DisplayName("success/error factories have no images")
    class DefaultNoImagesTests {

        @Test
        @DisplayName("success() has no images")
        void successHasNoImages() {
            ToolResult result = ToolResult.success("text");
            assertFalse(result.hasImages());
            assertNotNull(result.getImages());
            assertTrue(result.getImages().isEmpty());
        }

        @Test
        @DisplayName("error() has no images")
        void errorHasNoImages() {
            ToolResult result = ToolResult.error("err");
            assertFalse(result.hasImages());
        }

        @Test
        @DisplayName("error from exception has no images")
        void errorFromExceptionHasNoImages() {
            ToolResult result = ToolResult.error(new RuntimeException("boom"));
            assertFalse(result.hasImages());
            assertEquals("boom", result.getContent());
        }
    }

    @Test
    @DisplayName("images list is immutable")
    void imagesListIsImmutable() {
        ImageContent img = new ImageContent("https://example.com/a.png", "image/png");
        ToolResult result = ToolResult.withImages("frame", List.of(img));

        assertThrows(UnsupportedOperationException.class,
                () -> result.getImages().add(new ImageContent("extra", "image/png")));
    }
}
