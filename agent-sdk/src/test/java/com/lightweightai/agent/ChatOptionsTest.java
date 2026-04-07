package com.lightweightai.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatOptionsTest {

    @Test
    void shouldBuildWithAllFields() {
        ChatOptions options = ChatOptions.builder()
                .temperature(0.7)
                .maxTokens(512)
                .systemPrompt("You are helpful.")
                .build();

        assertEquals(0.7, options.getTemperature());
        assertEquals(512, options.getMaxTokens());
        assertEquals("You are helpful.", options.getSystemPrompt());
    }

    @Test
    void shouldDefaultUnsetFieldsToNull() {
        ChatOptions options = ChatOptions.builder().build();
        assertNull(options.getTemperature());
        assertNull(options.getMaxTokens());
        assertNull(options.getSystemPrompt());
    }

    @Test
    void shouldAcceptBoundaryTemperatures() {
        assertDoesNotThrow(() -> ChatOptions.builder().temperature(0.0).build());
        assertDoesNotThrow(() -> ChatOptions.builder().temperature(1.0).build());
    }

    @Test
    void shouldRejectTemperatureBelowZero() {
        assertThrows(IllegalArgumentException.class,
                () -> ChatOptions.builder().temperature(-0.01));
    }

    @Test
    void shouldRejectTemperatureAboveOne() {
        assertThrows(IllegalArgumentException.class,
                () -> ChatOptions.builder().temperature(1.01));
    }

    @Test
    void shouldRejectZeroOrNegativeMaxTokens() {
        assertThrows(IllegalArgumentException.class,
                () -> ChatOptions.builder().maxTokens(0));
        assertThrows(IllegalArgumentException.class,
                () -> ChatOptions.builder().maxTokens(-5));
    }

    @Test
    void shouldAllowNullSystemPrompt() {
        ChatOptions options = ChatOptions.builder().systemPrompt(null).build();
        assertNull(options.getSystemPrompt());
    }
}
