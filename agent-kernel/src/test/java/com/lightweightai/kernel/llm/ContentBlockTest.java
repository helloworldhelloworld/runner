package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ContentBlock - 多模态内容块")
class ContentBlockTest {

    @Nested
    @DisplayName("TextContent")
    class TextContentTests {

        @Test
        @DisplayName("创建 TextContent 并获取类型")
        void createAndGetType() {
            TextContent text = new TextContent("hello");
            assertEquals(ContentBlock.ContentType.TEXT, text.getType());
            assertEquals("hello", text.getText());
        }

        @Test
        @DisplayName("null 文本抛出 IllegalArgumentException")
        void nullTextThrows() {
            assertThrows(IllegalArgumentException.class, () -> new TextContent(null));
        }

        @Test
        @DisplayName("空字符串可以创建")
        void emptyTextAllowed() {
            TextContent text = new TextContent("");
            assertEquals("", text.getText());
        }

        @Test
        @DisplayName("toString 包含文本内容")
        void toStringContainsText() {
            TextContent text = new TextContent("sample");
            String str = text.toString();
            assertTrue(str.contains("sample"));
            assertTrue(str.contains("TextContent"));
        }
    }

    @Nested
    @DisplayName("ImageContent")
    class ImageContentTests {

        @Test
        @DisplayName("从 URL 创建 ImageContent")
        void createFromUrl() {
            ImageContent img = new ImageContent("https://example.com/img.png", "image/png");
            assertEquals(ContentBlock.ContentType.IMAGE, img.getType());
            assertEquals("https://example.com/img.png", img.getUrl());
            assertEquals("image/png", img.getMimeType());
            assertNull(img.getData());
            assertTrue(img.isUrl());
        }

        @Test
        @DisplayName("从 byte 数组创建 ImageContent")
        void createFromData() {
            byte[] data = new byte[]{1, 2, 3, 4};
            ImageContent img = new ImageContent(data, "image/jpeg");
            assertEquals(ContentBlock.ContentType.IMAGE, img.getType());
            assertNull(img.getUrl());
            assertEquals("image/jpeg", img.getMimeType());
            assertArrayEquals(new byte[]{1, 2, 3, 4}, img.getData());
            assertFalse(img.isUrl());
        }

        @Test
        @DisplayName("isUrl 区分 URL 和 data 模式")
        void isUrlDistinguishesMode() {
            ImageContent urlImg = new ImageContent("http://img", "image/png");
            ImageContent dataImg = new ImageContent(new byte[0], "image/png");

            assertTrue(urlImg.isUrl());
            assertFalse(dataImg.isUrl());
        }
    }

    @Nested
    @DisplayName("ContentType 枚举")
    class ContentTypeTests {

        @Test
        @DisplayName("包含所有预期的枚举值")
        void allEnumValues() {
            ContentBlock.ContentType[] values = ContentBlock.ContentType.values();
            assertEquals(6, values.length);
            assertNotNull(ContentBlock.ContentType.valueOf("TEXT"));
            assertNotNull(ContentBlock.ContentType.valueOf("IMAGE"));
            assertNotNull(ContentBlock.ContentType.valueOf("AUDIO"));
            assertNotNull(ContentBlock.ContentType.valueOf("VIDEO"));
            assertNotNull(ContentBlock.ContentType.valueOf("TOOL_USE"));
            assertNotNull(ContentBlock.ContentType.valueOf("TOOL_RESULT"));
        }
    }
}
