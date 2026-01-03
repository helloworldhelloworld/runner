package com.lightweightai.kernel.llm;

import com.lightweightai.kernel.llm.claude.ClaudeProvider;

/**
 * Factory for creating LLM providers
 */
public class LLMProviderFactory {

    /**
     * Create an LLM provider based on provider name
     *
     * @param providerName Provider name ("claude", "openai", etc.)
     * @param apiKey API key
     * @param model Model name
     * @return LLMProvider instance
     */
    public static LLMProvider create(String providerName, String apiKey, String model) {
        if (providerName == null || providerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Provider name cannot be null or empty");
        }

        String normalizedName = providerName.toLowerCase().trim();

        switch (normalizedName) {
            case "claude":
            case "anthropic":
                return new ClaudeProvider(apiKey, model);

            case "openai":
                // TODO: Implement OpenAI provider
                throw new UnsupportedOperationException(
                    "OpenAI provider not yet implemented. " +
                    "Please use 'claude' provider or implement OpenAIProvider."
                );

            default:
                throw new IllegalArgumentException(
                    "Unknown provider: " + providerName + ". " +
                    "Supported providers: claude, openai (coming soon)"
                );
        }
    }

    /**
     * Check if a provider is supported
     */
    public static boolean isSupported(String providerName) {
        if (providerName == null) {
            return false;
        }

        String normalizedName = providerName.toLowerCase().trim();
        return "claude".equals(normalizedName) || "anthropic".equals(normalizedName);
    }
}
