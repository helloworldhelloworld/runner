package com.lightweightai.kernel.context;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SnipCompactor - 旧 TOOL 消息删除")
class SnipCompactorTest {

    private ConversationMessage msg(MessageRole role, String text) {
        return ConversationMessage.builder().role(role).textContent(text).build();
    }

    private ConversationMessage toolMsg(String text) {
        return msg(MessageRole.TOOL, text);
    }

    @Nested
    @DisplayName("保护边界计算")
    class ProtectBoundaryTests {

        @Test
        @DisplayName("保留最近 N 轮的 TOOL 消息，删除更早的")
        void keepsRecentRoundsToolMessages() {
            List<ConversationMessage> messages = List.of(
                    msg(MessageRole.USER, "q1"),
                    toolMsg("old-tool-1"),          // round 1 - should be removed
                    msg(MessageRole.ASSISTANT, "a1"),
                    msg(MessageRole.USER, "q2"),
                    toolMsg("old-tool-2"),          // round 2 - should be removed
                    msg(MessageRole.ASSISTANT, "a2"),
                    msg(MessageRole.USER, "q3"),
                    toolMsg("recent-tool"),         // round 3 - should keep (within last 1 round)
                    msg(MessageRole.ASSISTANT, "a3")
            );

            SnipCompactor snip = new SnipCompactor(1);
            List<ConversationMessage> result = snip.compact(messages);

            List<String> toolContents = result.stream()
                    .filter(m -> m.getRole() == MessageRole.TOOL)
                    .map(ConversationMessage::getTextContent)
                    .toList();

            assertEquals(1, toolContents.size());
            assertEquals("recent-tool", toolContents.get(0));
        }

        @Test
        @DisplayName("keepRecentRounds=2 保留最近两轮的 TOOL")
        void keepsTwoRecentRounds() {
            List<ConversationMessage> messages = List.of(
                    msg(MessageRole.USER, "q1"),
                    toolMsg("old-tool"),
                    msg(MessageRole.ASSISTANT, "a1"),
                    msg(MessageRole.USER, "q2"),
                    toolMsg("mid-tool"),
                    msg(MessageRole.ASSISTANT, "a2"),
                    msg(MessageRole.USER, "q3"),
                    toolMsg("recent-tool"),
                    msg(MessageRole.ASSISTANT, "a3")
            );

            SnipCompactor snip = new SnipCompactor(2);
            List<ConversationMessage> result = snip.compact(messages);

            List<String> toolContents = result.stream()
                    .filter(m -> m.getRole() == MessageRole.TOOL)
                    .map(ConversationMessage::getTextContent)
                    .toList();

            assertEquals(2, toolContents.size());
            assertTrue(toolContents.contains("mid-tool"));
            assertTrue(toolContents.contains("recent-tool"));
            assertFalse(toolContents.contains("old-tool"));
        }
    }

    @Nested
    @DisplayName("边界情况")
    class EdgeCases {

        @Test
        @DisplayName("不足 N 轮时原样返回所有消息")
        void noCompactionWhenFewRounds() {
            List<ConversationMessage> messages = List.of(
                    msg(MessageRole.USER, "q1"),
                    toolMsg("tool-1"),
                    msg(MessageRole.ASSISTANT, "a1")
            );

            SnipCompactor snip = new SnipCompactor(5);
            List<ConversationMessage> result = snip.compact(messages);

            assertEquals(messages.size(), result.size());
            assertSame(messages, result, "Should return same list reference when no compaction needed");
        }

        @Test
        @DisplayName("恰好 N 轮时不删除")
        void noCompactionWhenExactlyNRounds() {
            List<ConversationMessage> messages = List.of(
                    msg(MessageRole.USER, "q1"),
                    toolMsg("tool-1"),
                    msg(MessageRole.ASSISTANT, "a1"),
                    msg(MessageRole.USER, "q2"),
                    toolMsg("tool-2"),
                    msg(MessageRole.ASSISTANT, "a2")
            );

            SnipCompactor snip = new SnipCompactor(2);
            List<ConversationMessage> result = snip.compact(messages);

            assertEquals(messages.size(), result.size());
        }

