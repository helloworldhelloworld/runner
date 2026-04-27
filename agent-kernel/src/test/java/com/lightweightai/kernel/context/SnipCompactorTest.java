package com.lightweightai.kernel.context;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SnipCompactor - 单元测试：边界条件与不变量")
class SnipCompactorTest {

    private ConversationMessage msg(MessageRole role, String text) {
        return ConversationMessage.builder().role(role).textContent(text).build();
    }

    @Test
    @DisplayName("空消息列表返回空列表")
    void emptyListReturnsEmpty() {
        SnipCompactor snip = new SnipCompactor(3);
        List<ConversationMessage> result = snip.compact(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("只有 SYSTEM 消息时不删除")
    void systemOnlyPreserved() {
        List<ConversationMessage> msgs = List.of(msg(MessageRole.SYSTEM, "You are a bot"));
        List<ConversationMessage> result = new SnipCompactor(1).compact(msgs);
        assertEquals(1, result.size());
        assertEquals(MessageRole.SYSTEM, result.get(0).getRole());
    }

    @Test
    @DisplayName("恰好 N 轮 USER 消息时不删除任何 TOOL 消息")
    void exactlyNRoundsNoSnip() {
        List<ConversationMessage> msgs = new ArrayList<>(List.of(
                msg(MessageRole.USER, "q1"),
                msg(MessageRole.TOOL, "tool result 1"),
                msg(MessageRole.ASSISTANT, "a1"),
                msg(MessageRole.USER, "q2"),
                msg(MessageRole.TOOL, "tool result 2"),
                msg(MessageRole.ASSISTANT, "a2")
        ));

        SnipCompactor snip = new SnipCompactor(2); // exactly 2 USER messages
        List<ConversationMessage> result = snip.compact(msgs);
        assertEquals(6, result.size(), "No snip when rounds == keepRecentRounds");
    }

    @Test
    @DisplayName("N+1 轮时删除最早轮的 TOOL 消息")
    void nPlus1RoundsSnipsOldest() {
        List<ConversationMessage> msgs = new ArrayList<>(List.of(
                msg(MessageRole.USER, "q1"),
                msg(MessageRole.TOOL, "old tool"),  // should be snipped
                msg(MessageRole.ASSISTANT, "a1"),
                msg(MessageRole.USER, "q2"),
                msg(MessageRole.TOOL, "recent tool 1"),
                msg(MessageRole.ASSISTANT, "a2"),
                msg(MessageRole.USER, "q3"),
                msg(MessageRole.ASSISTANT, "a3")
        ));

        SnipCompactor snip = new SnipCompactor(2);
        List<ConversationMessage> result = snip.compact(msgs);

        long toolCount = result.stream().filter(m -> m.getRole() == MessageRole.TOOL).count();
        assertEquals(1, toolCount);
        assertEquals("recent tool 1", result.stream()
                .filter(m -> m.getRole() == MessageRole.TOOL)
                .findFirst().get().getTextContent());
    }

    @Test
    @DisplayName("USER 和 ASSISTANT 消息永远保留，不受 snip 影响")
    void userAndAssistantAlwaysPreserved() {
        List<ConversationMessage> msgs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            msgs.add(msg(MessageRole.USER, "q" + i));
            msgs.add(msg(MessageRole.TOOL, "tool" + i));
            msgs.add(msg(MessageRole.ASSISTANT, "a" + i));
        }

        SnipCompactor snip = new SnipCompactor(2);
        List<ConversationMessage> result = snip.compact(msgs);

        long userCount = result.stream().filter(m -> m.getRole() == MessageRole.USER).count();
        long assistantCount = result.stream().filter(m -> m.getRole() == MessageRole.ASSISTANT).count();
        assertEquals(10, userCount, "All USER messages preserved");
        assertEquals(10, assistantCount, "All ASSISTANT messages preserved");
    }

    @Test
    @DisplayName("多个连续 TOOL 消息在保护线之前全部被删除")
    void multipleConsecutiveToolMessagesSnipped() {
        List<ConversationMessage> msgs = new ArrayList<>(List.of(
                msg(MessageRole.USER, "q1"),
                msg(MessageRole.TOOL, "t1a"),
                msg(MessageRole.TOOL, "t1b"),
                msg(MessageRole.TOOL, "t1c"),
                msg(MessageRole.ASSISTANT, "a1"),
                msg(MessageRole.USER, "q2"),
                msg(MessageRole.ASSISTANT, "a2")
        ));

        SnipCompactor snip = new SnipCompactor(1);
        List<ConversationMessage> result = snip.compact(msgs);

        long toolCount = result.stream().filter(m -> m.getRole() == MessageRole.TOOL).count();
        assertEquals(0, toolCount, "All old TOOL messages before protect boundary snipped");
    }

    @Test
    @DisplayName("keepRecentRounds=1 时只保留最后一轮的 TOOL 消息")
    void keepOneRound() {
        List<ConversationMessage> msgs = new ArrayList<>(List.of(
                msg(MessageRole.USER, "q1"),
                msg(MessageRole.TOOL, "old"),
                msg(MessageRole.ASSISTANT, "a1"),
                msg(MessageRole.USER, "q2"),
                msg(MessageRole.TOOL, "latest"),
                msg(MessageRole.ASSISTANT, "a2")
        ));

        SnipCompactor snip = new SnipCompactor(1);
        List<ConversationMessage> result = snip.compact(msgs);

        long toolCount = result.stream().filter(m -> m.getRole() == MessageRole.TOOL).count();
        assertEquals(1, toolCount);
        assertEquals("latest", result.stream()
                .filter(m -> m.getRole() == MessageRole.TOOL)
                .findFirst().get().getTextContent());
    }
}
