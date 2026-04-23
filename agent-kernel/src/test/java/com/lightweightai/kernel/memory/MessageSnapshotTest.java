package com.lightweightai.kernel.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MessageSnapshot - 不可变消息快照")
class MessageSnapshotTest {

    @Test
    @DisplayName("capture 从 MemoryProvider 拍摄快照")
    void captureFromProvider() {
        InMemoryProvider provider = new InMemoryProvider();
        provider.addMessage("s1", Message.user("hello"));
        provider.addMessage("s1", Message.assistant("hi"));

        MessageSnapshot snap = MessageSnapshot.capture(provider, "s1", 10);

        assertEquals("s1", snap.getSessionId());
        assertEquals(2, snap.size());
        assertFalse(snap.isEmpty());
        assertEquals("hello", snap.getMessages().get(0).getContent());
        assertEquals("hi", snap.getMessages().get(1).getContent());
    }

    @Test
    @DisplayName("capture 后修改 provider 不影响快照")
    void captureIsIsolatedFromProvider() {
        InMemoryProvider provider = new InMemoryProvider();
        provider.addMessage("s1", Message.user("hello"));

        MessageSnapshot snap = MessageSnapshot.capture(provider, "s1", 10);
        assertEquals(1, snap.size());

        provider.addMessage("s1", Message.assistant("world"));
        assertEquals(1, snap.size());
    }

    @Test
    @DisplayName("of() 创建防御性拷贝")
    void ofCreatesDefensiveCopy() {
        List<Message> original = new ArrayList<>();
        original.add(Message.user("a"));

        MessageSnapshot snap = MessageSnapshot.of("s1", original);
        assertEquals(1, snap.size());

        original.add(Message.user("b"));
        assertEquals(1, snap.size());
    }

    @Test
    @DisplayName("getMessages 返回不可变列表")
    void getMessagesReturnsImmutableList() {
        MessageSnapshot snap = MessageSnapshot.of("s1", List.of(Message.user("a")));
        assertThrows(UnsupportedOperationException.class,
                () -> snap.getMessages().add(Message.user("b")));
    }

    @Test
    @DisplayName("empty 创建空快照")
    void emptySnapshot() {
        MessageSnapshot snap = MessageSnapshot.empty("s1");
        assertTrue(snap.isEmpty());
        assertEquals(0, snap.size());
        assertEquals("s1", snap.getSessionId());
    }

    @Test
    @DisplayName("toString 包含 sessionId 和 count")
    void toStringFormat() {
        MessageSnapshot snap = MessageSnapshot.of("sess-42",
                List.of(Message.user("a"), Message.assistant("b")));
        String str = snap.toString();
        assertTrue(str.contains("sess-42"));
        assertTrue(str.contains("2"));
    }
}
