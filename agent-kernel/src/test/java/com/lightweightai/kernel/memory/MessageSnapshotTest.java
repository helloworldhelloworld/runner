package com.lightweightai.kernel.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MessageSnapshot — 不可变消息快照")
class MessageSnapshotTest {

    @Test
    @DisplayName("of() 创建快照并保留消息内容")
    void ofCreatesSnapshotWithContent() {
        List<Message> messages = List.of(Message.user("hello"), Message.assistant("hi"));
        MessageSnapshot snapshot = MessageSnapshot.of("s1", messages);

        assertEquals("s1", snapshot.getSessionId());
        assertEquals(2, snapshot.size());
        assertEquals("hello", snapshot.getMessages().get(0).getContent());
        assertEquals("hi", snapshot.getMessages().get(1).getContent());
    }

    @Test
    @DisplayName("of() 创建防御性拷贝，源列表修改不影响快照")
    void ofCreatesDefensiveCopy() {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.user("hello"));
        MessageSnapshot snapshot = MessageSnapshot.of("s1", messages);

        messages.add(Message.assistant("world"));
        assertEquals(1, snapshot.size());
    }

    @Test
    @DisplayName("getMessages() 返回不可变列表")
    void messagesAreUnmodifiable() {
        MessageSnapshot snapshot = MessageSnapshot.of("s1", List.of(Message.user("hello")));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.getMessages().add(Message.user("x")));
    }

    @Test
    @DisplayName("empty() 创建空快照")
    void emptySnapshot() {
        MessageSnapshot snapshot = MessageSnapshot.empty("s1");

        assertEquals("s1", snapshot.getSessionId());
        assertEquals(0, snapshot.size());
        assertTrue(snapshot.isEmpty());
    }

    @Test
    @DisplayName("非空快照 isEmpty 返回 false")
    void nonEmptySnapshotIsNotEmpty() {
        MessageSnapshot snapshot = MessageSnapshot.of("s1", List.of(Message.user("hi")));
        assertFalse(snapshot.isEmpty());
    }

    @Test
    @DisplayName("capture() 从 MemoryProvider 创建快照")
    void captureFromProvider() {
        MemoryProvider provider = new InMemoryProvider();
        provider.addMessage("s1", Message.user("hello"));
        provider.addMessage("s1", Message.assistant("hi"));

        MessageSnapshot snapshot = MessageSnapshot.capture(provider, "s1", 100);

        assertEquals("s1", snapshot.getSessionId());
        assertEquals(2, snapshot.size());
        assertEquals("hello", snapshot.getMessages().get(0).getContent());
    }

    @Test
    @DisplayName("toString 包含 sessionId 和 count")
    void toStringFormat() {
        MessageSnapshot snapshot = MessageSnapshot.of("s1", List.of(Message.user("hi")));
        String str = snapshot.toString();
        assertTrue(str.contains("s1"));
        assertTrue(str.contains("1"));
    }
}
