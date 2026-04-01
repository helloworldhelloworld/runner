package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ImageContent - 图片内容块")
class ImageContentTest {

    @Test
    @DisplayName("URL 构造")
    void urlConstruction() {
        ImageContent img = new ImageContent("https://example.com/img.png", "image/png");

        assertEquals(ContentBlock.ContentType.IMAGE, img.getType());
        assertEquals("https://example.com/img.png", img.getUrl());
        assertEquals("image/png", img.getMimeType());
        assertNull(img.getData());
        assertTrue(img.isUrl());
    }

    @Test
    @DisplayName("字节数据构造")
    void dataConstruction() {
        byte[] data = new byte[]{1, 2, 3};
        ImageContent img = new ImageContent(data, "image/jpeg");

        assertEquals(ContentBlock.ContentType.IMAGE, img.getType());
        assertNull(img.getUrl());
        assertEquals("image/jpeg", img.getMimeType());
        assertArrayEquals(new byte[]{1, 2, 3}, img.getData());
        assertFalse(img.isUrl());
    }
}
