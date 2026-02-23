package com.lightweightai.kernel.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD: 统一 MemoryProvider 接口测试
 *
 * MemoryProvider 合并三套 Memory 实现：
 * 1. ConversationMemory - 会话记忆
 * 2. FileMemoryManager - 持久化记忆 (Ephemeral/Durable)
 * 3. SearchResult - 记忆检索
 */
@DisplayName("MemoryProvider - 统一记忆接口")
class MemoryProviderTest {

    private MemoryProvider memory;
    private static final String SESSION_ID = "test-session-001";

    @BeforeEach
    void setUp() {
        // 使用内存实现进行测试
        memory = new InMemoryProvider();
    }

    // ==================== 会话记忆 ====================

    @Test
    @DisplayName("添加并获取会话消息")
    void shouldAddAndGetSessionMessages() {
        // Given
        Message userMsg = Message.user("你好，我叫张三");
        Message assistantMsg = Message.assistant("你好张三，很高兴认识你");

        // When
        memory.addMessage(SESSION_ID, userMsg);
        memory.addMessage(SESSION_ID, assistantMsg);
        List<Message> history = memory.getHistory(SESSION_ID, 10);

        // Then
        assertEquals(2, history.size());
        assertEquals("user", history.get(0).getRole());
        assertEquals("你好，我叫张三", history.get(0).getContent());
        assertEquals("assistant", history.get(1).getRole());
    }

    @Test
    @DisplayName("会话隔离 - 不同session独立")
    void shouldIsolateSessionMessages() {
        // Given
        String session1 = "session-1";
        String session2 = "session-2";

        // When
        memory.addMessage(session1, Message.user("Session 1 message"));
        memory.addMessage(session2, Message.user("Session 2 message"));

        // Then
        assertEquals(1, memory.getHistory(session1, 10).size());
        assertEquals(1, memory.getHistory(session2, 10).size());
        assertEquals("Session 1 message", memory.getHistory(session1, 10).get(0).getContent());
    }

    @Test
    @DisplayName("限制返回消息数量")
    void shouldLimitHistorySize() {
        // Given - 添加 5 条消息
        for (int i = 1; i <= 5; i++) {
            memory.addMessage(SESSION_ID, Message.user("Message " + i));
        }

        // When - 只获取最近 3 条
        List<Message> history = memory.getHistory(SESSION_ID, 3);

        // Then - 返回最近的 3 条
        assertEquals(3, history.size());
        assertEquals("Message 3", history.get(0).getContent());
        assertEquals("Message 5", history.get(2).getContent());
    }

    // ==================== 持久化记忆 (OpenClaw风格) ====================

    @Test
    @DisplayName("写入并检索 Ephemeral 记忆")
    void shouldWriteAndSearchEphemeralMemory() {
        // Given
        memory.writeEphemeral("用户张三今天心情不好，工作压力大");
        memory.writeEphemeral("用户提到喜欢听音乐放松");

        // When
        List<MemorySearchResult> results = memory.search("张三 心情");

        // Then
        assertFalse(results.isEmpty());
        assertTrue(results.get(0).getContent().contains("张三"));
    }

    @Test
    @DisplayName("写入并检索 Durable 记忆")
    void shouldWriteAndSearchDurableMemory() {
        // Given
        memory.writeDurable("用户信息", "姓名: 张三");
        memory.writeDurable("用户信息", "爱好: 音乐、阅读");

        // When
        List<MemorySearchResult> results = memory.search("张三 爱好");

        // Then
        assertFalse(results.isEmpty());
    }

    @Test
    @DisplayName("搜索返回相关性排序结果")
    void shouldReturnRankedSearchResults() {
        // Given
        memory.writeEphemeral("用户喜欢喝咖啡");
        memory.writeEphemeral("用户讨厌下雨天");
        memory.writeEphemeral("用户每天早上喝一杯咖啡提神");

        // When
        List<MemorySearchResult> results = memory.search("咖啡");

        // Then
        assertTrue(results.size() >= 2);
        // 结果按相关性排序
        assertTrue(results.get(0).getScore() >= results.get(1).getScore());
    }

    @Test
    @DisplayName("空查询返回空结果")
    void shouldReturnEmptyForNoMatch() {
        // Given
        memory.writeEphemeral("用户喜欢运动");

        // When
        List<MemorySearchResult> results = memory.search("编程 代码");

        // Then
        assertTrue(results.isEmpty());
    }

    // ==================== 清理操作 ====================

    @Test
    @DisplayName("清空会话历史")
    void shouldClearSessionHistory() {
        // Given
        memory.addMessage(SESSION_ID, Message.user("Test message"));
        assertEquals(1, memory.getHistory(SESSION_ID, 10).size());

        // When
        memory.clearSession(SESSION_ID);

        // Then
        assertTrue(memory.getHistory(SESSION_ID, 10).isEmpty());
    }
}
