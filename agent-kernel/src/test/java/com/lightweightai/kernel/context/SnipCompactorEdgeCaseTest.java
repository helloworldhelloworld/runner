package com.lightweightai.kernel.context;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SnipCompactor - 边界条件与 SYSTEM 消息保留")
class SnipCompactorEdgeCaseTest {

    private ConversationMessage msg(MessageRole role, String text) {
        return ConversationMessage.builder().role(role).textContent(text).build();
    }

    @Test
    @DisplayName("keepRecentRounds=0 boundary defaults to 0, so no TOOL is considered old")
    void zeroRoundsKeepsAllToolsDueToBoundaryLogic() {
        List<ConversationMessage> messages = List.of(
                msg(MessageRole.USER, "q1"),
                msg(MessageRole.TOOL, "tool1"),
                msg(MessageRole.ASSISTANT, "a1"),
                msg(MessageRole.USER, "q2"),
                msg(MessageRole.TOOL, "tool2"),
                msg(MessageRole.ASSISTANT, "a2"));

        SnipCompactor snip = new SnipCompactor(0);
        List<ConversationMessage> result = snip.compact(messages);

        // boundary=0 means i<0 is never true, so all TOOL messages survive
        assertEquals(messages.size(), result.size());
    }

    @Test
    @DisplayName("keepRecentRounds=1 with 3 rounds removes first 2 rounds' TOOL messages")
    void oneRoundKeepsOnlyLatestTool() {
        List<ConversationMessage> messages = List.of(
                msg(MessageRole.USER, "q1"),
                msg(MessageRole.TOOL, "old-tool-1"),
                msg(MessageRole.ASSISTANT, "a1"),
                msg(MessageRole.USER, "q2"),
                msg(MessageRole.TOOL, "old-tool-2"),
                msg(MessageRole.ASSISTANT, "a2"),
                msg(MessageRole.USER, "q3"),
                msg(MessageRole.TOOL, "recent-tool"),
                msg(MessageRole.ASSISTANT, "a3"));

        SnipCompactor snip = new SnipCompactor(1);
        List<ConversationMessage> result = snip.compact(messages);

        long toolCount = result.stream().filter(m -> m.getRole() == MessageRole.TOOL).count();
        assertEquals(1, toolCount);
        assertEquals("recent-tool", result.stream()
                .filter(m -> m.getRole() == MessageRole.TOOL)
                .findFirst().get().getTextContent());
    }

    @Test
    @DisplayName("SYSTEM messages are always preserved")
    void systemMessagesAlwaysPreserved() {
        List<ConversationMessage> messages = List.of(
                msg(MessageRole.SYSTEM, "You are an assistant"),
                msg(MessageRole.USER, "q1"),
                msg(MessageRole.TOOL, "old-tool"),
                msg(MessageRole.ASSISTANT, "a1"),
                msg(MessageRole.USER, "q2"),
                msg(MessageRole.ASSISTANT, "a2"),
                msg(MessageRole.USER, "q3"),
                msg(MessageRole.ASSISTANT, "a3"));

        SnipCompactor snip = new SnipCompactor(1);
        List<ConversationMessage> result = snip.compact(messages);

        assertTrue(result.stream().anyMatch(
                m -> m.getRole() == MessageRole.SYSTEM && m.getTextContent().equals("You are an assistant")));
    }

    @Test
    @DisplayName("恰好等于 keepRecentRounds 时不删除任何消息")
    void exactRoundCountPreservesAll() {
        List<ConversationMessage> messages = List.of(
                msg(MessageRole.USER, "q1"),
                msg(MessageRole.TOOL, "tool"),
                msg(MessageRole.ASSISTANT, "a1"),
                msg(MessageRole.USER, "q2"),
                msg(MessageRole.ASSISTANT, "a2"));

        SnipCompactor snip = new SnipCompactor(2);
        List<ConversationMessage> result = snip.compact(messages);

        assertEquals(messages.size(), result.size());
    }

    @Test
    @DisplayName("只有 USER 消息（无 ASSISTANT）的对话不崩溃")
    void onlyUserMessagesDoesNotCrash() {
        List<ConversationMessage> messages = List.of(
                msg(MessageRole.USER, "q1"),
                msg(MessageRole.USER, "q2"),
                msg(MessageRole.TOOL, "tool"));

        SnipCompactor snip = new SnipCompactor(1);
        List<ConversationMessage> result = snip.compact(messages);

        assertNotNull(result);
    }

    @Test
    @DisplayName("连续多个 TOOL 消息在保护线之前全被删除")
    void multipleConsecutiveOldToolsRemoved() {
        List<ConversationMessage> messages = List.of(
                msg(MessageRole.USER, "q1"),
                msg(MessageRole.TOOL, "tool-a"),
                msg(MessageRole.TOOL, "tool-b"),
                msg(MessageRole.TOOL, "tool-c"),
                msg(MessageRole.ASSISTANT, "a1"),
                msg(MessageRole.USER, "q2"),
                msg(MessageRole.TOOL, "recent-tool"),
                msg(MessageRole.ASSISTANT, "a2"));

        SnipCompactor snip = new SnipCompactor(1);
        List<ConversationMessage> result = snip.compact(messages);

        long toolCount = result.stream().filter(m -> m.getRole() == MessageRole.TOOL).count();
        assertEquals(1, toolCount);
        assertEquals("recent-tool", result.stream()
                .filter(m -> m.getRole() == MessageRole.TOOL)
                .findFirst().get().getTextContent());
    }

    @Test
    @DisplayName("空消息列表不报错")
    void emptyListDoesNotThrow() {
        SnipCompactor snip = new SnipCompactor(3);
        List<ConversationMessage> result = snip.compact(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("无 TOOL 消息的对话原样返回")
    void noToolMessagesReturnsSame() {
        List<ConversationMessage> messages = List.of(
                msg(MessageRole.USER, "q1"),
                msg(MessageRole.ASSISTANT, "a1"),
                msg(MessageRole.USER, "q2"),
                msg(MessageRole.ASSISTANT, "a2"),
                msg(MessageRole.USER, "q3"),
                msg(MessageRole.ASSISTANT, "a3"));

        SnipCompactor snip = new SnipCompactor(1);
        List<ConversationMessage> result = snip.compact(messages);

        assertEquals(messages.size(), result.size());
    }
}
