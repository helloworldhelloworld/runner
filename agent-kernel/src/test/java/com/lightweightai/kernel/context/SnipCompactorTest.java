package com.lightweightai.kernel.context;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SnipCompactorTest {

    private static ConversationMessage msg(MessageRole role, String text) {
        return ConversationMessage.builder().role(role).textContent(text).build();
    }

    @Test
    void fewerRoundsThanThresholdReturnsOriginal() {
        SnipCompactor sc = new SnipCompactor(3);
        List<ConversationMessage> input = List.of(
                msg(MessageRole.USER, "q1"),
                msg(MessageRole.TOOL, "t1"),
                msg(MessageRole.ASSISTANT, "a1"),
                msg(MessageRole.USER, "q2"),
                msg(MessageRole.TOOL, "t2"),
                msg(MessageRole.ASSISTANT, "a2")
        );
        assertSame(input, sc.compact(input));
    }

    @Test
    void exactlyAtThresholdReturnsOriginal() {
        SnipCompactor sc = new SnipCompactor(2);
        List<ConversationMessage> input = List.of(
                msg(MessageRole.USER, "q1"),
                msg(MessageRole.TOOL, "t1"),
                msg(MessageRole.USER, "q2"),
                msg(MessageRole.TOOL, "t2")
        );
        assertSame(input, sc.compact(input));
    }

    @Test
    void oldToolMessagesRemoved() {
        SnipCompactor sc = new SnipCompactor(1);
        List<ConversationMessage> input = List.of(
                msg(MessageRole.USER, "q1"),
                msg(MessageRole.TOOL, "old-tool"),
                msg(MessageRole.ASSISTANT, "a1"),
                msg(MessageRole.USER, "q2"),
                msg(MessageRole.TOOL, "new-tool"),
                msg(MessageRole.ASSISTANT, "a2")
        );

        List<ConversationMessage> result = sc.compact(input);
        assertEquals(5, result.size());
        assertFalse(result.stream().anyMatch(m -> "old-tool".equals(m.getTextContent())));
        assertTrue(result.stream().anyMatch(m -> "new-tool".equals(m.getTextContent())));
    }

    @Test
    void nonToolMessagesAlwaysPreserved() {
        SnipCompactor sc = new SnipCompactor(1);
        List<ConversationMessage> input = List.of(
                msg(MessageRole.SYSTEM, "sys"),
                msg(MessageRole.USER, "q1"),
                msg(MessageRole.ASSISTANT, "a1"),
                msg(MessageRole.USER, "q2"),
                msg(MessageRole.ASSISTANT, "a2")
        );

        List<ConversationMessage> result = sc.compact(input);
        assertEquals(5, result.size());
    }

    @Test
    void multipleOldToolsRemoved() {
        SnipCompactor sc = new SnipCompactor(1);
        List<ConversationMessage> input = List.of(
                msg(MessageRole.USER, "q1"),
                msg(MessageRole.TOOL, "tool-1a"),
                msg(MessageRole.TOOL, "tool-1b"),
                msg(MessageRole.ASSISTANT, "a1"),
                msg(MessageRole.USER, "q2"),
                msg(MessageRole.TOOL, "tool-2"),
                msg(MessageRole.ASSISTANT, "a2"),
                msg(MessageRole.USER, "q3"),
                msg(MessageRole.TOOL, "tool-3"),
                msg(MessageRole.ASSISTANT, "a3")
        );

        List<ConversationMessage> result = sc.compact(input);

        List<String> toolTexts = result.stream()
                .filter(m -> m.getRole() == MessageRole.TOOL)
                .map(ConversationMessage::getTextContent)
                .toList();

        assertFalse(toolTexts.contains("tool-1a"));
        assertFalse(toolTexts.contains("tool-1b"));
        assertFalse(toolTexts.contains("tool-2"));
        assertTrue(toolTexts.contains("tool-3"));
    }

    @Test
    void keepRecentRounds2PreservesLast2() {
        SnipCompactor sc = new SnipCompactor(2);
        List<ConversationMessage> input = List.of(
                msg(MessageRole.USER, "q1"),
                msg(MessageRole.TOOL, "tool-old"),
                msg(MessageRole.ASSISTANT, "a1"),
                msg(MessageRole.USER, "q2"),
                msg(MessageRole.TOOL, "tool-keep-1"),
                msg(MessageRole.ASSISTANT, "a2"),
                msg(MessageRole.USER, "q3"),
                msg(MessageRole.TOOL, "tool-keep-2"),
                msg(MessageRole.ASSISTANT, "a3")
        );

        List<ConversationMessage> result = sc.compact(input);

        List<String> toolTexts = result.stream()
                .filter(m -> m.getRole() == MessageRole.TOOL)
                .map(ConversationMessage::getTextContent)
                .toList();

        assertEquals(2, toolTexts.size());
        assertTrue(toolTexts.contains("tool-keep-1"));
        assertTrue(toolTexts.contains("tool-keep-2"));
    }

    @Test
    void emptyMessageList() {
        SnipCompactor sc = new SnipCompactor(3);
        List<ConversationMessage> result = sc.compact(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void noToolMessagesNoChange() {
        SnipCompactor sc = new SnipCompactor(1);
        List<ConversationMessage> input = List.of(
                msg(MessageRole.USER, "q1"),
                msg(MessageRole.ASSISTANT, "a1"),
                msg(MessageRole.USER, "q2"),
                msg(MessageRole.ASSISTANT, "a2"),
                msg(MessageRole.USER, "q3"),
                msg(MessageRole.ASSISTANT, "a3")
        );

        List<ConversationMessage> result = sc.compact(input);
        assertEquals(6, result.size());
    }

    @Test
    void toolAtBeginningDeleted() {
        SnipCompactor sc = new SnipCompactor(1);
        List<ConversationMessage> input = List.of(
                msg(MessageRole.TOOL, "orphan-tool"),
                msg(MessageRole.USER, "q1"),
                msg(MessageRole.ASSISTANT, "a1"),
                msg(MessageRole.USER, "q2"),
                msg(MessageRole.TOOL, "recent-tool"),
                msg(MessageRole.ASSISTANT, "a2")
        );

        List<ConversationMessage> result = sc.compact(input);
        assertFalse(result.stream().anyMatch(m -> "orphan-tool".equals(m.getTextContent())));
        assertTrue(result.stream().anyMatch(m -> "recent-tool".equals(m.getTextContent())));
    }
}
