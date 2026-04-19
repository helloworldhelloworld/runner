package com.lightweightai.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChatOptions - 聊天选项验证")
class ChatOptionsTest {

    @Nested
    @DisplayName("Temperature 验证")
    class TemperatureTests {

        @Test
        @DisplayName("有效温度值 0.0 ~ 1.0")
        void shouldAcceptValidTemperature() {
            ChatOptions options = ChatOptions.builder()
                    .temperature(0.7)
                    .build();
            assertEquals(0.7, options.getTemperature());
        }

        @Test
        @DisplayName("边界值 0.0")
        void shouldAcceptZeroTemperature() {
            ChatOptions options = ChatOptions.builder()
                    .temperature(0.0)
                    .build();
            assertEquals(0.0, options.getTemperature());
        }

        @Test
        @DisplayName("边界值 1.0")
        void shouldAcceptOneTemperature() {
            ChatOptions options = ChatOptions.builder()
                    .temperature(1.0)
                    .build();
            assertEquals(1.0, options.getTemperature());
        }

        @Test
        @DisplayName("温度 < 0 抛出异常")
        void shouldRejectNegativeTemperature() {
            assertThrows(IllegalArgumentException.class,
                    () -> ChatOptions.builder().temperature(-0.1));
        }

        @Test
        @DisplayName("温度 > 1 抛出异常")
        void shouldRejectTemperatureAboveOne() {
            assertThrows(IllegalArgumentException.class,
                    () -> ChatOptions.builder().temperature(1.1));
        }
    }

    @Nested
    @DisplayName("MaxTokens 验证")
    class MaxTokensTests {

        @Test
        @DisplayName("有效 maxTokens")
        void shouldAcceptValidMaxTokens() {
            ChatOptions options = ChatOptions.builder()
                    .maxTokens(4096)
                    .build();
            assertEquals(4096, options.getMaxTokens());
        }

        @Test
        @DisplayName("maxTokens = 1 为最小有效值")
        void shouldAcceptMinMaxTokens() {
            ChatOptions options = ChatOptions.builder()
                    .maxTokens(1)
                    .build();
            assertEquals(1, options.getMaxTokens());
        }

        @Test
        @DisplayName("maxTokens <= 0 抛出异常")
        void shouldRejectZeroMaxTokens() {
            assertThrows(IllegalArgumentException.class,
                    () -> ChatOptions.builder().maxTokens(0));
        }

        @Test
        @DisplayName("maxTokens 负数抛出异常")
        void shouldRejectNegativeMaxTokens() {
            assertThrows(IllegalArgumentException.class,
                    () -> ChatOptions.builder().maxTokens(-1));
        }
    }

    @Nested
    @DisplayName("SystemPrompt")
    class SystemPromptTests {

        @Test
        @DisplayName("设置 systemPrompt")
        void shouldAcceptSystemPrompt() {
            ChatOptions options = ChatOptions.builder()
                    .systemPrompt("You are a helpful assistant")
                    .build();
            assertEquals("You are a helpful assistant", options.getSystemPrompt());
        }

        @Test
        @DisplayName("systemPrompt 默认为 null")
        void shouldDefaultToNull() {
            ChatOptions options = ChatOptions.builder().build();
            assertNull(options.getSystemPrompt());
        }
    }

    @Test
    @DisplayName("所有字段组合")
    void shouldBuildWithAllFields() {
        ChatOptions options = ChatOptions.builder()
                .temperature(0.5)
                .maxTokens(2048)
                .systemPrompt("Test prompt")
                .build();

        assertEquals(0.5, options.getTemperature());
        assertEquals(2048, options.getMaxTokens());
        assertEquals("Test prompt", options.getSystemPrompt());
    }

    @Test
    @DisplayName("空 build 所有字段为 null")
    void shouldBuildWithDefaults() {
        ChatOptions options = ChatOptions.builder().build();
        assertNull(options.getTemperature());
        assertNull(options.getMaxTokens());
        assertNull(options.getSystemPrompt());
    }
}
