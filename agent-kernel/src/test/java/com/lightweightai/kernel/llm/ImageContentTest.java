package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ImageContent — 多模态图片内容块")
class ImageContentTest {

    @Nested
    @DisplayName("URL 模式")
    class UrlModeTests {

        @Test
        @DisplayName("URL 构造: isUrl()=true, getData()=null")
        void urlModeProperties() {
            ImageContent img = new ImageContent("https://example.com/img.png", "image/png");

            assertEquals(ContentBlock.ContentType.IMAGE, img.getType());
            assertTrue(img.isUrl());
            assertEquals("https://example.com/img.png", img.getUrl());
            assertNull(img.getData());
            assertEquals("image/png", img.getMimeType());
        }
    }

    @Nested
    @DisplayName("二进制模式")
    class BinaryModeTests {

        @Test
        @DisplayName("二进制构造: isUrl()=false, getUrl()=null")
        void binaryModeProperties() {
            byte[] data = new byte[]{1, 2, 3, 4};
            ImageContent img = new ImageContent(data, "image/jpeg");

            assertEquals(ContentBlock.ContentType.IMAGE, img.getType());
            assertFalse(img.isUrl());
            assertNull(img.getUrl());
            assertArrayEquals(data, img.getData());
            assertEquals("image/jpeg", img.getMimeType());
        }
    }

    @Test
    @DisplayName("getType 始终返回 IMAGE")
    void typeIsAlwaysImage() {
        assertEquals(ContentBlock.ContentType.IMAGE,
                new ImageContent("url", "image/png").getType());
        assertEquals(ContentBlock.ContentType.IMAGE,
                new ImageContent(new byte[0], "image/gif").getType());
    }
}
