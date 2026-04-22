package com.lightweightai.kernel.context;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SnipCompactor - 旧 TOOL 消息删除压缩器")
class SnipCompactorTest {

    private ConversationMessage msg(MessageRole role, String text) {
        return ConversationMessage.builder().role(role).textContent(text).build();
    }

    private ConversationMessage toolMsg(String text) {
        return ConversationMessage.builder().role(MessageRole.TOOL).textContent(text).build();
    }

    @Nested
    @DisplayName("轮数不足时保留所有消息")
    class PreserveWhenFewRounds {

        @Test
        @DisplayName("USER 消息数少于 keepRecentRounds 时全部保留")
        void fewerRoundsThanKeep() {
            List<ConversationMessage> messages = List.of(
                    msg(MessageRole.USER, "q1"),
                    toolMsg("tool1"),
                    msg(MessageRole.ASSISTANT, "a1"),
                    msg(MessageRole.USER, "q2"),
                    toolMsg("tool2"),
                    msg(MessageRole.ASSISTANT, "a2")
            );

            SnipCompactor snip = new SnipCompactor(5);
            List<ConversationMessage> result = snip.compact(messages);

            assertEquals(6, result.size());
            assertEquals("q1", result.get(0).getTextContent());
            assertEquals("tool1", result.get(1).getTextContent());
            assertEquals("a1", result.get(2).getTextContent());
            assertEquals("q2", result.get(3).getTextContent());
            assertEquals("tool2", result.get(4).getTextContent());
            assertEquals("a2", result.get(5).getTextContent());
        }

        @Test
        @DisplayName("USER 消息数恰好等于 keepRecentRounds 时全部保留")
        void exactlyKeepRecentRounds() {
            List<ConversationMessage> messages = List.of(
                    msg(MessageRole.USER, "q1"),
                    toolMsg("tool1"),
                    msg(MessageRole.ASSISTANT, "a1"),
                    msg(MessageRole.USER, "q2"),
                    toolMsg("tool2"),
                    msg(MessageRole.ASSISTANT, "a2"),
                    msg(MessageRole.USER, "q3"),
                    toolMsg("tool3"),
                    msg(MessageRole.ASSISTANT, "a3")
            );

            SnipCompactor snip = new SnipCompactor(3);
            List<ConversationMessage> result = snip.compact(messages);

            assertEquals(9, result.size());
            long toolCount = result.stream().filter(m -> m.getRole() == MessageRole.TOOL).count();
            assertEquals(3, toolCount, "All TOOL messages should be preserved");
        }
    }

    @Nested
    @DisplayName("旧 TOOL 消息删除")
    class RemoveOldTools {

        @Test
        @DisplayName("超过 keepRecentRounds 时删除旧的 TOOL 消息，保留近期的")
        void removesOldToolKeepsRecent() {
            List<ConversationMessage> messages = new ArrayList<>(List.of(
                    msg(MessageRole.USER, "q1"),
                    msg(MessageRole.ASSISTANT, "a1"),
                    toolMsg("old-tool-1"),
                    toolMsg("old-tool-2"),
                    msg(MessageRole.USER, "q2"),
                    msg(MessageRole.ASSISTANT, "a2"),
                    toolMsg("recent-tool"),
                    msg(MessageRole.USER, "q3"),
                    msg(MessageRole.ASSISTANT, "a3")
            ));

            SnipCompactor snip = new SnipCompactor(2);
            List<ConversationMessage> result = snip.compact(messages);

            boolean oldTool1Present = result.stream()
                    .anyMatch(m -> "old-tool-1".equals(m.getTextContent()));
            assertFalse(oldTool1Present, "old-tool-1 should be removed");

            boolean oldTool2Present = result.stream()
                    .anyMatch(m -> "old-tool-2".equals(m.getTextContent()));
            assertFalse(oldTool2Present, "old-tool-2 should be removed");

            boolean recentToolPresent = result.stream()
                    .anyMatch(m -> "recent-tool".equals(m.getTextContent()));
            assertTrue(recentToolPresent, "recent-tool should be preserved");
        }

        @Test
        @DisplayName("keepRecentRounds=1 时只保留最后一轮的 TOOL 消息")
        void keepOnlyLastRound() {
            List<ConversationMessage> messages = new ArrayList<>(List.of(
                    msg(MessageRole.USER, "q1"),
                    toolMsg("tool-r1"),
                    msg(MessageRole.ASSISTANT, "a1"),
                    msg(MessageRole.USER, "q2"),
                    toolMsg("tool-r2"),
                    msg(MessageRole.ASSISTANT, "a2"),
                    msg(MessageRole.USER, "q3"),
                    toolMsg("tool-r3"),
                    msg(MessageRole.ASSISTANT, "a3"),
                    msg(MessageRole.USER, "q4"),
                    toolMsg("tool-r4"),
                    msg(MessageRole.ASSISTANT, "a4")
            ));

            SnipCompactor snip = new SnipCompactor(1);
            List<ConversationMessage> result = snip.compact(messages);

            List<String> toolContents = result.stream()
                    .filter(m -> m.getRole() == MessageRole.TOOL)
                    .map(ConversationMessage::getTextContent)
                    .toList();
            assertEquals(1, toolContents.size(), "Only one TOOL message from last round should survive");
            assertEquals("tool-r4", toolContents.get(0), "Only the TOOL near the last USER should survive");
        }
    }

    @Nested
    @DisplayName("非 TOOL 消息始终保留")
    class NonToolAlwaysPreserved {

