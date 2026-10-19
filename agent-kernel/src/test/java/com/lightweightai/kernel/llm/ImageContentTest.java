package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ImageContent — multimodal image block")
class ImageContentTest {

    @Test
    @DisplayName("URL-based image stores url and mimeType")
    void urlBasedImage() {
        ImageContent img = new ImageContent("https://example.com/img.png", "image/png");

        assertEquals("https://example.com/img.png", img.getUrl());
        assertEquals("image/png", img.getMimeType());
        assertNull(img.getData());
        assertTrue(img.isUrl());
    }

    @Test
    @DisplayName("data-based image stores bytes and mimeType")
    void dataBasedImage() {
        byte[] data = new byte[]{1, 2, 3, 4};
        ImageContent img = new ImageContent(data, "image/jpeg");

        assertNull(img.getUrl());
        assertEquals("image/jpeg", img.getMimeType());
        assertArrayEquals(new byte[]{1, 2, 3, 4}, img.getData());
        assertFalse(img.isUrl());
    }

    @Test
    @DisplayName("getType returns IMAGE")
    void contentTypeIsImage() {
        ImageContent img = new ImageContent("https://example.com/img.png", "image/png");
        assertEquals(ContentBlock.ContentType.IMAGE, img.getType());
    }

    @Test
    @DisplayName("isUrl returns true for url-based, false for data-based")
    void isUrlDistinction() {
        ImageContent urlImg = new ImageContent("https://example.com/img.png", "image/png");
        ImageContent dataImg = new ImageContent(new byte[]{1}, "image/jpeg");

        assertTrue(urlImg.isUrl());
        assertFalse(dataImg.isUrl());
    }
}
