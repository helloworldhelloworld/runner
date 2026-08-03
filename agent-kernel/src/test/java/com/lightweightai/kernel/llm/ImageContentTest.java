package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ImageContent - multimodal image content block")
class ImageContentTest {

    @Nested
    @DisplayName("Given URL constructor")
    class UrlConstructor {

        @Test
        @DisplayName("stores URL and mimeType, data is null")
        void shouldStoreUrlAndMimeType() {
            // Given
            String url = "https://example.com/photo.png";
            String mimeType = "image/png";

            // When
            ImageContent content = new ImageContent(url, mimeType);

            // Then
            assertEquals(url, content.getUrl());
            assertEquals(mimeType, content.getMimeType());
            assertNull(content.getData());
        }

        @Test
        @DisplayName("isUrl returns true")
        void isUrlReturnsTrue() {
            ImageContent content = new ImageContent("https://example.com/img.jpg", "image/jpeg");

            assertTrue(content.isUrl());
        }

        @Test
        @DisplayName("getType returns ContentType.IMAGE")
        void getTypeReturnsImage() {
            ImageContent content = new ImageContent("https://example.com/img.jpg", "image/jpeg");

            assertEquals(ContentBlock.ContentType.IMAGE, content.getType());
        }
    }

    @Nested
    @DisplayName("Given byte[] data constructor")
    class DataConstructor {

        @Test
        @DisplayName("stores data and mimeType, URL is null")
        void shouldStoreDataAndMimeType() {
            // Given
            byte[] data = new byte[]{10, 20, 30, 40, 50};
            String mimeType = "image/jpeg";

            // When
            ImageContent content = new ImageContent(data, mimeType);

            // Then
            assertArrayEquals(data, content.getData());
            assertEquals(mimeType, content.getMimeType());
            assertNull(content.getUrl());
        }

        @Test
        @DisplayName("isUrl returns false")
        void isUrlReturnsFalse() {
            ImageContent content = new ImageContent(new byte[]{1, 2, 3}, "image/png");

            assertFalse(content.isUrl());
        }

        @Test
        @DisplayName("getType returns ContentType.IMAGE")
        void getTypeReturnsImage() {
            ImageContent content = new ImageContent(new byte[]{1}, "image/gif");

            assertEquals(ContentBlock.ContentType.IMAGE, content.getType());
        }

        @Test
        @DisplayName("empty byte array is valid data")
        void emptyByteArrayIsValid() {
            ImageContent content = new ImageContent(new byte[0], "image/png");

            assertFalse(content.isUrl());
            assertNotNull(content.getData());
            assertEquals(0, content.getData().length);
        }
    }

    @Nested
    @DisplayName("ContentBlock contract")
    class ContentBlockContract {

        @Test
        @DisplayName("implements ContentBlock interface")
        void implementsContentBlock() {
            ImageContent urlContent = new ImageContent("http://img", "image/png");
            ImageContent dataContent = new ImageContent(new byte[]{1}, "image/png");

            assertInstanceOf(ContentBlock.class, urlContent);
            assertInstanceOf(ContentBlock.class, dataContent);
        }

        @Test
        @DisplayName("both constructors return IMAGE content type")
        void bothConstructorsReturnImageType() {
            ImageContent urlContent = new ImageContent("http://img", "image/png");
            ImageContent dataContent = new ImageContent(new byte[]{1}, "image/png");

            assertEquals(urlContent.getType(), dataContent.getType());
            assertEquals(ContentBlock.ContentType.IMAGE, urlContent.getType());
        }
    }
}
