package com.lightweightai.web.skillcreator;

import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InMemoryMemoryProvider - 轻量内存会话提供者")
class InMemoryMemoryProviderTest {

    private InMemoryMemoryProvider provider;

    @BeforeEach
    void setUp() {
        provider = new InMemoryMemoryProvider();
    }

    // ==================== 会话历史 ====================

    @Test
    @DisplayName("addMessage 和 getHistory 正常工作")
    void addAndGetHistory() {
        provider.addMessage("s1", Message.user("hello"));
        provider.addMessage("s1", Message.assistant("hi"));

        List<Message> history = provider.getHistory("s1", 10);
        assertEquals(2, history.size());
        assertEquals("hello", history.get(0).getContent());
        assertEquals("hi", history.get(1).getContent());
    }

    @Test
    @DisplayName("getHistory limit 返回最近 N 条")
    void getHistoryWithLimit() {
        provider.addMessage("s1", Message.user("a"));
        provider.addMessage("s1", Message.assistant("b"));
        provider.addMessage("s1", Message.user("c"));
        provider.addMessage("s1", Message.assistant("d"));

        List<Message> history = provider.getHistory("s1", 2);
        assertEquals(2, history.size());
        assertEquals("c", history.get(0).getContent());
        assertEquals("d", history.get(1).getContent());
    }

    @Test
    @DisplayName("getHistory limit 大于消息数返回全部")
    void getHistoryLimitLargerThanSize() {
        provider.addMessage("s1", Message.user("only"));
        List<Message> history = provider.getHistory("s1", 100);
        assertEquals(1, history.size());
    }

    @Test
    @DisplayName("不同 sessionId 隔离")
    void sessionIsolation() {
        provider.addMessage("s1", Message.user("session1"));
        provider.addMessage("s2", Message.user("session2"));

        List<Message> h1 = provider.getHistory("s1", 10);
        List<Message> h2 = provider.getHistory("s2", 10);

        assertEquals(1, h1.size());
        assertEquals("session1", h1.get(0).getContent());
        assertEquals(1, h2.size());
        assertEquals("session2", h2.get(0).getContent());
    }

    @Test
    @DisplayName("不存在的 session 返回空列表")
    void nonexistentSessionReturnsEmpty() {
        List<Message> history = provider.getHistory("nonexistent", 10);
        assertTrue(history.isEmpty());
    }

    // ==================== clearSession ====================

    @Test
    @DisplayName("clearSession 清空指定会话")
    void clearSession() {
        provider.addMessage("s1", Message.user("a"));
        provider.addMessage("s2", Message.user("b"));

        provider.clearSession("s1");

        assertTrue(provider.getHistory("s1", 10).isEmpty());
        assertFalse(provider.getHistory("s2", 10).isEmpty());
    }

    @Test
    @DisplayName("clearSession 不存在的 session 不报错")
    void clearNonexistentSession() {
        assertDoesNotThrow(() -> provider.clearSession("nonexistent"));
    }

    // ==================== 持久化记忆（no-op） ====================

    @Test
    @DisplayName("writeEphemeral 是 no-op 不报错")
    void writeEphemeralNoOp() {
        assertDoesNotThrow(() -> provider.writeEphemeral("some content"));
    }

    @Test
    @DisplayName("writeDurable 是 no-op 不报错")
    void writeDurableNoOp() {
        assertDoesNotThrow(() -> provider.writeDurable("section", "content"));
    }

    // ==================== 搜索 ====================

    @Test
    @DisplayName("search 返回空列表")
    void searchReturnsEmpty() {
        List<MemorySearchResult> results = provider.search("anything");
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    // ==================== 返回值不可变性 ====================

    @Test
    @DisplayName("getHistory 返回的列表修改不影响内部状态")
    void historyReturnsCopy() {
        provider.addMessage("s1", Message.user("original"));
        List<Message> history = provider.getHistory("s1", 10);

        history.clear();

        assertEquals(1, provider.getHistory("s1", 10).size());
    }
}
