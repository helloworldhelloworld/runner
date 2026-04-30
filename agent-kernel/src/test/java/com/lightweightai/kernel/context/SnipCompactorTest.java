package com.lightweightai.kernel.context;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SnipCompactor — 旧 TOOL 消息删除（边界用例）")
class SnipCompactorTest {

    private ConversationMessage msg(MessageRole role, String text) {
        return ConversationMessage.builder().role(role).textContent(text).build();
    }

    private ConversationMessage toolMsg(String text) {
        return msg(MessageRole.TOOL, text);
    }

    @Test
    @DisplayName("空消息列表不报错")
    void emptyMessagesReturnEmpty() {
        SnipCompactor snip = new SnipCompactor(3);
        List<ConversationMessage> result = snip.compact(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("只有 SYSTEM 消息时不删除")
    void systemOnlyMessagePreserved() {
        List<ConversationMessage> messages = List.of(msg(MessageRole.SYSTEM, "你是助手"));
        SnipCompactor snip = new SnipCompactor(1);
        List<ConversationMessage> result = snip.compact(messages);
        assertEquals(1, result.size());
        assertEquals(MessageRole.SYSTEM, result.get(0).getRole());
    }

    @Test
    @DisplayName("刚好 N 轮时不删除任何 TOOL 消息")
    void exactlyNRoundsKeepsAll() {
        List<ConversationMessage> messages = new ArrayList<>(List.of(
                msg(MessageRole.USER, "q1"),
                toolMsg("tool1"),
                msg(MessageRole.ASSISTANT, "a1"),
                msg(MessageRole.USER, "q2"),
                toolMsg("tool2"),
                msg(MessageRole.ASSISTANT, "a2")
        ));

        SnipCompactor snip = new SnipCompactor(2);
        List<ConversationMessage> result = snip.compact(messages);

        long toolCount = result.stream().filter(m -> m.getRole() == MessageRole.TOOL).count();
        assertEquals(2, toolCount, "All TOOL messages should be kept at exact N rounds");
    }

    @Test
    @DisplayName("N+1 轮时最早的 TOOL 消息被删除")
    void nPlusOneRoundsDeletesOldest() {
        List<ConversationMessage> messages = new ArrayList<>(List.of(
                msg(MessageRole.USER, "q1"),
                toolMsg("old-tool"),
                msg(MessageRole.ASSISTANT, "a1"),
                msg(MessageRole.USER, "q2"),
                toolMsg("recent-tool1"),
                msg(MessageRole.ASSISTANT, "a2"),
                msg(MessageRole.USER, "q3"),
                toolMsg("recent-tool2"),
                msg(MessageRole.ASSISTANT, "a3")
        ));

        SnipCompactor snip = new SnipCompactor(2);
        List<ConversationMessage> result = snip.compact(messages);

        long toolCount = result.stream().filter(m -> m.getRole() == MessageRole.TOOL).count();
        assertEquals(2, toolCount, "Only recent 2 rounds' TOOL messages should remain");

        List<String> toolContents = result.stream()
                .filter(m -> m.getRole() == MessageRole.TOOL)
                .map(ConversationMessage::getTextContent)
                .toList();
        assertFalse(toolContents.contains("old-tool"), "Oldest TOOL should be removed");
        assertTrue(toolContents.contains("recent-tool1"));
        assertTrue(toolContents.contains("recent-tool2"));
    }

    @Test
    @DisplayName("USER 和 ASSISTANT 消息永远不被删除")
    void userAndAssistantNeverDeleted() {
        List<ConversationMessage> messages = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            messages.add(msg(MessageRole.USER, "q" + i));
            messages.add(toolMsg("tool" + i));
            messages.add(msg(MessageRole.ASSISTANT, "a" + i));
        }

        SnipCompactor snip = new SnipCompactor(1);
        List<ConversationMessage> result = snip.compact(messages);

        long userCount = result.stream().filter(m -> m.getRole() == MessageRole.USER).count();
        long assistantCount = result.stream().filter(m -> m.getRole() == MessageRole.ASSISTANT).count();
        assertEquals(10, userCount, "All USER messages must be preserved");
        assertEquals(10, assistantCount, "All ASSISTANT messages must be preserved");
    }

    @Test
    @DisplayName("连续多个 TOOL 消息在保护线内全部保留")
    void multipleConsecutiveToolsWithinProtection() {
        List<ConversationMessage> messages = new ArrayList<>(List.of(
                msg(MessageRole.USER, "q1"),
                msg(MessageRole.ASSISTANT, "a1"),
                msg(MessageRole.USER, "q2"),
                toolMsg("tool-a"),
                toolMsg("tool-b"),
                toolMsg("tool-c"),
                msg(MessageRole.ASSISTANT, "a2")
        ));

        SnipCompactor snip = new SnipCompactor(2);
        List<ConversationMessage> result = snip.compact(messages);

        long toolCount = result.stream().filter(m -> m.getRole() == MessageRole.TOOL).count();
        assertEquals(3, toolCount, "All TOOL messages within protection range should be kept");
    }

    @Test
    @DisplayName("keepRecentRounds=0 时 boundary=0，TOOL 消息在保护线后仍保留")
    void keepZeroKeepsToolsBeyondBoundary() {
        List<ConversationMessage> messages = new ArrayList<>(List.of(
                msg(MessageRole.USER, "q1"),
                toolMsg("tool1"),
                msg(MessageRole.ASSISTANT, "a1")
        ));

        SnipCompactor snip = new SnipCompactor(0);
        List<ConversationMessage> result = snip.compact(messages);

        long toolCount = result.stream().filter(m -> m.getRole() == MessageRole.TOOL).count();
        assertEquals(1, toolCount, "With boundary=0, TOOL at index>0 is beyond protectFrom, so kept");
        assertEquals(3, result.size(), "All messages should be preserved");
    }
}
