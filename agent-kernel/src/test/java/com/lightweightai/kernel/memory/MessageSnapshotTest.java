package com.lightweightai.kernel.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MessageSnapshot — 不可变消息快照")
class MessageSnapshotTest {

    @Test
    @DisplayName("of 创建快照，消息列表不可变")
    void ofCreatesImmutableSnapshot() {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.user("hello"));
        messages.add(Message.assistant("hi"));

        MessageSnapshot snapshot = MessageSnapshot.of("session-1", messages);

        assertEquals(2, snapshot.size());
        assertEquals("session-1", snapshot.getSessionId());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.getMessages().add(Message.user("hack")));
    }

    @Test
    @DisplayName("of 是防御性拷贝，原始列表变更不影响快照")
    void ofMakesDefensiveCopy() {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.user("hello"));

        MessageSnapshot snapshot = MessageSnapshot.of("s1", messages);
        messages.add(Message.assistant("new"));

        assertEquals(1, snapshot.size(), "Snapshot should not be affected by original list changes");
    }

    @Test
    @DisplayName("empty 创建空快照")
    void emptyCreatesEmptySnapshot() {
        MessageSnapshot snapshot = MessageSnapshot.empty("s1");

        assertTrue(snapshot.isEmpty());
        assertEquals(0, snapshot.size());
        assertEquals("s1", snapshot.getSessionId());
        assertTrue(snapshot.getMessages().isEmpty());
    }

    @Test
    @DisplayName("capture 从 MemoryProvider 创建快照")
    void captureFromProvider() {
        MemoryProvider provider = new MemoryProvider() {
            @Override
            public void addMessage(String sessionId, Message message) {}

            @Override
            public List<Message> getHistory(String sessionId, int limit) {
                return List.of(
                        Message.user("question"),
                        Message.assistant("answer")
                );
            }

            @Override
            public void clearSession(String sessionId) {}

            @Override
            public void writeEphemeral(String content) {}

            @Override
            public void writeDurable(String sessionId, String content) {}

            @Override
            public List<MemorySearchResult> search(String query) { return List.of(); }
        };

        MessageSnapshot snapshot = MessageSnapshot.capture(provider, "s1", 100);

        assertEquals(2, snapshot.size());
        assertFalse(snapshot.isEmpty());
        assertEquals("user", snapshot.getMessages().get(0).getRole());
        assertEquals("question", snapshot.getMessages().get(0).getContent());
    }

    @Test
    @DisplayName("getMessages 返回的列表内容正确")
    void getMessagesReturnsCorrectContent() {
        MessageSnapshot snapshot = MessageSnapshot.of("s1", List.of(
                Message.user("hello"),
                Message.assistant("world")
        ));

        List<Message> messages = snapshot.getMessages();
        assertEquals(2, messages.size());
        assertEquals("hello", messages.get(0).getContent());
        assertEquals("world", messages.get(1).getContent());
    }

    @Test
    @DisplayName("toString 包含 sessionId 和 count")
    void toStringIncludesSessionIdAndCount() {
        MessageSnapshot snapshot = MessageSnapshot.of("my-session", List.of(
                Message.user("a"), Message.assistant("b")
        ));

        String str = snapshot.toString();
        assertTrue(str.contains("my-session"));
        assertTrue(str.contains("2"));
    }
}
