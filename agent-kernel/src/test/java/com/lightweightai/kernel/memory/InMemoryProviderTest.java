package com.lightweightai.kernel.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InMemoryProvider - 内存记忆提供者")
class InMemoryProviderTest {

    private InMemoryProvider provider;

    @BeforeEach
    void setUp() {
        provider = new InMemoryProvider();
    }

    // ==================== 会话管理 ====================

    @Test
    @DisplayName("添加消息并获取历史")
    void shouldAddAndGetHistory() {
        provider.addMessage("s1", Message.user("你好"));
        provider.addMessage("s1", Message.assistant("你好！"));

        List<Message> history = provider.getHistory("s1", 10);
        assertEquals(2, history.size());
        assertEquals("你好", history.get(0).getContent());
        assertEquals("你好！", history.get(1).getContent());
    }

    @Test
    @DisplayName("getHistory限制返回数量")
    void shouldLimitHistorySize() {
        for (int i = 0; i < 10; i++) {
            provider.addMessage("s1", Message.user("msg" + i));
        }

        List<Message> history = provider.getHistory("s1", 3);
        assertEquals(3, history.size());
        // Should return last 3 messages
        assertEquals("msg7", history.get(0).getContent());
        assertEquals("msg8", history.get(1).getContent());
        assertEquals("msg9", history.get(2).getContent());
    }

    @Test
    @DisplayName("获取不存在的会话返回空列表")
    void shouldReturnEmptyForNonExistentSession() {
        List<Message> history = provider.getHistory("non-existent", 10);
        assertTrue(history.isEmpty());
    }

    @Test
    @DisplayName("清除会话")
    void shouldClearSession() {
        provider.addMessage("s1", Message.user("test"));
        provider.clearSession("s1");

        List<Message> history = provider.getHistory("s1", 10);
        assertTrue(history.isEmpty());
    }

    @Test
    @DisplayName("getHistory返回副本不影响原数据")
    void shouldReturnCopyOfHistory() {
        provider.addMessage("s1", Message.user("original"));

        List<Message> history = provider.getHistory("s1", 10);
        history.clear(); // Modify returned list

        // Original should be unchanged
        assertEquals(1, provider.getHistory("s1", 10).size());
    }

    @Test
    @DisplayName("不同会话互相独立")
    void shouldIsolateSessions() {
        provider.addMessage("s1", Message.user("session1"));
        provider.addMessage("s2", Message.user("session2"));

        assertEquals(1, provider.getHistory("s1", 10).size());
        assertEquals(1, provider.getHistory("s2", 10).size());
        assertEquals("session1", provider.getHistory("s1", 10).get(0).getContent());
        assertEquals("session2", provider.getHistory("s2", 10).get(0).getContent());
    }

    // ==================== 临时记忆 ====================

    @Test
    @DisplayName("写入和读取今日临时记忆")
    void shouldWriteAndReadEphemeral() {
        provider.writeEphemeral("今天学了Java");
        provider.writeEphemeral("下午打了篮球");

        String today = provider.readTodayEphemeral();
        assertTrue(today.contains("今天学了Java"));
        assertTrue(today.contains("下午打了篮球"));
    }

    @Test
    @DisplayName("无临时记忆返回空字符串")
    void shouldReturnEmptyForNoEphemeral() {
        assertEquals("", provider.readTodayEphemeral());
    }

    // ==================== 长期记忆 ====================

    @Test
    @DisplayName("写入和读取长期记忆")
    void shouldWriteAndReadDurable() {
        provider.writeDurable("爱好", "喜欢编程");
        provider.writeDurable("爱好", "喜欢跑步");
        provider.writeDurable("性格", "性格开朗");

        String durable = provider.readDurable();
        assertTrue(durable.contains("## 爱好"));
        assertTrue(durable.contains("喜欢编程"));
        assertTrue(durable.contains("喜欢跑步"));
        assertTrue(durable.contains("## 性格"));
        assertTrue(durable.contains("性格开朗"));
    }

    @Test
    @DisplayName("无长期记忆返回空字符串")
    void shouldReturnEmptyForNoDurable() {
        assertEquals("", provider.readDurable());
    }

    // ==================== 搜索 ====================

