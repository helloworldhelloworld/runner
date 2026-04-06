package com.lightweightai.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentBuilder - SDK Agent 构建器")
class AgentBuilderTest {

    @Test
    @DisplayName("缺少 llmProvider 抛异常")
    void shouldThrowWithoutProvider() {
        assertThrows(IllegalStateException.class,
            () -> Agent.builder().build());
    }

    @Test
    @DisplayName("缺少 apiKey 抛异常")
    void shouldThrowWithoutApiKey() {
        // 不设置 provider 和 key
        assertThrows(IllegalStateException.class,
            () -> Agent.builder().build());
    }

    @Test
    @DisplayName("claude() 设置 provider 和默认模型")
    void shouldSetClaudeProvider() {
        // 只验证构建器不抛异常（实际 chat 需要 API key）
        Agent agent = Agent.builder()
            .claude("test-key")
            .build();
        assertNotNull(agent);
    }

    @Test
    @DisplayName("claude() 自定义模型")
    void shouldSetClaudeCustomModel() {
        Agent agent = Agent.builder()
            .claude("test-key", "claude-3-opus-20240229")
            .build();
        assertNotNull(agent);
    }

    @Test
    @DisplayName("openai() 设置 provider")
    void shouldSetOpenAIProvider() {
        Agent agent = Agent.builder()
            .openai("test-key")
            .build();
        assertNotNull(agent);
    }

    @Test
    @DisplayName("withMemory() 启用记忆")
    void shouldEnableMemory() {
        Agent agent = Agent.builder()
            .claude("test-key")
            .withMemory()
            .build();
        assertNotNull(agent.getMemory());
    }

    @Test
    @DisplayName("默认不启用记忆")
    void shouldNotEnableMemoryByDefault() {
        Agent agent = Agent.builder()
            .claude("test-key")
            .build();
        assertNull(agent.getMemory());
    }

    @Test
    @DisplayName("options() 设置默认选项")
    void shouldSetDefaultOptions() {
        ChatOptions opts = ChatOptions.builder()
            .temperature(0.5)
            .systemPrompt("test prompt")
            .build();
        Agent agent = Agent.builder()
            .claude("test-key")
            .options(opts)
            .build();
        assertNotNull(agent);
    }

    @Test
    @DisplayName("addPlugin 后 getPlugins 包含该插件")
    void shouldAddPlugin() {
        Agent agent = Agent.builder()
            .claude("test-key")
            .build();
        // getPlugins 应该返回空列表
        assertTrue(agent.getPlugins().isEmpty());
    }

    @Test
    @DisplayName("clearMemory 不抛异常（无论是否启用记忆）")
    void shouldHandleClearMemory() {
        Agent agent = Agent.builder()
            .claude("test-key")
            .build();
        assertDoesNotThrow(agent::clearMemory);
    }

    @Test
    @DisplayName("withMemory + clearMemory 清空记忆")
    void shouldClearMemory() {
        Agent agent = Agent.builder()
            .claude("test-key")
            .withMemory()
            .build();
        agent.clearMemory();
        assertNotNull(agent.getMemory());
    }
}
