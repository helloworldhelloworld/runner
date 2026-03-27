package com.lightweightai.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatOptions - 聊天选项配置测试
 */
@DisplayName("ChatOptions - 聊天选项")
class ChatOptionsTest {

    @Nested
    @DisplayName("Builder 构建")
    class BuilderTests {

        @Test
        @DisplayName("构建完整配置")
        void shouldBuildFullOptions() {
            ChatOptions options = ChatOptions.builder()
                .temperature(0.7)
                .maxTokens(2048)
                .systemPrompt("You are a helpful assistant.")
                .build();

            assertEquals(0.7, options.getTemperature());
            assertEquals(2048, options.getMaxTokens());
            assertEquals("You are a helpful assistant.", options.getSystemPrompt());
        }

        @Test
        @DisplayName("所有字段可选，默认为 null")
        void shouldAllowEmptyBuild() {
            ChatOptions options = ChatOptions.builder().build();

            assertNull(options.getTemperature());
            assertNull(options.getMaxTokens());
            assertNull(options.getSystemPrompt());
        }

        @Test
        @DisplayName("temperature 边界值 0")
        void shouldAcceptZeroTemperature() {
            ChatOptions options = ChatOptions.builder().temperature(0).build();
            assertEquals(0.0, options.getTemperature());
        }

        @Test
        @DisplayName("temperature 边界值 1")
        void shouldAcceptOneTemperature() {
            ChatOptions options = ChatOptions.builder().temperature(1).build();
            assertEquals(1.0, options.getTemperature());
        }
    }

    @Nested
    @DisplayName("参数校验")
    class ValidationTests {

        @Test
        @DisplayName("temperature 小于 0 抛异常")
        void shouldRejectNegativeTemperature() {
            assertThrows(IllegalArgumentException.class,
                () -> ChatOptions.builder().temperature(-0.1));
        }

        @Test
        @DisplayName("temperature 大于 1 抛异常")
        void shouldRejectTemperatureAboveOne() {
            assertThrows(IllegalArgumentException.class,
                () -> ChatOptions.builder().temperature(1.1));
        }

        @Test
        @DisplayName("maxTokens 为 0 抛异常")
        void shouldRejectZeroMaxTokens() {
            assertThrows(IllegalArgumentException.class,
                () -> ChatOptions.builder().maxTokens(0));
        }

        @Test
        @DisplayName("maxTokens 为负数抛异常")
        void shouldRejectNegativeMaxTokens() {
            assertThrows(IllegalArgumentException.class,
                () -> ChatOptions.builder().maxTokens(-100));
        }
    }
}
