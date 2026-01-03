package com.lightweightai.kernel.llm;

/**
 * Base interface for message content blocks.
 * Supports multimodal content (text, images, tool calls, etc.)
 */
public interface ContentBlock {

    ContentType getType();

    enum ContentType {
        TEXT,
        IMAGE,
        AUDIO,
        VIDEO,
        TOOL_USE,
        TOOL_RESULT
    }
}
