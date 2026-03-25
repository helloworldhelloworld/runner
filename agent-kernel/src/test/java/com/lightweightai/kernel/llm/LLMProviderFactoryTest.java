package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LLMProviderFactory - LLM 提供者工厂")
class LLMProviderFactoryTest {

    @Nested
    @DisplayName("create()")
    class CreateTests {

        @Test
        @DisplayName("claude 创建 ClaudeProvider")
        void createClaudeProvider() {
            LLMProvider provider = LLMProviderFactory.create("claude", "test-key", "claude-sonnet-4-20250514");
            assertNotNull(provider);
        }

        @Test
        @DisplayName("anthropic 别名创建 ClaudeProvider")
        void createWithAnthropicAlias() {
            LLMProvider provider = LLMProviderFactory.create("anthropic", "test-key", "claude-sonnet-4-20250514");
            assertNotNull(provider);
        }

        @Test
        @DisplayName("大小写不敏感")
        void caseInsensitive() {
            LLMProvider provider = LLMProviderFactory.create("CLAUDE", "key", "model");
            assertNotNull(provider);
        }

        @Test
        @DisplayName("带空格的名称被 trim")
        void trimmedName() {
            LLMProvider provider = LLMProviderFactory.create("  claude  ", "key", "model");
            assertNotNull(provider);
        }

        @Test
        @DisplayName("不支持的提供者抛出 IllegalArgumentException")
        void unsupportedProviderThrows() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> LLMProviderFactory.create("gemini", "key", "model"));
            assertTrue(ex.getMessage().contains("Unknown provider"));
        }

        @Test
        @DisplayName("openai 抛出 UnsupportedOperationException")
        void openaiThrowsUnsupported() {
            assertThrows(UnsupportedOperationException.class,
                () -> LLMProviderFactory.create("openai", "key", "model"));
        }

        @Test
        @DisplayName("null 提供者名抛出 IllegalArgumentException")
        void nullProviderThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> LLMProviderFactory.create(null, "key", "model"));
        }

        @Test
        @DisplayName("空字符串提供者名抛出 IllegalArgumentException")
        void emptyProviderThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> LLMProviderFactory.create("", "key", "model"));
        }

        @Test
        @DisplayName("空格提供者名抛出 IllegalArgumentException")
        void blankProviderThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> LLMProviderFactory.create("   ", "key", "model"));
        }
    }

    @Nested
    @DisplayName("isSupported()")
    class IsSupportedTests {

        @Test
        @DisplayName("claude 返回 true")
        void claudeSupported() {
            assertTrue(LLMProviderFactory.isSupported("claude"));
        }

        @Test
        @DisplayName("anthropic 返回 true")
        void anthropicSupported() {
            assertTrue(LLMProviderFactory.isSupported("anthropic"));
        }

        @Test
        @DisplayName("大小写不敏感")
        void caseInsensitive() {
            assertTrue(LLMProviderFactory.isSupported("CLAUDE"));
            assertTrue(LLMProviderFactory.isSupported("Anthropic"));
        }

        @Test
        @DisplayName("openai 返回 false")
        void openaiNotSupported() {
            assertFalse(LLMProviderFactory.isSupported("openai"));
        }

        @Test
        @DisplayName("null 返回 false")
        void nullReturnsFalse() {
            assertFalse(LLMProviderFactory.isSupported(null));
        }

        @Test
        @DisplayName("未知提供者返回 false")
        void unknownReturnsFalse() {
            assertFalse(LLMProviderFactory.isSupported("gemini"));
        }
    }
}