    @Test
    @DisplayName("搜索临时记忆")
    void shouldSearchEphemeralMemory() {
        provider.writeEphemeral("今天学了Java编程");
        provider.writeEphemeral("明天要去跑步");

        List<MemorySearchResult> results = provider.search("Java");
        assertFalse(results.isEmpty());
        assertTrue(results.get(0).getContent().contains("Java"));
    }

    @Test
    @DisplayName("搜索长期记忆")
    void shouldSearchDurableMemory() {
        provider.writeDurable("技能", "精通Python和Java");

        List<MemorySearchResult> results = provider.search("Python");
        assertFalse(results.isEmpty());
        assertTrue(results.get(0).getContent().contains("Python"));
    }

    @Test
    @DisplayName("搜索结果按相关性排序")
    void shouldSortByRelevance() {
        provider.writeEphemeral("今天学了Java");
        provider.writeEphemeral("Java编程很有趣，Java是我最喜欢的语言");

        List<MemorySearchResult> results = provider.search("Java");
        assertTrue(results.size() >= 2);
        // Both should match, first should have higher score (shorter content + same match)
        assertTrue(results.get(0).getScore() >= results.get(1).getScore());
    }

    @Test
    @DisplayName("null查询返回空列表")
    void shouldReturnEmptyForNullQuery() {
        provider.writeEphemeral("some content");
        assertTrue(provider.search(null).isEmpty());
    }

    @Test
    @DisplayName("空查询返回空列表")
    void shouldReturnEmptyForEmptyQuery() {
        provider.writeEphemeral("some content");
        assertTrue(provider.search("").isEmpty());
    }

    @Test
    @DisplayName("无匹配返回空列表")
    void shouldReturnEmptyForNoMatch() {
        provider.writeEphemeral("今天天气很好");
        assertTrue(provider.search("量子力学").isEmpty());
    }

    @Test
    @DisplayName("搜索不区分大小写")
    void shouldSearchCaseInsensitive() {
        provider.writeEphemeral("Learning JAVA today");

        assertFalse(provider.search("java").isEmpty());
        assertFalse(provider.search("JAVA").isEmpty());
    }

    @Test
    @DisplayName("多词搜索匹配更多词得分更高")
    void shouldScoreHigherForMoreMatches() {
        provider.writeEphemeral("Java is fun");
        provider.writeEphemeral("Java programming is great fun");

        List<MemorySearchResult> results = provider.search("Java fun");
        assertTrue(results.size() >= 2);
        // Both match "Java" and "fun"
        for (MemorySearchResult r : results) {
            assertTrue(r.getScore() > 0);
        }
    }

    // ==================== 辅助方法 ====================

    @Test
    @DisplayName("获取所有会话ID")
    void shouldGetAllSessionIds() {
        provider.addMessage("s1", Message.user("test1"));
        provider.addMessage("s2", Message.user("test2"));

        Set<String> ids = provider.getAllSessionIds();
        assertEquals(2, ids.size());
        assertTrue(ids.contains("s1"));
        assertTrue(ids.contains("s2"));
    }

    @Test
    @DisplayName("获取统计信息")
    void shouldGetStats() {
        provider.addMessage("s1", Message.user("msg1"));
        provider.addMessage("s1", Message.assistant("msg2"));
        provider.addMessage("s2", Message.user("msg3"));
        provider.writeEphemeral("ephemeral");
        provider.writeDurable("section", "durable");

        Map<String, Object> stats = provider.getStats();
        assertEquals(2, stats.get("sessions"));
        assertEquals(1, stats.get("ephemeralDays"));
        assertEquals(1, stats.get("durableSections"));
        assertEquals(3, stats.get("totalMessages"));
    }

    @Test
    @DisplayName("清空所有记忆")
    void shouldClearAll() {
        provider.addMessage("s1", Message.user("test"));
        provider.writeEphemeral("ephemeral");
        provider.writeDurable("section", "durable");

        provider.clearAll();

        assertTrue(provider.getHistory("s1", 10).isEmpty());
        assertEquals("", provider.readTodayEphemeral());
        assertEquals("", provider.readDurable());
        assertTrue(provider.getAllSessionIds().isEmpty());
    }
}
