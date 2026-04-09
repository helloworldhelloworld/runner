package com.lightweightai.kernel.llm;

/**
 * LLM Provider SPI — 供 ServiceLoader 发现的 Provider 工厂接口
 *
 * 每个 Provider 实现（Claude、OpenAI、OpenRouter 等）提供一个此接口的实现，
 * 放入 META-INF/services/com.lightweightai.kernel.llm.LLMProviderSpi 即可自动注册。
 *
 * 参考: Claude Code 泄露源码的可插拔 provider 模型；
 *       runner 原有 LLMProviderFactory 的 switch-case 硬编码问题。
 */
public interface LLMProviderSpi {

    /**
     * 此工厂支持的 provider 名称列表（小写，如 "claude", "anthropic"）
     */
    String[] supportedNames();

    /**
     * 创建 LLMProvider 实例
     *
     * @param apiKey API 密钥
     * @param model  模型名称
     * @return LLMProvider 实例
     */
    LLMProvider create(String apiKey, String model);
}
