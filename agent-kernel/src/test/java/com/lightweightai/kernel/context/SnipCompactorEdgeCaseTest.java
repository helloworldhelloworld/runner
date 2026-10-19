package com.lightweightai.kernel.context;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SnipCompactor 边界条件测试。
 *
 * 已有 SnipCompactorTest 覆盖基本功能。
 * 此测试补充：空列表、只有 SYSTEM 消息、TOOL 在保护线之后不被删除。
 */
@DisplayName("SnipCompactor - 边界条件")
class SnipCompactorEdgeCaseTest {

    private ConversationMessage msg(MessageRole role, String text) {
        return ConversationMessage.builder().role(role).textContent(text).build();
    }

    @Test
    @DisplayName("空消息列表返回空列表")
    void emptyListReturnsEmpty() {
        SnipCompactor compactor = new SnipCompactor(3);
        List<ConversationMessage> result = compactor.compact(new ArrayList<>());
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("只有 SYSTEM 消息全部保留")
    void systemOnlyAllRetained() {
        SnipCompactor compactor = new SnipCompactor(1);
        List<ConversationMessage> msgs = List.of(
                msg(MessageRole.SYSTEM, "You are a helpful assistant"),
                msg(MessageRole.SYSTEM, "Additional instructions")
        );
        List<ConversationMessage> result = compactor.compact(new ArrayList<>(msgs));
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("保护线之后的 TOOL 消息不被删除")
    void toolAfterProtectBoundaryKept() {
        SnipCompactor compactor = new SnipCompactor(1);
        List<ConversationMessage> msgs = new ArrayList<>(List.of(
                msg(MessageRole.USER, "old question"),
                msg(MessageRole.ASSISTANT, "old answer"),
                msg(MessageRole.TOOL, "old tool result"), // should be removed
                msg(MessageRole.USER, "new question"),
                msg(MessageRole.ASSISTANT, "new answer"),
                msg(MessageRole.TOOL, "new tool result")  // should be kept
        ));

        List<ConversationMessage> result = compactor.compact(msgs);

        // Should keep: old question, old answer, new question, new answer, new tool result
        // Should remove: old tool result
        long toolCount = result.stream().filter(m -> m.getRole() == MessageRole.TOOL).count();
        assertEquals(1, toolCount, "Only recent TOOL messages should survive");
        assertEquals("new tool result", result.stream()
                .filter(m -> m.getRole() == MessageRole.TOOL)
                .findFirst().get().getTextContent());
    }

    @Test
    @DisplayName("keepRecentRounds 等于总轮数时不删任何消息")
    void keepAllRoundsNoChange() {
        SnipCompactor compactor = new SnipCompactor(3);
        List<ConversationMessage> msgs = new ArrayList<>(List.of(
                msg(MessageRole.USER, "q1"),
                msg(MessageRole.TOOL, "t1"),
                msg(MessageRole.USER, "q2"),
                msg(MessageRole.TOOL, "t2"),
                msg(MessageRole.USER, "q3"),
                msg(MessageRole.TOOL, "t3")
        ));

        List<ConversationMessage> result = compactor.compact(msgs);
        assertEquals(msgs.size(), result.size(), "No messages should be removed when rounds == keepRecent");
    }

    @Test
    @DisplayName("USER/ASSISTANT 消息永远不被 Snip 删除")
    void userAssistantNeverRemoved() {
        SnipCompactor compactor = new SnipCompactor(1);
        List<ConversationMessage> msgs = new ArrayList<>(List.of(
                msg(MessageRole.USER, "q1"),
                msg(MessageRole.ASSISTANT, "a1"),
                msg(MessageRole.USER, "q2"),
                msg(MessageRole.ASSISTANT, "a2"),
                msg(MessageRole.USER, "q3"),
                msg(MessageRole.ASSISTANT, "a3")
        ));

        List<ConversationMessage> result = compactor.compact(msgs);
        long userCount = result.stream().filter(m -> m.getRole() == MessageRole.USER).count();
        long assistantCount = result.stream().filter(m -> m.getRole() == MessageRole.ASSISTANT).count();
        assertEquals(3, userCount, "All USER messages must be kept");
        assertEquals(3, assistantCount, "All ASSISTANT messages must be kept");
    }
}
