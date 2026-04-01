package com.lightweightai.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChatOptions - 聊天选项")
class ChatOptionsTest {

    @Test
    @DisplayName("默认构建 - 所有字段为 null")
    void defaultBuild() {
        ChatOptions opts = ChatOptions.builder().build();
        assertNull(opts.getTemperature());
        assertNull(opts.getMaxTokens());
        assertNull(opts.getSystemPrompt());
    }

    @Test
    @DisplayName("完整构建")
    void fullBuild() {
        ChatOptions opts = ChatOptions.builder()
                .temperature(0.7)
                .maxTokens(1000)
                .systemPrompt("You are a helper")
                .build();

        assertEquals(0.7, opts.getTemperature(), 0.01);
        assertEquals(1000, opts.getMaxTokens());
        assertEquals("You are a helper", opts.getSystemPrompt());
    }

    @Test
    @DisplayName("temperature 超出范围抛异常 - 太高")
    void temperatureTooHigh() {
        assertThrows(IllegalArgumentException.class, () ->
                ChatOptions.builder().temperature(1.5));
    }

    @Test
    @DisplayName("temperature 超出范围抛异常 - 负数")
    void temperatureNegative() {
        assertThrows(IllegalArgumentException.class, () ->
                ChatOptions.builder().temperature(-0.1));
    }

    @Test
    @DisplayName("temperature 边界值 0 和 1")
    void temperatureBoundary() {
        assertDoesNotThrow(() -> ChatOptions.builder().temperature(0));
        assertDoesNotThrow(() -> ChatOptions.builder().temperature(1));
    }

    @Test
    @DisplayName("maxTokens 必须为正数")
    void maxTokensMustBePositive() {
        assertThrows(IllegalArgumentException.class, () ->
                ChatOptions.builder().maxTokens(0));
        assertThrows(IllegalArgumentException.class, () ->
                ChatOptions.builder().maxTokens(-1));
    }
}
