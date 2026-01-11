package com.lightweightai.kernel.instruction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ProviderAdapterFactory
 */
class ProviderAdapterFactoryTest {

    @Test
    void shouldCreateClaudeAdapter() {
        ProviderAdapter adapter = ProviderAdapterFactory.create("claude");

        assertNotNull(adapter);
        assertEquals("claude", adapter.getProviderName());
        assertTrue(adapter.supports("claude"));
    }

    @Test
    void shouldCreateAnthropicAdapter() {
        ProviderAdapter adapter = ProviderAdapterFactory.create("anthropic");

        assertNotNull(adapter);
        assertTrue(adapter.supports("anthropic"));
    }

    @Test
    void shouldBeCaseInsensitive() {
        ProviderAdapter adapter1 = ProviderAdapterFactory.create("CLAUDE");
        ProviderAdapter adapter2 = ProviderAdapterFactory.create("Claude");
        ProviderAdapter adapter3 = ProviderAdapterFactory.create("claude");

        assertEquals(adapter1.getProviderName(), adapter2.getProviderName());
        assertEquals(adapter2.getProviderName(), adapter3.getProviderName());
    }

    @Test
    void shouldThrowForUnsupportedProvider() {
        assertThrows(IllegalArgumentException.class, () ->
            ProviderAdapterFactory.create("unknown-provider")
        );
    }

    @Test
    void shouldCheckSupport() {
        assertTrue(ProviderAdapterFactory.isSupported("claude"));
        assertTrue(ProviderAdapterFactory.isSupported("anthropic"));
        assertFalse(ProviderAdapterFactory.isSupported("unknown"));
    }

    @Test
    void shouldListSupportedProviders() {
        var supported = ProviderAdapterFactory.getSupportedProviders();

        assertNotNull(supported);
        assertFalse(supported.isEmpty());
        assertTrue(supported.contains("claude"));
    }

    @Test
    void shouldAllowCustomRegistration() {
        // Register a custom adapter
        ProviderAdapter customAdapter = new ProviderAdapter() {
            @Override
            public String getProviderName() {
                return "custom";
            }

            @Override
            public boolean supports(String providerName) {
                return "custom".equals(providerName);
            }

            @Override
            public com.lightweightai.kernel.llm.ConversationMessage formatAsSystemMessage(
                InstructionPackage pkg
            ) {
                return null;
            }

            @Override
            public com.lightweightai.kernel.llm.ConversationMessage formatAsUserPrefix(
                InstructionPackage pkg,
                String userMessage
            ) {
                return null;
            }

            @Override
            public java.util.List<com.lightweightai.kernel.llm.ConversationMessage> injectInstructions(
                java.util.List<com.lightweightai.kernel.llm.ConversationMessage> messages,
                java.util.List<InstructionPackage> packages
            ) {
                return messages;
            }

            @Override
            public ProviderCapabilities getCapabilities() {
                return ProviderCapabilities.minimal();
            }
        };

        ProviderAdapterFactory.registerInstance("custom", customAdapter);

        assertTrue(ProviderAdapterFactory.isSupported("custom"));

        ProviderAdapter retrieved = ProviderAdapterFactory.create("custom");
        assertEquals("custom", retrieved.getProviderName());
    }

    @Test
    void shouldCreateDefaultAdapter() {
        ProviderAdapter adapter = ProviderAdapterFactory.defaultAdapter();

        assertNotNull(adapter);
        // Should be Claude by default
        assertEquals("claude", adapter.getProviderName());
    }

    @Test
    void shouldCreateClaudeAdapterViaConvenience() {
        ProviderAdapter adapter = ProviderAdapterFactory.claude();

        assertNotNull(adapter);
        assertEquals("claude", adapter.getProviderName());
    }
}
