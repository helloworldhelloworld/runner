package com.lightweightai.kernel.llm;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Factory for creating LLM providers (SPI-based)
 *
 * 通过 ServiceLoader 自动发现所有 LLMProviderSpi 实现。
 * 新增 Provider 只需：
 * 1. 实现 LLMProviderSpi
 * 2. 注册到 META-INF/services/com.lightweightai.kernel.llm.LLMProviderSpi
 *
 * 不再需要修改本类代码。
 */
public class LLMProviderFactory {

    private static final Map<String, LLMProviderSpi> REGISTRY = new LinkedHashMap<>();

    static {
        for (LLMProviderSpi spi : ServiceLoader.load(LLMProviderSpi.class)) {
            for (String name : spi.supportedNames()) {
                String key = name.toLowerCase().trim();
                LLMProviderSpi existing = REGISTRY.putIfAbsent(key, spi);
                if (existing != null) {
                    throw new IllegalStateException(
                        "Duplicate LLMProviderSpi registration for name '" + key +
                        "': " + existing.getClass().getName() + " vs " + spi.getClass().getName()
                    );
                }
            }
        }
    }

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
        LLMProviderSpi spi = REGISTRY.get(normalizedName);

        if (spi == null) {
            throw new IllegalArgumentException(
                "Unknown provider: " + providerName + ". " +
                "Registered providers: " + REGISTRY.keySet()
            );
        }

        return spi.create(apiKey, model);
    }

    /**
     * Check if a provider is supported
     */
    public static boolean isSupported(String providerName) {
        if (providerName == null) {
            return false;
        }

        return REGISTRY.containsKey(providerName.toLowerCase().trim());
    }
}
