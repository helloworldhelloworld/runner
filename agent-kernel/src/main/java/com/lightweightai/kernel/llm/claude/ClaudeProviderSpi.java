package com.lightweightai.kernel.llm.claude;

import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.LLMProviderSpi;

/**
 * Claude/Anthropic Provider 的 SPI 注册入口
 */
public class ClaudeProviderSpi implements LLMProviderSpi {

    @Override
    public String[] supportedNames() {
        return new String[]{"claude", "anthropic"};
    }

    @Override
    public LLMProvider create(String apiKey, String model) {
        return new ClaudeProvider(apiKey, model);
    }
}