        @Test
        @DisplayName("USER 消息无论位置始终保留")
        void userMessagesAlwaysPreserved() {
            List<ConversationMessage> messages = new ArrayList<>(List.of(
                    msg(MessageRole.USER, "q1"),
                    toolMsg("tool1"),
                    msg(MessageRole.USER, "q2"),
                    toolMsg("tool2"),
                    msg(MessageRole.USER, "q3"),
                    toolMsg("tool3"),
                    msg(MessageRole.USER, "q4")
            ));

            SnipCompactor snip = new SnipCompactor(1);
            List<ConversationMessage> result = snip.compact(messages);

            List<String> userContents = result.stream()
                    .filter(m -> m.getRole() == MessageRole.USER)
                    .map(ConversationMessage::getTextContent)
                    .toList();
            assertEquals(List.of("q1", "q2", "q3", "q4"), userContents,
                    "All USER messages must be preserved in order");
        }

        @Test
        @DisplayName("ASSISTANT 消息无论位置始终保留")
        void assistantMessagesAlwaysPreserved() {
            List<ConversationMessage> messages = new ArrayList<>(List.of(
                    msg(MessageRole.USER, "q1"),
                    msg(MessageRole.ASSISTANT, "a1"),
                    toolMsg("tool1"),
                    msg(MessageRole.USER, "q2"),
                    msg(MessageRole.ASSISTANT, "a2"),
                    toolMsg("tool2"),
                    msg(MessageRole.USER, "q3"),
                    msg(MessageRole.ASSISTANT, "a3")
            ));

            SnipCompactor snip = new SnipCompactor(1);
            List<ConversationMessage> result = snip.compact(messages);

            List<String> assistantContents = result.stream()
                    .filter(m -> m.getRole() == MessageRole.ASSISTANT)
                    .map(ConversationMessage::getTextContent)
                    .toList();
            assertEquals(List.of("a1", "a2", "a3"), assistantContents,
                    "All ASSISTANT messages must be preserved in order");
        }

        @Test
        @DisplayName("SYSTEM 消息始终保留")
        void systemMessagesAlwaysPreserved() {
            List<ConversationMessage> messages = new ArrayList<>(List.of(
                    msg(MessageRole.SYSTEM, "system prompt"),
                    msg(MessageRole.USER, "q1"),
                    toolMsg("tool1"),
                    msg(MessageRole.USER, "q2"),
                    toolMsg("tool2"),
                    msg(MessageRole.USER, "q3")
            ));

            SnipCompactor snip = new SnipCompactor(1);
            List<ConversationMessage> result = snip.compact(messages);

            boolean systemPresent = result.stream()
                    .anyMatch(m -> m.getRole() == MessageRole.SYSTEM
                            && "system prompt".equals(m.getTextContent()));
            assertTrue(systemPresent, "SYSTEM message must always be preserved");
        }
    }

    @Nested
    @DisplayName("保护边界计算")
    class ProtectBoundary {

        @Test
        @DisplayName("保护边界正确划分：边界之前的 TOOL 删除，之后的保留")
        void protectBoundaryCorrectlyDivides() {
            List<ConversationMessage> messages = new ArrayList<>(List.of(
                    msg(MessageRole.SYSTEM, "sys"),
                    msg(MessageRole.USER, "q1"),
                    toolMsg("tool-before-boundary"),
                    msg(MessageRole.ASSISTANT, "a1"),
                    msg(MessageRole.USER, "q2"),
                    toolMsg("tool-at-boundary"),
                    msg(MessageRole.ASSISTANT, "a2"),
                    msg(MessageRole.USER, "q3"),
                    toolMsg("tool-after-boundary"),
                    msg(MessageRole.ASSISTANT, "a3")
            ));

            SnipCompactor snip = new SnipCompactor(2);
            List<ConversationMessage> result = snip.compact(messages);

            boolean beforeBoundary = result.stream()
                    .anyMatch(m -> "tool-before-boundary".equals(m.getTextContent()));
            assertFalse(beforeBoundary, "TOOL before protect boundary should be removed");

            boolean atBoundary = result.stream()
                    .anyMatch(m -> "tool-at-boundary".equals(m.getTextContent()));
            assertTrue(atBoundary, "TOOL at/after protect boundary should be preserved");

            boolean afterBoundary = result.stream()
                    .anyMatch(m -> "tool-after-boundary".equals(m.getTextContent()));
            assertTrue(afterBoundary, "TOOL after protect boundary should be preserved");
        }

        @Test
        @DisplayName("多个旧 TOOL 消息全部被删除")
        void multipleOldToolsAllRemoved() {
            List<ConversationMessage> messages = new ArrayList<>(List.of(
                    msg(MessageRole.USER, "q1"),
                    toolMsg("old-1"),
                    toolMsg("old-2"),
                    toolMsg("old-3"),
                    msg(MessageRole.ASSISTANT, "a1"),
                    msg(MessageRole.USER, "q2"),
                    msg(MessageRole.ASSISTANT, "a2"),
                    msg(MessageRole.USER, "q3"),
                    toolMsg("recent"),
                    msg(MessageRole.ASSISTANT, "a3")
            ));

            SnipCompactor snip = new SnipCompactor(2);
            List<ConversationMessage> result = snip.compact(messages);

            List<String> toolContents = result.stream()
                    .filter(m -> m.getRole() == MessageRole.TOOL)
                    .map(ConversationMessage::getTextContent)
                    .toList();
            assertEquals(List.of("recent"), toolContents,
                    "All old TOOL messages should be removed, only recent one kept");
        }

        @Test
        @DisplayName("无 TOOL 消息时列表大小不变")
        void noToolMessagesUnchanged() {
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

            assertEquals(6, result.size(), "No TOOL messages to remove, size unchanged");
            assertEquals("q1", result.get(0).getTextContent());
            assertEquals("a3", result.get(5).getTextContent());
        }
    }
}
