package com.lightweightai.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChatOptions - chat configuration")
class ChatOptionsTest {

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        void defaultBuild_hasNullFields() {
            ChatOptions opts = ChatOptions.builder().build();
            assertNull(opts.getTemperature());
            assertNull(opts.getMaxTokens());
            assertNull(opts.getSystemPrompt());
        }

        @Test
        void allFieldsSet() {
            ChatOptions opts = ChatOptions.builder()
                .temperature(0.7)
                .maxTokens(1024)
                .systemPrompt("You are helpful")
                .build();

            assertEquals(0.7, opts.getTemperature());
            assertEquals(1024, opts.getMaxTokens());
            assertEquals("You are helpful", opts.getSystemPrompt());
        }

        @Test
        void temperature_zeroIsValid() {
            ChatOptions opts = ChatOptions.builder().temperature(0.0).build();
            assertEquals(0.0, opts.getTemperature());
        }

        @Test
        void temperature_oneIsValid() {
            ChatOptions opts = ChatOptions.builder().temperature(1.0).build();
            assertEquals(1.0, opts.getTemperature());
        }

        @Test
        void maxTokens_oneIsValid() {
            ChatOptions opts = ChatOptions.builder().maxTokens(1).build();
            assertEquals(1, opts.getMaxTokens());
        }
    }

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        void temperature_belowZero_throws() {
            assertThrows(IllegalArgumentException.class,
                () -> ChatOptions.builder().temperature(-0.1));
        }

        @Test
        void temperature_aboveOne_throws() {
            assertThrows(IllegalArgumentException.class,
                () -> ChatOptions.builder().temperature(1.1));
        }

        @Test
        void maxTokens_zero_throws() {
            assertThrows(IllegalArgumentException.class,
                () -> ChatOptions.builder().maxTokens(0));
        }

        @Test
        void maxTokens_negative_throws() {
            assertThrows(IllegalArgumentException.class,
                () -> ChatOptions.builder().maxTokens(-1));
        }
    }
}
