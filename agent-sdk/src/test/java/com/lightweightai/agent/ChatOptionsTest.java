package com.lightweightai.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChatOptions")
class ChatOptionsTest {

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        void shouldBuildWithAllOptions() {
            ChatOptions options = ChatOptions.builder()
                    .temperature(0.7)
                    .maxTokens(1000)
                    .systemPrompt("You are helpful")
                    .build();

            assertEquals(0.7, options.getTemperature());
            assertEquals(1000, options.getMaxTokens());
            assertEquals("You are helpful", options.getSystemPrompt());
        }

        @Test
        void shouldBuildWithDefaults() {
            ChatOptions options = ChatOptions.builder().build();

            assertNull(options.getTemperature());
            assertNull(options.getMaxTokens());
            assertNull(options.getSystemPrompt());
        }

        @Test
        void shouldBuildWithPartialOptions() {
            ChatOptions options = ChatOptions.builder()
                    .temperature(0.5)
                    .build();

            assertEquals(0.5, options.getTemperature());
            assertNull(options.getMaxTokens());
            assertNull(options.getSystemPrompt());
        }

        @Test
        void shouldAcceptBoundaryTemperatures() {
            ChatOptions zero = ChatOptions.builder().temperature(0.0).build();
            ChatOptions one = ChatOptions.builder().temperature(1.0).build();

            assertEquals(0.0, zero.getTemperature());
            assertEquals(1.0, one.getTemperature());
        }
    }

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        void shouldRejectNegativeTemperature() {
            assertThrows(IllegalArgumentException.class, () ->
                    ChatOptions.builder().temperature(-0.1));
        }

        @Test
        void shouldRejectTemperatureAboveOne() {
            assertThrows(IllegalArgumentException.class, () ->
                    ChatOptions.builder().temperature(1.1));
        }

        @Test
        void shouldRejectZeroMaxTokens() {
            assertThrows(IllegalArgumentException.class, () ->
                    ChatOptions.builder().maxTokens(0));
        }

        @Test
        void shouldRejectNegativeMaxTokens() {
            assertThrows(IllegalArgumentException.class, () ->
                    ChatOptions.builder().maxTokens(-1));
        }
    }
}