        @Test
        @DisplayName("没有 TOOL 消息时，所有消息都保留")
        void noToolMessagesKepsAll() {
            List<ConversationMessage> messages = List.of(
                    msg(MessageRole.USER, "q1"),
                    msg(MessageRole.ASSISTANT, "a1"),
                    msg(MessageRole.USER, "q2"),
                    msg(MessageRole.ASSISTANT, "a2"),
                    msg(MessageRole.USER, "q3"),
                    msg(MessageRole.ASSISTANT, "a3")
            );

            SnipCompactor snip = new SnipCompactor(1);
            List<ConversationMessage> result = snip.compact(messages);

            assertEquals(messages.size(), result.size());
        }

        @Test
        @DisplayName("SYSTEM 消息永远保留")
        void systemMessagesAlwaysKept() {
            List<ConversationMessage> messages = List.of(
                    msg(MessageRole.SYSTEM, "you are an assistant"),
                    msg(MessageRole.USER, "q1"),
                    toolMsg("old-tool"),
                    msg(MessageRole.ASSISTANT, "a1"),
                    msg(MessageRole.USER, "q2"),
                    msg(MessageRole.ASSISTANT, "a2"),
                    msg(MessageRole.USER, "q3"),
                    msg(MessageRole.ASSISTANT, "a3")
            );

            SnipCompactor snip = new SnipCompactor(1);
            List<ConversationMessage> result = snip.compact(messages);

            assertTrue(result.stream().anyMatch(m -> m.getRole() == MessageRole.SYSTEM),
                    "SYSTEM message must be preserved");
        }

        @Test
        @DisplayName("USER 和 ASSISTANT 消息永远保留，即使在旧轮次中")
        void userAndAssistantAlwaysPreserved() {
            List<ConversationMessage> messages = List.of(
                    msg(MessageRole.USER, "q1"),
                    msg(MessageRole.ASSISTANT, "a1"),
                    msg(MessageRole.USER, "q2"),
                    msg(MessageRole.ASSISTANT, "a2"),
                    msg(MessageRole.USER, "q3"),
                    msg(MessageRole.ASSISTANT, "a3"),
                    msg(MessageRole.USER, "q4"),
                    msg(MessageRole.ASSISTANT, "a4")
            );

            SnipCompactor snip = new SnipCompactor(1);
            List<ConversationMessage> result = snip.compact(messages);

            long userCount = result.stream().filter(m -> m.getRole() == MessageRole.USER).count();
            long assistantCount = result.stream().filter(m -> m.getRole() == MessageRole.ASSISTANT).count();
            assertEquals(4, userCount);
            assertEquals(4, assistantCount);
        }
    }

    @Nested
    @DisplayName("多 TOOL 消息在同一轮")
    class MultipleToolPerRound {

        @Test
        @DisplayName("同一保护区内的多个 TOOL 消息全部保留")
        void keepsAllToolsInProtectedRound() {
            List<ConversationMessage> messages = List.of(
                    msg(MessageRole.USER, "q1"),
                    toolMsg("old1"),
                    toolMsg("old2"),
                    msg(MessageRole.ASSISTANT, "a1"),
                    msg(MessageRole.USER, "q2"),
                    toolMsg("new1"),
                    toolMsg("new2"),
                    toolMsg("new3"),
                    msg(MessageRole.ASSISTANT, "a2")
            );

            SnipCompactor snip = new SnipCompactor(1);
            List<ConversationMessage> result = snip.compact(messages);

            List<String> toolContents = result.stream()
                    .filter(m -> m.getRole() == MessageRole.TOOL)
                    .map(ConversationMessage::getTextContent)
                    .toList();

            assertEquals(3, toolContents.size());
            assertTrue(toolContents.contains("new1"));
            assertTrue(toolContents.contains("new2"));
            assertTrue(toolContents.contains("new3"));
        }
    }
}
