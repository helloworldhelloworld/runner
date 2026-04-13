package com.lightweightai.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChatOptions - simplified configuration for chat parameters")
class ChatOptionsTest {

    // ==================== builder ====================

    @Test
    @DisplayName("builder creates options with all fields")
    void builderCreatesWithAllFields() {
        ChatOptions options = ChatOptions.builder()
            .temperature(0.7)
            .maxTokens(2000)
            .systemPrompt("You are helpful")
            .build();

        assertEquals(0.7, options.getTemperature());
        assertEquals(2000, options.getMaxTokens());
        assertEquals("You are helpful", options.getSystemPrompt());
    }

    @Test
    @DisplayName("builder creates options with defaults (null)")
    void builderCreatesWithDefaults() {
        ChatOptions options = ChatOptions.builder().build();

        assertNull(options.getTemperature());
        assertNull(options.getMaxTokens());
        assertNull(options.getSystemPrompt());
    }

    // ==================== temperature validation ====================

    @Test
    @DisplayName("temperature accepts 0")
    void temperatureAcceptsZero() {
        ChatOptions options = ChatOptions.builder().temperature(0).build();
        assertEquals(0.0, options.getTemperature());
    }

    @Test
    @DisplayName("temperature accepts 1")
    void temperatureAcceptsOne() {
        ChatOptions options = ChatOptions.builder().temperature(1).build();
        assertEquals(1.0, options.getTemperature());
    }

    @Test
    @DisplayName("temperature rejects negative value")
    void temperatureRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () ->
            ChatOptions.builder().temperature(-0.1));
    }

    @Test
    @DisplayName("temperature rejects value greater than 1")
    void temperatureRejectsAboveOne() {
        assertThrows(IllegalArgumentException.class, () ->
            ChatOptions.builder().temperature(1.1));
    }

    // ==================== maxTokens validation ====================

    @Test
    @DisplayName("maxTokens accepts positive value")
    void maxTokensAcceptsPositive() {
        ChatOptions options = ChatOptions.builder().maxTokens(100).build();
        assertEquals(100, options.getMaxTokens());
    }

    @Test
    @DisplayName("maxTokens rejects zero")
    void maxTokensRejectsZero() {
        assertThrows(IllegalArgumentException.class, () ->
            ChatOptions.builder().maxTokens(0));
    }

    @Test
    @DisplayName("maxTokens rejects negative value")
    void maxTokensRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () ->
            ChatOptions.builder().maxTokens(-1));
    }

    // ==================== systemPrompt ====================

    @Test
    @DisplayName("systemPrompt can be null")
    void systemPromptNull() {
        ChatOptions options = ChatOptions.builder().systemPrompt(null).build();
        assertNull(options.getSystemPrompt());
    }

    @Test
    @DisplayName("systemPrompt can be empty")
    void systemPromptEmpty() {
        ChatOptions options = ChatOptions.builder().systemPrompt("").build();
        assertEquals("", options.getSystemPrompt());
    }
}
