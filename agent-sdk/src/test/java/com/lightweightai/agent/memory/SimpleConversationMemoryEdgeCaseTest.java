package com.lightweightai.agent.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SimpleConversationMemory 边界条件测试
 *
 * 覆盖：
 * - 大量消息添加
 * - clear 后状态
 * - history 不可变性
 * - 角色参数被忽略的行为
 * - 连续 clear 操作
 */
@DisplayName("SimpleConversationMemory - 边界条件")
class SimpleConversationMemoryEdgeCaseTest {

    private SimpleConversationMemory memory;

    @BeforeEach
    void setUp() {
        memory = new SimpleConversationMemory();
    }

    @Nested
    @DisplayName("大量消息")
    class LargeMessageCount {

        @Test
        @DisplayName("添加100条消息后历史正确")
        void shouldHandleHundredMessages() {
            for (int i = 0; i < 100; i++) {
                memory.addMessage(i % 2 == 0 ? "user" : "assistant", "message-" + i);
            }

            assertEquals(100, memory.getMessageCount());
            assertEquals(100, memory.getHistory().size());
            assertEquals("message-0", memory.getHistory().get(0));
            assertEquals("message-99", memory.getHistory().get(99));
        }
    }

    @Nested
    @DisplayName("Clear 行为")
    class ClearBehavior {

        @Test
        @DisplayName("clear 后消息计数为 0")
        void clearShouldResetCount() {
            memory.addMessage("user", "hello");
            memory.addMessage("assistant", "hi");

            memory.clear();

            assertEquals(0, memory.getMessageCount());
            assertTrue(memory.getHistory().isEmpty());
        }

        @Test
        @DisplayName("clear 后可以重新添加消息")
        void shouldAddMessagesAfterClear() {
            memory.addMessage("user", "old");
            memory.clear();
            memory.addMessage("user", "new");

            assertEquals(1, memory.getMessageCount());
            assertEquals("new", memory.getHistory().get(0));
        }

        @Test
        @DisplayName("连续 clear 不报错")
        void doubleClearShouldNotError() {
            memory.addMessage("user", "hello");
            memory.clear();
            assertDoesNotThrow(() -> memory.clear());
            assertEquals(0, memory.getMessageCount());
        }

        @Test
        @DisplayName("空记忆 clear 不报错")
        void clearEmptyMemoryShouldNotError() {
            assertDoesNotThrow(() -> memory.clear());
        }
    }

    @Nested
    @DisplayName("History 不可变性")
    class HistoryImmutability {

        @Test
        @DisplayName("修改返回的 history 列表不影响内部状态")
        void modifyingReturnedHistoryShouldNotAffectInternal() {
            memory.addMessage("user", "message");

            List<String> history = memory.getHistory();
            assertThrows(UnsupportedOperationException.class, () -> history.add("hacked"));
            assertThrows(UnsupportedOperationException.class, () -> history.clear());

            assertEquals(1, memory.getMessageCount());
        }
    }

    @Nested
    @DisplayName("角色处理")
    class RoleHandling {

        @Test
        @DisplayName("不同角色的消息按顺序存储内容")
        void differentRolesStoredInOrder() {
            memory.addMessage("user", "question");
            memory.addMessage("assistant", "answer");
            memory.addMessage("system", "system message");

            List<String> history = memory.getHistory();
            assertEquals(3, history.size());
            assertEquals("question", history.get(0));
            assertEquals("answer", history.get(1));
            assertEquals("system message", history.get(2));
        }
    }

    @Nested
    @DisplayName("空/特殊内容")
    class SpecialContent {

        @Test
        @DisplayName("添加空字符串消息")
        void shouldHandleEmptyStringContent() {
            memory.addMessage("user", "");
            assertEquals(1, memory.getMessageCount());
            assertEquals("", memory.getHistory().get(0));
        }

        @Test
        @DisplayName("添加包含换行的消息")
        void shouldHandleNewlineContent() {
            memory.addMessage("user", "line1\nline2\nline3");
            assertEquals(1, memory.getMessageCount());
            assertEquals("line1\nline2\nline3", memory.getHistory().get(0));
        }

        @Test
        @DisplayName("添加 Unicode 内容")
        void shouldHandleUnicodeContent() {
            memory.addMessage("user", "你好世界 🌍");
            assertEquals(1, memory.getMessageCount());
            assertEquals("你好世界 🌍", memory.getHistory().get(0));
        }
    }
}
