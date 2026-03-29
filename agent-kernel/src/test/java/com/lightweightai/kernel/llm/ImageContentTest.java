package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ImageContent - multimodal image block")
class ImageContentTest {

    @Test
    void urlConstructor_setsUrlAndMimeType() {
        ImageContent img = new ImageContent("https://example.com/img.png", "image/png");

        assertEquals("https://example.com/img.png", img.getUrl());
        assertEquals("image/png", img.getMimeType());
        assertNull(img.getData());
        assertTrue(img.isUrl());
    }

    @Test
    void dataConstructor_setsBytesAndMimeType() {
        byte[] data = new byte[]{1, 2, 3, 4};
        ImageContent img = new ImageContent(data, "image/jpeg");

        assertArrayEquals(new byte[]{1, 2, 3, 4}, img.getData());
        assertEquals("image/jpeg", img.getMimeType());
        assertNull(img.getUrl());
        assertFalse(img.isUrl());
    }

    @Test
    void getType_returnsImage() {
        ImageContent img = new ImageContent("url", "image/png");
        assertEquals(ContentBlock.ContentType.IMAGE, img.getType());
    }

    @Test
    void isUrl_trueForUrlConstructor() {
        ImageContent img = new ImageContent("https://img.com/a.jpg", "image/jpeg");
        assertTrue(img.isUrl());
    }

    @Test
    void isUrl_falseForDataConstructor() {
        ImageContent img = new ImageContent(new byte[]{0}, "image/gif");
        assertFalse(img.isUrl());
    }
}
