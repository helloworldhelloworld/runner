package com.lightweightai.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChatOptions - SDK 聊天选项")
class ChatOptionsTest {

    @Test
    @DisplayName("默认构建所有字段为 null")
    void shouldHaveNullDefaults() {
        ChatOptions options = ChatOptions.builder().build();
        assertNull(options.getTemperature());
        assertNull(options.getMaxTokens());
        assertNull(options.getSystemPrompt());
    }

    @Test
    @DisplayName("设置 temperature")
    void shouldSetTemperature() {
        ChatOptions options = ChatOptions.builder()
            .temperature(0.7)
            .build();
        assertEquals(0.7, options.getTemperature());
    }

    @Test
    @DisplayName("temperature 超出范围抛异常")
    void shouldThrowForInvalidTemperature() {
        assertThrows(IllegalArgumentException.class,
            () -> ChatOptions.builder().temperature(1.5));
        assertThrows(IllegalArgumentException.class,
            () -> ChatOptions.builder().temperature(-0.1));
    }

    @Test
    @DisplayName("temperature 边界值 0 和 1 有效")
    void shouldAcceptBoundaryTemperature() {
        assertDoesNotThrow(() -> ChatOptions.builder().temperature(0.0));
        assertDoesNotThrow(() -> ChatOptions.builder().temperature(1.0));
    }

    @Test
    @DisplayName("设置 maxTokens")
    void shouldSetMaxTokens() {
        ChatOptions options = ChatOptions.builder()
            .maxTokens(4096)
            .build();
        assertEquals(4096, options.getMaxTokens());
    }

    @Test
    @DisplayName("maxTokens <= 0 抛异常")
    void shouldThrowForInvalidMaxTokens() {
        assertThrows(IllegalArgumentException.class,
            () -> ChatOptions.builder().maxTokens(0));
        assertThrows(IllegalArgumentException.class,
            () -> ChatOptions.builder().maxTokens(-1));
    }

    @Test
    @DisplayName("设置 systemPrompt")
    void shouldSetSystemPrompt() {
        ChatOptions options = ChatOptions.builder()
            .systemPrompt("你是一个助手")
            .build();
        assertEquals("你是一个助手", options.getSystemPrompt());
    }

    @Test
    @DisplayName("链式设置所有字段")
    void shouldChainAllFields() {
        ChatOptions options = ChatOptions.builder()
            .temperature(0.5)
            .maxTokens(2048)
            .systemPrompt("prompt")
            .build();

        assertEquals(0.5, options.getTemperature());
        assertEquals(2048, options.getMaxTokens());
        assertEquals("prompt", options.getSystemPrompt());
    }
}
