package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TextContent - 文本内容块")
class TextContentTest {

    @Test
    @DisplayName("正常构建")
    void normalConstruction() {
        TextContent tc = new TextContent("hello world");

        assertEquals(ContentBlock.ContentType.TEXT, tc.getType());
        assertEquals("hello world", tc.getText());
    }

    @Test
    @DisplayName("null 文本抛异常")
    void nullTextThrows() {
        assertThrows(IllegalArgumentException.class, () -> new TextContent(null));
    }

    @Test
    @DisplayName("空字符串可以创建")
    void emptyTextAllowed() {
        TextContent tc = new TextContent("");
        assertEquals("", tc.getText());
    }

    @Test
    @DisplayName("toString 包含文本内容")
    void toStringOutput() {
        TextContent tc = new TextContent("test");
        assertTrue(tc.toString().contains("test"));
    }
}
