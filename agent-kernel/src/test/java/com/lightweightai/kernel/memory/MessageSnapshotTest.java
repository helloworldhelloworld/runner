package com.lightweightai.kernel.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageSnapshot - immutable conversation history snapshot")
class MessageSnapshotTest {

    @Mock
    private MemoryProvider memoryProvider;

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
        List<Message> history = List.of(
            Message.user("hello"),
            Message.assistant("hi")
        );
        when(memoryProvider.getHistory("s1", 50)).thenReturn(history);

        MessageSnapshot snapshot = MessageSnapshot.capture(memoryProvider, "s1", 50);

        assertEquals("s1", snapshot.getSessionId());
        assertEquals(2, snapshot.size());
        verify(memoryProvider).getHistory("s1", 50);
    }

    // ==================== immutability ====================

    @Test
    @DisplayName("getMessages returns unmodifiable list")
    void getMessagesReturnsUnmodifiable() {
        MessageSnapshot snapshot = MessageSnapshot.of("s1", List.of(Message.user("test")));

        assertThrows(UnsupportedOperationException.class, () ->
            snapshot.getMessages().add(Message.user("injected")));
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
