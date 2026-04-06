package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LLMProviderException - LLM调用异常")
class LLMProviderExceptionTest {

    @Test
    @DisplayName("包含 providerName")
    void shouldContainProviderName() {
        RuntimeException cause = new RuntimeException("timeout");
        LLMProviderException ex = new LLMProviderException(
            "LLM call failed", cause, "claude", 3);

        assertEquals("claude", ex.getProviderName());
    }

    @Test
    @DisplayName("包含 attempts 次数")
    void shouldContainAttempts() {
        LLMProviderException ex = new LLMProviderException(
            "LLM call failed", null, "gpt-4", 2);

        assertEquals(2, ex.getAttempts());
    }

    @Test
    @DisplayName("继承 RuntimeException 的 message 和 cause")
    void shouldInheritFromRuntimeException() {
        RuntimeException cause = new RuntimeException("network error");
        LLMProviderException ex = new LLMProviderException(
            "LLM failed after retries", cause, "provider", 1);

        assertEquals("LLM failed after retries", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    @DisplayName("cause 可以为 null")
    void shouldAllowNullCause() {
        LLMProviderException ex = new LLMProviderException(
            "LLM failed", null, "provider", 1);

        assertNull(ex.getCause());
    }
}
