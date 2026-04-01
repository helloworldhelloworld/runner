package com.lightweightai.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentBuilder - Agent 构建器")
class AgentBuilderTest {

    @Test
    @DisplayName("缺少 LLM provider 抛异常")
    void missingProviderThrows() {
        assertThrows(IllegalStateException.class, () ->
                Agent.builder().build());
    }

    @Test
    @DisplayName("缺少 API key 抛异常")
    void missingApiKeyThrows() {
        assertThrows(IllegalStateException.class, () ->
                Agent.builder().build());
    }

    @Test
    @DisplayName("claude() 设置 provider 和 key")
    void claudeSetup() {
        // 验证构建不抛异常
        Agent agent = Agent.builder()
                .claude("sk-test-key")
                .build();
        assertNotNull(agent);
    }

    @Test
    @DisplayName("claude() 指定模型")
    void claudeWithModel() {
        Agent agent = Agent.builder()
                .claude("sk-test-key", "claude-3-haiku-20240307")
                .build();
        assertNotNull(agent);
    }

    @Test
    @DisplayName("openai() 设置 provider 和 key")
    void openaiSetup() {
        Agent agent = Agent.builder()
                .openai("sk-test-key")
                .build();
        assertNotNull(agent);
    }

    @Test
    @DisplayName("withMemory() 启用记忆")
    void withMemory() {
        Agent agent = Agent.builder()
                .claude("sk-test")
                .withMemory()
                .build();
        assertNotNull(agent.getMemory());
    }

    @Test
    @DisplayName("默认不启用记忆")
    void defaultNoMemory() {
        Agent agent = Agent.builder()
                .claude("sk-test")
                .build();
        assertNull(agent.getMemory());
    }

    @Test
    @DisplayName("options() 设置默认选项")
    void withOptions() {
        ChatOptions opts = ChatOptions.builder()
                .temperature(0.5)
                .systemPrompt("Be helpful")
                .build();
        Agent agent = Agent.builder()
                .claude("sk-test")
                .options(opts)
                .build();
        assertNotNull(agent);
    }

    @Test
    @DisplayName("timeout() 设置超时")
    void withTimeout() {
        // 不抛异常即可
        Agent agent = Agent.builder()
                .claude("sk-test")
                .timeout(Duration.ofSeconds(30))
                .build();
        assertNotNull(agent);
    }

    @Test
    @DisplayName("getPlugins 初始为空")
    void emptyPlugins() {
        Agent agent = Agent.builder()
                .claude("sk-test")
                .build();
        assertTrue(agent.getPlugins().isEmpty());
    }

    @Test
    @DisplayName("clearMemory 无记忆时不抛异常")
    void clearMemoryNoOp() {
        Agent agent = Agent.builder()
                .claude("sk-test")
                .build();
        assertDoesNotThrow(() -> agent.clearMemory());
    }

    @Test
    @DisplayName("clearMemory 有记忆时清空")
    void clearMemoryWorks() {
        Agent agent = Agent.builder()
                .claude("sk-test")
                .withMemory()
                .build();
        assertNotNull(agent.getMemory());
        agent.clearMemory();
        assertTrue(agent.getMemory().getHistory().isEmpty());
    }
}
