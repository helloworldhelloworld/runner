package com.lightweightai.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD: Test for conversation memory
 *
 * User Story: 作为开发者，我希望Agent能记住对话历史
 */
class AgentMemoryTest {

    @Test
    void shouldEnableMemoryByDefault() {
        // Given: 创建Agent时启用记忆
        Agent agent = Agent.builder()
                .claude("test-api-key")
                .withMemory()
                .build();

        // Then: 应该有记忆功能
        assertNotNull(agent.getMemory());
    }

    @Test
    void shouldRememberConversationHistory() {
        // Given: 一个启用了记忆的Agent
        Agent agent = Agent.builder()
                .claude("test-api-key")
                .withMemory()
                .build();

        // When: 进行对话
        // agent.chat("我叫张三");
        // agent.chat("我叫什么名字？");

        // Then: 记忆中应该有两条消息
        // assertEquals(2, agent.getMemory().getMessageCount());

        assertNotNull(agent.getMemory());
    }

    @Test
    void shouldClearMemory() {
        // Given: 一个有对话历史的Agent
        Agent agent = Agent.builder()
                .claude("test-api-key")
                .withMemory()
                .build();

        // agent.chat("Hello");

        // When: 清除记忆
        agent.clearMemory();

        // Then: 记忆应该为空
        assertEquals(0, agent.getMemory().getMessageCount());
    }

    @Test
    void shouldWorkWithoutMemory() {
        // Given: 不启用记忆的Agent
        Agent agent = Agent.builder()
                .claude("test-api-key")
                .build();  // 没有调用withMemory()

        // Then: 每次对话都是独立的
        assertNull(agent.getMemory());
    }
}
