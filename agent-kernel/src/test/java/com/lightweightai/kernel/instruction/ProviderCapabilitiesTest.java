package com.lightweightai.kernel.instruction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProviderAdapter.ProviderCapabilities - 提供者能力描述")
class ProviderCapabilitiesTest {

    @Nested
    @DisplayName("Claude 能力")
    class ClaudeTests {

        @Test
        @DisplayName("Claude 支持系统消息")
        void shouldSupportSystemMessages() {
            var caps = ProviderAdapter.ProviderCapabilities.claude();
            assertTrue(caps.supportsSystemMessages());
        }

        @Test
        @DisplayName("Claude 支持指令")
        void shouldSupportInstructions() {
            var caps = ProviderAdapter.ProviderCapabilities.claude();
            assertTrue(caps.supportsInstructions());
        }

        @Test
        @DisplayName("Claude 支持资源")
        void shouldSupportResources() {
            var caps = ProviderAdapter.ProviderCapabilities.claude();
            assertTrue(caps.supportsResources());
        }

        @Test
        @DisplayName("Claude 最大指令长度为 200000")
        void shouldHaveCorrectMaxLength() {
            var caps = ProviderAdapter.ProviderCapabilities.claude();
            assertEquals(200000, caps.getMaxInstructionLength());
        }

        @Test
        @DisplayName("Claude 支持多指令")
        void shouldSupportMultipleInstructions() {
            var caps = ProviderAdapter.ProviderCapabilities.claude();
            assertTrue(caps.supportsMultipleInstructions());
        }
    }

    @Nested
    @DisplayName("OpenAI 能力")
    class OpenAITests {

        @Test
        @DisplayName("OpenAI 支持系统消息")
        void shouldSupportSystemMessages() {
            var caps = ProviderAdapter.ProviderCapabilities.openai();
            assertTrue(caps.supportsSystemMessages());
        }

        @Test
        @DisplayName("OpenAI 不支持资源")
        void shouldNotSupportResources() {
            var caps = ProviderAdapter.ProviderCapabilities.openai();
            assertFalse(caps.supportsResources());
        }

        @Test
        @DisplayName("OpenAI 最大指令长度为 128000")
        void shouldHaveCorrectMaxLength() {
            var caps = ProviderAdapter.ProviderCapabilities.openai();
            assertEquals(128000, caps.getMaxInstructionLength());
        }
    }

    @Nested
    @DisplayName("Minimal 能力")
    class MinimalTests {

        @Test
        @DisplayName("Minimal 不支持系统消息")
        void shouldNotSupportSystemMessages() {
            var caps = ProviderAdapter.ProviderCapabilities.minimal();
            assertFalse(caps.supportsSystemMessages());
        }

        @Test
        @DisplayName("Minimal 支持指令（基础功能）")
        void shouldSupportInstructions() {
            var caps = ProviderAdapter.ProviderCapabilities.minimal();
            assertTrue(caps.supportsInstructions());
        }

        @Test
        @DisplayName("Minimal 不支持资源")
        void shouldNotSupportResources() {
            var caps = ProviderAdapter.ProviderCapabilities.minimal();
            assertFalse(caps.supportsResources());
        }

        @Test
        @DisplayName("Minimal 最大指令长度为 4096")
        void shouldHaveSmallMaxLength() {
            var caps = ProviderAdapter.ProviderCapabilities.minimal();
            assertEquals(4096, caps.getMaxInstructionLength());
        }

        @Test
        @DisplayName("Minimal 不支持多指令")
        void shouldNotSupportMultipleInstructions() {
            var caps = ProviderAdapter.ProviderCapabilities.minimal();
            assertFalse(caps.supportsMultipleInstructions());
        }
    }

    @Nested
    @DisplayName("自定义能力")
    class CustomTests {

        @Test
        @DisplayName("自定义构造器设置所有字段")
        void shouldAllowCustomCapabilities() {
            var caps = new ProviderAdapter.ProviderCapabilities(
                    true, false, true, 50000, false
            );

            assertTrue(caps.supportsSystemMessages());
            assertFalse(caps.supportsInstructions());
            assertTrue(caps.supportsResources());
            assertEquals(50000, caps.getMaxInstructionLength());
            assertFalse(caps.supportsMultipleInstructions());
        }
    }
}
