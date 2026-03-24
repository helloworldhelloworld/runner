package com.lightweightai.kernel.memory;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConversationMemory - 会话记忆管理")
class ConversationMemoryTest {

    private ConversationMemory memory;

    @BeforeEach
    void setUp() {
        memory = new ConversationMemory();
    }

    private ConversationMessage msg(MessageRole role, String text) {
        return ConversationMessage.builder()
            .role(role)
            .textContent(text)
            .build();
    }

    @Nested
    @DisplayName("消息添加与检索")
    class AddAndRetrieve {

        @Test
        @DisplayName("添加消息并检索历史")
        void addAndGetHistory() {
            memory.addMessage("s1", msg(MessageRole.USER, "hello"));
            memory.addMessage("s1", msg(MessageRole.ASSISTANT, "hi"));

            List<ConversationMessage> history = memory.getHistory("s1");
            assertEquals(2, history.size());
            assertEquals("hello", history.get(0).getTextContent());
            assertEquals("hi", history.get(1).getTextContent());
        }

        @Test
        @DisplayName("不同 session 互相独立")
        void sessionIsolation() {
            memory.addMessage("s1", msg(MessageRole.USER, "a"));
            memory.addMessage("s2", msg(MessageRole.USER, "b"));

            assertEquals(1, memory.getHistory("s1").size());
            assertEquals(1, memory.getHistory("s2").size());
            assertEquals("a", memory.getHistory("s1").get(0).getTextContent());
            assertEquals("b", memory.getHistory("s2").get(0).getTextContent());
        }

        @Test
        @DisplayName("空 session 返回空列表")
        void emptySession() {
            List<ConversationMessage> history = memory.getHistory("nonexistent");
            assertNotNull(history);
            assertTrue(history.isEmpty());
        }
    }

    @Nested
    @DisplayName("历史裁剪")
    class HistoryTrimming {

        @Test
        @DisplayName("超出 maxHistorySize 时裁剪旧消息")
        void trimWhenExceedingMax() {
            ConversationMemory small = new ConversationMemory(5);

            for (int i = 0; i < 7; i++) {
                small.addMessage("s1", msg(MessageRole.USER, "msg-" + i));
            }

            List<ConversationMessage> history = small.getHistory("s1");
            assertEquals(5, history.size());
            // Should keep the most recent messages
            assertEquals("msg-6", history.get(history.size() - 1).getTextContent());
        }

        @Test
        @DisplayName("系统消息在裁剪时保留")
        void systemMessagesPreservedDuringTrimming() {
            ConversationMemory small = new ConversationMemory(5);

            small.addMessage("s1", msg(MessageRole.SYSTEM, "system-prompt"));
            for (int i = 0; i < 6; i++) {
                small.addMessage("s1", msg(MessageRole.USER, "msg-" + i));
            }

            List<ConversationMessage> history = small.getHistory("s1");
            // System message should be preserved
            boolean hasSystem = history.stream()
                .anyMatch(m -> m.getRole() == MessageRole.SYSTEM);
            assertTrue(hasSystem);
            assertEquals(5, history.size());
        }
    }

    @Nested
    @DisplayName("getRecentMessages")
    class RecentMessages {

        @Test
        @DisplayName("返回最近 N 条消息")
        void returnsRecentN() {
            for (int i = 0; i < 10; i++) {
                memory.addMessage("s1", msg(MessageRole.USER, "msg-" + i));
            }

            List<ConversationMessage> recent = memory.getRecentMessages("s1", 3);
            assertEquals(3, recent.size());
            assertEquals("msg-7", recent.get(0).getTextContent());
            assertEquals("msg-8", recent.get(1).getTextContent());
            assertEquals("msg-9", recent.get(2).getTextContent());
        }

        @Test
        @DisplayName("请求数量超过实际消息数量时返回全部")
        void requestMoreThanAvailable() {
            memory.addMessage("s1", msg(MessageRole.USER, "only-one"));

            List<ConversationMessage> recent = memory.getRecentMessages("s1", 100);
            assertEquals(1, recent.size());
        }

        @Test
        @DisplayName("空 session 返回空列表")
        void emptySessionReturnsEmpty() {
            List<ConversationMessage> recent = memory.getRecentMessages("none", 5);
            assertTrue(recent.isEmpty());
        }
    }

    @Nested
    @DisplayName("用户长期记忆")
    class UserMemoryTests {

        @Test
        @DisplayName("getUserMemory 创建新的 UserMemory")
        void getUserMemoryCreatesNew() {
            UserMemory um = memory.getUserMemory("s1");
            assertNotNull(um);
        }

        @Test
        @DisplayName("getUserMemory 返回同一实例")
        void getUserMemoryReturnsSameInstance() {
            UserMemory um1 = memory.getUserMemory("s1");
            UserMemory um2 = memory.getUserMemory("s1");
            assertSame(um1, um2);
        }

        @Test
        @DisplayName("updateUserMemory 存储键值对")
        void updateStoresValues() {
            memory.updateUserMemory("s1", "userName", "Alice");
            assertEquals("Alice", memory.getUserMemory("s1").get("userName"));
        }
    }

    @Nested
    @DisplayName("清除操作")
    class ClearOperations {

        @Test
        @DisplayName("clearHistory 移除指定 session")
        void clearHistoryRemovesSession() {
            memory.addMessage("s1", msg(MessageRole.USER, "hello"));
            memory.clearHistory("s1");
            assertTrue(memory.getHistory("s1").isEmpty());
        }

        @Test
        @DisplayName("clearHistory 不影响其他 session")
        void clearHistoryDoesNotAffectOthers() {
            memory.addMessage("s1", msg(MessageRole.USER, "a"));
            memory.addMessage("s2", msg(MessageRole.USER, "b"));
            memory.clearHistory("s1");

            assertTrue(memory.getHistory("s1").isEmpty());
            assertEquals(1, memory.getHistory("s2").size());
        }

        @Test
        @DisplayName("clearAll 移除所有数据")
        void clearAllRemovesEverything() {
            memory.addMessage("s1", msg(MessageRole.USER, "hello"));
            memory.updateUserMemory("s1", "key", "val");
            memory.clearAll();

            assertTrue(memory.getHistory("s1").isEmpty());
            // After clearAll, getUserMemory creates a fresh one
            assertNull(memory.getUserMemory("s1").get("key"));
        }
    }

    @Nested
    @DisplayName("getSummary")
    class SummaryTests {

        @Test
        @DisplayName("无数据返回新用户")
        void noDataReturnsNewUser() {
            assertEquals("新用户", memory.getSummary("s1"));
        }

        @Test
        @DisplayName("有用户数据返回摘要")
        void withUserDataReturnsSummary() {
            memory.updateUserMemory("s1", "userName", "Alice");
            memory.updateUserMemory("s1", "concerns", "sleep");
            memory.updateUserMemory("s1", "emotionalState", "calm");

            String summary = memory.getSummary("s1");
            assertTrue(summary.contains("Alice"));
            assertTrue(summary.contains("sleep"));
            assertTrue(summary.contains("calm"));
        }

        @Test
        @DisplayName("仅有无关键不产生摘要时返回新用户")
        void unknownKeysReturnNewUser() {
            memory.updateUserMemory("s1", "randomKey", "randomValue");
            assertEquals("新用户", memory.getSummary("s1"));
        }
    }
}
