package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TextContent - text content block")
class TextContentTest {

    @Nested
    @DisplayName("Given valid text")
    class ValidText {

        @Test
        @DisplayName("stores the text and returns it via getText")
        void shouldStoreAndReturnText() {
            // Given / When
            TextContent content = new TextContent("Hello, world!");

            // Then
            assertEquals("Hello, world!", content.getText());
        }

        @Test
        @DisplayName("getType returns ContentType.TEXT")
        void getTypeReturnsText() {
            TextContent content = new TextContent("sample");

            assertEquals(ContentBlock.ContentType.TEXT, content.getType());
        }

        @Test
        @DisplayName("empty string is accepted")
        void emptyStringIsAccepted() {
            TextContent content = new TextContent("");

            assertEquals("", content.getText());
            assertEquals(ContentBlock.ContentType.TEXT, content.getType());
        }
    }

    @Nested
    @DisplayName("Given null text")
    class NullText {

        @Test
        @DisplayName("constructor throws IllegalArgumentException")
        void shouldThrowForNullText() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new TextContent(null)
            );
            assertTrue(ex.getMessage().contains("null"),
                "Exception message should mention null");
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringBehavior {

        @Test
        @DisplayName("includes the text content")
        void shouldContainTextContent() {
            TextContent content = new TextContent("my text");

            String result = content.toString();

            assertTrue(result.contains("my text"));
        }

        @Test
        @DisplayName("includes the class name")
        void shouldContainClassName() {
            TextContent content = new TextContent("test");

            String result = content.toString();

            assertTrue(result.contains("TextContent"));
        }
    }

    @Nested
    @DisplayName("ContentBlock contract")
    class ContentBlockContract {

        @Test
        @DisplayName("implements ContentBlock interface")
        void implementsContentBlock() {
            TextContent content = new TextContent("test");

            assertInstanceOf(ContentBlock.class, content);
        }
    }
}
