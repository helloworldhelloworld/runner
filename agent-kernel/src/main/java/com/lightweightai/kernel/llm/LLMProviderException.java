package com.lightweightai.kernel.llm;

/**
 * LLM Provider 调用异常
 *
 * 包含 provider 名称和重试次数等上下文信息，
 * 方便上层（Controller / Gateway）生成友好的错误响应。
 */
public class LLMProviderException extends RuntimeException {

    private final String providerName;
    private final int attempts;

    public LLMProviderException(String message, Throwable cause, String providerName, int attempts) {
        super(message, cause);
        this.providerName = providerName;
        this.attempts = attempts;
    }

    public String getProviderName() {
        return providerName;
    }

    public int getAttempts() {
        return attempts;
    }
}
