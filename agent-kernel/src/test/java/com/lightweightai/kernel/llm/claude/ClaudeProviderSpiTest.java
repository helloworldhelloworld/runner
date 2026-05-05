package com.lightweightai.kernel.llm.claude;

import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.LLMProviderSpi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClaudeProviderSpi — ServiceLoader entry point for Claude/Anthropic")
class ClaudeProviderSpiTest {

    private final ClaudeProviderSpi spi = new ClaudeProviderSpi();

    @Nested
    @DisplayName("supportedNames")
    class SupportedNames {

        @Test
        @DisplayName("returns both 'claude' and 'anthropic'")
        void returnsBothNames() {
            String[] names = spi.supportedNames();
            assertNotNull(names);
            assertEquals(2, names.length);
            assertArrayEquals(new String[]{"claude", "anthropic"}, names);
        }
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("creates a ClaudeProvider with correct type")
        void createsClaudeProvider() {
            LLMProvider provider = spi.create("test-key", "claude-3-opus");
            assertNotNull(provider);
            assertInstanceOf(ClaudeProvider.class, provider);
        }

        @Test
        @DisplayName("created provider reports 'claude' as provider name")
        void providerNameIsClaude() {
            LLMProvider provider = spi.create("test-key", "claude-3-sonnet");
            assertEquals("claude", provider.getProviderName());
        }
    }

    @Nested
    @DisplayName("LLMProviderSpi contract")
    class SpiContract {

        @Test
        @DisplayName("implements LLMProviderSpi interface")
        void implementsSpiInterface() {
            assertInstanceOf(LLMProviderSpi.class, spi);
        }
    }
}
