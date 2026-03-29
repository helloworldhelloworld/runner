package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TextContent - text content block")
class TextContentTest {

    @Test
    void constructor_setsText() {
        TextContent tc = new TextContent("hello world");
        assertEquals("hello world", tc.getText());
    }

    @Test
    void constructor_nullThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> new TextContent(null));
    }

    @Test
    void getType_returnsText() {
        TextContent tc = new TextContent("test");
        assertEquals(ContentBlock.ContentType.TEXT, tc.getType());
    }

    @Test
    void toString_containsText() {
        TextContent tc = new TextContent("sample");
        String str = tc.toString();
        assertTrue(str.contains("sample"));
        assertTrue(str.contains("TextContent"));
    }

    @Test
    void emptyString_isAllowed() {
        TextContent tc = new TextContent("");
        assertEquals("", tc.getText());
    }
}
