package com.lightweightai.kernel.instruction;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for creating provider-specific adapters
 *
 * Provides a centralized way to get the correct adapter for different
 * LLM providers. Supports both built-in adapters and custom registrations.
 */
public class ProviderAdapterFactory {

    private static final Map<String, Class<? extends ProviderAdapter>> adapters = new HashMap<>();
    private static final Map<String, ProviderAdapter> instances = new HashMap<>();

    static {
        // Register built-in adapters
        registerBuiltInAdapters();
    }

    private static void registerBuiltInAdapters() {
        try {
            // Register Claude adapter
            Class<?> claudeAdapter = Class.forName(
                "com.lightweightai.kernel.instruction.claude.ClaudeProviderAdapter"
            );
            if (ProviderAdapter.class.isAssignableFrom(claudeAdapter)) {
                @SuppressWarnings("unchecked")
                Class<? extends ProviderAdapter> adapterClass =
                    (Class<? extends ProviderAdapter>) claudeAdapter;
                register("claude", adapterClass);
                register("anthropic", adapterClass);
            }
        } catch (ClassNotFoundException e) {
            // Claude adapter not available, skip
        }

        // Future: Register other built-in adapters
        // register("openai", OpenAIProviderAdapter.class);
        // register("gemini", GeminiProviderAdapter.class);
    }

    /**
     * Register a provider adapter class
     */
    public static void register(String providerName, Class<? extends ProviderAdapter> adapterClass) {
        adapters.put(providerName.toLowerCase(), adapterClass);
    }

    /**
     * Register a provider adapter instance
     */
    public static void registerInstance(String providerName, ProviderAdapter adapter) {
        instances.put(providerName.toLowerCase(), adapter);
    }

    /**
     * Create an adapter for the specified provider
     *
     * @param providerName Provider name (case-insensitive)
     * @return Provider adapter instance
     * @throws IllegalArgumentException if provider not supported
     */
    public static ProviderAdapter create(String providerName) {
        String normalizedName = providerName.toLowerCase();

        // Check for registered instance first
        if (instances.containsKey(normalizedName)) {
            return instances.get(normalizedName);
        }

        // Try to create from registered class
        if (adapters.containsKey(normalizedName)) {
            try {
                Class<? extends ProviderAdapter> adapterClass = adapters.get(normalizedName);

                // Try to get singleton instance via getInstance()
                try {
                    return (ProviderAdapter) adapterClass.getMethod("getInstance").invoke(null);
                } catch (NoSuchMethodException e) {
                    // No getInstance, create new instance
                    return adapterClass.getDeclaredConstructor().newInstance();
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to create adapter for " + providerName, e);
            }
        }

        throw new IllegalArgumentException("No adapter registered for provider: " + providerName);
    }

    /**
     * Check if a provider is supported
     */
    public static boolean isSupported(String providerName) {
        String normalizedName = providerName.toLowerCase();
        return instances.containsKey(normalizedName) ||
               adapters.containsKey(normalizedName);
    }

    /**
     * Get all supported provider names
     */
    public static java.util.Set<String> getSupportedProviders() {
        java.util.Set<String> providers = new java.util.HashSet<>();
        providers.addAll(instances.keySet());
        providers.addAll(adapters.keySet());
        return providers;
    }

    /**
     * Create Claude adapter (convenience method)
     */
    public static ProviderAdapter claude() {
        return create("claude");
    }

    /**
     * Create a default adapter
     *
     * Returns Claude adapter if available, otherwise throws exception
     */
    public static ProviderAdapter defaultAdapter() {
        if (isSupported("claude")) {
            return claude();
        }
        throw new IllegalStateException("No default adapter available");
    }
}
