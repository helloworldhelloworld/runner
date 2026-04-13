package com.lightweightai.kernel.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MessageSnapshot - immutable conversation history snapshot")
class MessageSnapshotTest {

    // ==================== factory methods ====================

    @Test
    @DisplayName("of creates snapshot with defensive copy")
    void ofCreatesDefensiveCopy() {
        List<Message> original = new ArrayList<>();
        original.add(Message.user("hello"));

        MessageSnapshot snapshot = MessageSnapshot.of("s1", original);

        assertEquals("s1", snapshot.getSessionId());
        assertEquals(1, snapshot.size());
        assertFalse(snapshot.isEmpty());

        // Mutating original should not affect snapshot
        original.add(Message.user("second"));
        assertEquals(1, snapshot.size());
    }

    @Test
    @DisplayName("empty creates empty snapshot")
    void emptyCreatesEmptySnapshot() {
        MessageSnapshot snapshot = MessageSnapshot.empty("s1");

        assertEquals("s1", snapshot.getSessionId());
        assertEquals(0, snapshot.size());
        assertTrue(snapshot.isEmpty());
        assertTrue(snapshot.getMessages().isEmpty());
    }

    @Test
    @DisplayName("capture creates snapshot from MemoryProvider")
    void captureFromMemoryProvider() {
        // Use InMemoryProvider which is available in agent-kernel
        InMemoryProvider provider = new InMemoryProvider();
        provider.addMessage("s1", Message.user("hello"));
        provider.addMessage("s1", Message.assistant("hi"));

        MessageSnapshot snapshot = MessageSnapshot.capture(provider, "s1", 50);

        assertEquals("s1", snapshot.getSessionId());
        assertEquals(2, snapshot.size());
    }

    // ==================== immutability ====================

    @Test
    @DisplayName("getMessages returns unmodifiable list")
    void getMessagesReturnsUnmodifiable() {
        MessageSnapshot snapshot = MessageSnapshot.of("s1", List.of(Message.user("test")));

        assertThrows(UnsupportedOperationException.class, () ->
            snapshot.getMessages().add(Message.user("injected")));
    }

    // ==================== multiple messages ====================

    @Test
    @DisplayName("of preserves message order")
    void ofPreservesOrder() {
        List<Message> messages = List.of(
            Message.user("first"),
            Message.assistant("second"),
            Message.user("third")
        );

        MessageSnapshot snapshot = MessageSnapshot.of("s1", messages);

        assertEquals(3, snapshot.size());
        assertEquals("first", snapshot.getMessages().get(0).getContent());
        assertEquals("third", snapshot.getMessages().get(2).getContent());
    }

    // ==================== toString ====================

    @Test
    @DisplayName("toString includes sessionId and count")
    void toStringIncludesInfo() {
        MessageSnapshot snapshot = MessageSnapshot.of("s1", List.of(Message.user("a")));
        String str = snapshot.toString();
        assertTrue(str.contains("s1"));
        assertTrue(str.contains("1"));
    }
}
