package com.lightweightai.kernel.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MessageSnapshot - 不可变消息快照")
class MessageSnapshotTest {

    @Nested
    @DisplayName("创建与基本属性")
    class CreationTests {

        @Test
        @DisplayName("of() 创建包含消息的快照")
        void shouldCreateFromMessageList() {
            List<Message> messages = List.of(
                    Message.user("hello"),
                    Message.assistant("hi there")
            );

            MessageSnapshot snapshot = MessageSnapshot.of("sess-1", messages);

            assertEquals("sess-1", snapshot.getSessionId());
            assertEquals(2, snapshot.size());
            assertFalse(snapshot.isEmpty());
            assertEquals("hello", snapshot.getMessages().get(0).getContent());
            assertEquals("hi there", snapshot.getMessages().get(1).getContent());
        }

        @Test
        @DisplayName("empty() 创建空快照")
        void shouldCreateEmptySnapshot() {
            MessageSnapshot snapshot = MessageSnapshot.empty("sess-2");

            assertEquals("sess-2", snapshot.getSessionId());
            assertEquals(0, snapshot.size());
            assertTrue(snapshot.isEmpty());
            assertTrue(snapshot.getMessages().isEmpty());
        }

        @Test
        @DisplayName("toString 包含 sessionId 和 count")
        void toStringShouldContainIdAndCount() {
            MessageSnapshot snapshot = MessageSnapshot.of("s1", List.of(Message.user("hi")));

            String str = snapshot.toString();
            assertTrue(str.contains("s1"));
            assertTrue(str.contains("1"));
        }
    }

    @Nested
    @DisplayName("不可变性保证")
    class ImmutabilityTests {

        @Test
        @DisplayName("修改原始列表不影响快照")
        void sourceListModificationShouldNotAffectSnapshot() {
            List<Message> mutable = new ArrayList<>();
            mutable.add(Message.user("original"));

            MessageSnapshot snapshot = MessageSnapshot.of("sess", mutable);

            mutable.add(Message.assistant("added later"));
            mutable.clear();

            assertEquals(1, snapshot.size());
            assertEquals("original", snapshot.getMessages().get(0).getContent());
        }

        @Test
        @DisplayName("getMessages 返回不可变列表")
        void getMessagesShouldReturnUnmodifiableList() {
            MessageSnapshot snapshot = MessageSnapshot.of("sess",
                    List.of(Message.user("test")));

            assertThrows(UnsupportedOperationException.class,
                    () -> snapshot.getMessages().add(Message.user("injected")));
        }

        @Test
        @DisplayName("getMessages 返回不可变列表 - clear 也应拒绝")
        void getMessagesShouldRejectClear() {
            MessageSnapshot snapshot = MessageSnapshot.of("sess",
                    List.of(Message.user("a"), Message.assistant("b")));

            assertThrows(UnsupportedOperationException.class,
                    () -> snapshot.getMessages().clear());

            assertEquals(2, snapshot.size());
        }
    }

    @Nested
    @DisplayName("capture 从 MemoryProvider 获取")
    class CaptureTests {

        @Test
        @DisplayName("capture 从 provider 获取历史并冻结")
        void shouldCaptureFromProvider() {
            InMemoryProvider provider = new InMemoryProvider();
            provider.addMessage("s1", Message.user("msg1"));
            provider.addMessage("s1", Message.assistant("reply1"));
            provider.addMessage("s1", Message.user("msg2"));

            MessageSnapshot snapshot = MessageSnapshot.capture(provider, "s1", 10);

            assertEquals(3, snapshot.size());
            assertEquals("msg1", snapshot.getMessages().get(0).getContent());

            // 之后添加的消息不影响已捕获的快照
            provider.addMessage("s1", Message.assistant("reply2"));
            assertEquals(3, snapshot.size());
        }

        @Test
        @DisplayName("capture 受 limit 限制")
        void captureShouldRespectLimit() {
            InMemoryProvider provider = new InMemoryProvider();
            for (int i = 0; i < 10; i++) {
                provider.addMessage("s1", Message.user("msg" + i));
            }

            MessageSnapshot snapshot = MessageSnapshot.capture(provider, "s1", 3);

            assertEquals(3, snapshot.size());
        }

        @Test
        @DisplayName("capture 空 session 返回空快照")
        void captureEmptySessionShouldReturnEmptySnapshot() {
            InMemoryProvider provider = new InMemoryProvider();

            MessageSnapshot snapshot = MessageSnapshot.capture(provider, "nonexistent", 10);

            assertEquals(0, snapshot.size());
            assertTrue(snapshot.isEmpty());
        }
    }
}
