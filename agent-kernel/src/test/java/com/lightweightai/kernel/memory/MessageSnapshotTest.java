package com.lightweightai.kernel.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MessageSnapshot - 不可变消息快照")
class MessageSnapshotTest {

    @Test
    @DisplayName("of() 创建防御性副本")
    void ofCreatesDefensiveCopy() {
        List<Message> original = new ArrayList<>();
        original.add(Message.user("hello"));
        original.add(Message.assistant("hi"));

        MessageSnapshot snapshot = MessageSnapshot.of("session-1", original);

        original.add(Message.user("more"));
        assertEquals(2, snapshot.size(), "Snapshot should not reflect mutations to original list");
    }

    @Test
    @DisplayName("getMessages() 返回不可变列表")
    void messagesAreUnmodifiable() {
        MessageSnapshot snapshot = MessageSnapshot.of("s1", List.of(Message.user("hello")));

        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.getMessages().add(Message.user("hack")));
    }

    @Test
    @DisplayName("empty() 创建空快照")
    void emptySnapshotHasNoMessages() {
        MessageSnapshot snapshot = MessageSnapshot.empty("s1");

        assertTrue(snapshot.isEmpty());
        assertEquals(0, snapshot.size());
        assertEquals("s1", snapshot.getSessionId());
    }

    @Test
    @DisplayName("capture() 从 MemoryProvider 创建快照")
    void captureFromProvider() {
        MemoryProvider provider = new InMemoryProvider();
        provider.addMessage("s1", Message.user("question"));
        provider.addMessage("s1", Message.assistant("answer"));

        MessageSnapshot snapshot = MessageSnapshot.capture(provider, "s1", 100);

        assertEquals(2, snapshot.size());
        assertEquals("s1", snapshot.getSessionId());
    }

    @Test
    @DisplayName("capture() 后 provider 变更不影响快照")
    void captureIsIsolatedFromProviderChanges() {
        MemoryProvider provider = new InMemoryProvider();
        provider.addMessage("s1", Message.user("q1"));

        MessageSnapshot snapshot = MessageSnapshot.capture(provider, "s1", 100);
        provider.addMessage("s1", Message.assistant("a1"));

        assertEquals(1, snapshot.size(), "Snapshot should not reflect later additions");
    }

    @Test
    @DisplayName("size() 返回消息数量")
    void sizeReturnsCount() {
        MessageSnapshot snapshot = MessageSnapshot.of("s1",
                List.of(Message.user("a"), Message.assistant("b"), Message.user("c")));

        assertEquals(3, snapshot.size());
        assertFalse(snapshot.isEmpty());
    }

    @Test
    @DisplayName("toString() 包含 sessionId 和 count")
    void toStringContainsKeyInfo() {
        MessageSnapshot snapshot = MessageSnapshot.of("test-session", List.of(Message.user("hi")));

        String str = snapshot.toString();
        assertTrue(str.contains("test-session"));
        assertTrue(str.contains("1"));
    }
}
