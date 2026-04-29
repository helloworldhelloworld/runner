package com.lightweightai.web.skillcreator;

import com.lightweightai.kernel.memory.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InMemoryMemoryProvider - Skill Creator 会话记忆")
class InMemoryMemoryProviderTest {

    private InMemoryMemoryProvider provider;

    @BeforeEach
    void setUp() {
        provider = new InMemoryMemoryProvider();
    }

    @Test
    @DisplayName("addMessage 后 getHistory 可获取消息")
    void addAndGetHistory() {
        provider.addMessage("s1", Message.user("hello"));
        provider.addMessage("s1", Message.assistant("hi"));

        List<Message> history = provider.getHistory("s1", 10);
        assertEquals(2, history.size());
        assertEquals("hello", history.get(0).getContent());
        assertEquals("hi", history.get(1).getContent());
    }

    @Test
    @DisplayName("getHistory limit 限制返回最近的消息")
    void getHistoryWithLimit() {
        provider.addMessage("s1", Message.user("msg1"));
        provider.addMessage("s1", Message.assistant("msg2"));
        provider.addMessage("s1", Message.user("msg3"));
        provider.addMessage("s1", Message.assistant("msg4"));

        List<Message> history = provider.getHistory("s1", 2);
        assertEquals(2, history.size());
        assertEquals("msg3", history.get(0).getContent());
        assertEquals("msg4", history.get(1).getContent());
    }

    @Test
    @DisplayName("不同 sessionId 互相隔离")
    void sessionsAreIsolated() {
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
    @DisplayName("clearSession 清空指定会话")
    void clearSessionRemovesHistory() {
        provider.addMessage("s1", Message.user("hello"));
        provider.addMessage("s2", Message.user("world"));

        provider.clearSession("s1");

        assertTrue(provider.getHistory("s1", 10).isEmpty());
        assertEquals(1, provider.getHistory("s2", 10).size());
    }

    @Test
    @DisplayName("不存在的 sessionId 返回空列表")
    void nonExistentSessionReturnsEmpty() {
        List<Message> history = provider.getHistory("nonexistent", 10);
        assertTrue(history.isEmpty());
    }

    @Test
    @DisplayName("getHistory 返回副本，修改不影响内部状态")
    void getHistoryReturnsCopy() {
        provider.addMessage("s1", Message.user("hello"));

        List<Message> history = provider.getHistory("s1", 10);
        history.clear();

        assertEquals(1, provider.getHistory("s1", 10).size());
    }

    @Test
    @DisplayName("writeEphemeral 和 writeDurable 为 no-op 不抛异常")
    void noOpMethodsDontThrow() {
        assertDoesNotThrow(() -> provider.writeEphemeral("content"));
        assertDoesNotThrow(() -> provider.writeDurable("section", "content"));
    }

    @Test
    @DisplayName("search 返回空列表")
    void searchReturnsEmpty() {
        provider.addMessage("s1", Message.user("hello"));
        assertTrue(provider.search("hello").isEmpty());
    }
}
