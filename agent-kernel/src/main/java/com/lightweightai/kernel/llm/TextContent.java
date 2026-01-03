package com.lightweightai.kernel.llm;

/**
 * Text content block
 */
public class TextContent implements ContentBlock {

    private final String text;

    public TextContent(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Text cannot be null");
        }
        this.text = text;
    }

    @Override
    public ContentType getType() {
        return ContentType.TEXT;
    }

    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return "TextContent{text='" + text + "'}";
    }
}
