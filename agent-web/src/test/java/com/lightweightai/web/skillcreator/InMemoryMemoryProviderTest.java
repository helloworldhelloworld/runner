package com.lightweightai.web.skillcreator;

import com.lightweightai.kernel.memory.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InMemoryMemoryProvider — lightweight session memory for skill creator")
class InMemoryMemoryProviderTest {

    private InMemoryMemoryProvider provider;

    @BeforeEach
    void setUp() {
        provider = new InMemoryMemoryProvider();
    }

    @Test
    @DisplayName("addMessage and getHistory round-trip")
    void addAndRetrieve() {
        provider.addMessage("s1", Message.user("hello"));
        provider.addMessage("s1", Message.assistant("hi there"));

        List<Message> history = provider.getHistory("s1", 10);

        assertEquals(2, history.size());
    }

    @Test
    @DisplayName("getHistory respects limit — returns most recent messages")
    void historyRespectsLimit() {
        provider.addMessage("s1", Message.user("m1"));
        provider.addMessage("s1", Message.user("m2"));
        provider.addMessage("s1", Message.user("m3"));

        List<Message> history = provider.getHistory("s1", 2);

        assertEquals(2, history.size());
    }

    @Test
    @DisplayName("getHistory returns all when fewer than limit")
    void historyReturnsAllWhenFewer() {
        provider.addMessage("s1", Message.user("m1"));

        List<Message> history = provider.getHistory("s1", 10);

        assertEquals(1, history.size());
    }

    @Test
    @DisplayName("different sessions are isolated")
    void sessionIsolation() {
        provider.addMessage("s1", Message.user("s1-msg"));
        provider.addMessage("s2", Message.user("s2-msg"));

        List<Message> s1History = provider.getHistory("s1", 10);
        List<Message> s2History = provider.getHistory("s2", 10);

        assertEquals(1, s1History.size());
        assertEquals(1, s2History.size());
    }

    @Test
    @DisplayName("clearSession removes all messages for that session")
    void clearSession() {
        provider.addMessage("s1", Message.user("msg"));
        provider.clearSession("s1");

        List<Message> history = provider.getHistory("s1", 10);
        assertTrue(history.isEmpty());
    }

    @Test
    @DisplayName("clearSession does not affect other sessions")
    void clearSessionIsolated() {
        provider.addMessage("s1", Message.user("s1-msg"));
        provider.addMessage("s2", Message.user("s2-msg"));

        provider.clearSession("s1");

        assertTrue(provider.getHistory("s1", 10).isEmpty());
        assertEquals(1, provider.getHistory("s2", 10).size());
    }

    @Test
    @DisplayName("getHistory for unknown session returns empty list")
    void unknownSessionReturnsEmpty() {
        List<Message> history = provider.getHistory("nonexistent", 10);
        assertTrue(history.isEmpty());
    }

    @Test
    @DisplayName("writeEphemeral is no-op (does not throw)")
    void writeEphemeralNoOp() {
        assertDoesNotThrow(() -> provider.writeEphemeral("some content"));
    }

    @Test
    @DisplayName("writeDurable is no-op (does not throw)")
    void writeDurableNoOp() {
        assertDoesNotThrow(() -> provider.writeDurable("section", "content"));
    }

    @Test
    @DisplayName("search always returns empty list")
    void searchReturnsEmpty() {
        provider.addMessage("s1", Message.user("searchable content"));
        assertTrue(provider.search("searchable").isEmpty());
    }

    @Test
    @DisplayName("returned history is a defensive copy")
    void historyIsDefensiveCopy() {
        provider.addMessage("s1", Message.user("msg"));

        List<Message> history1 = provider.getHistory("s1", 10);
        List<Message> history2 = provider.getHistory("s1", 10);

        assertNotSame(history1, history2);
    }
}
