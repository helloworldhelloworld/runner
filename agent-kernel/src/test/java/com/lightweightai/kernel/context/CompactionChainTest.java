package com.lightweightai.kernel.context;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CompactionChain - 链式压缩器")
class CompactionChainTest {

    private ConversationMessage msg(MessageRole role, String text) {
        return ConversationMessage.builder().role(role).textContent(text).build();
    }

    private ConversationMessage toolMsg(String text) {
        return ConversationMessage.builder().role(MessageRole.TOOL).textContent(text).build();
    }

    @Test
    @DisplayName("空链返回消息列表不变")
    void emptyChainReturnsMessagesUnchanged() {
        List<ConversationMessage> messages = List.of(
                msg(MessageRole.USER, "hello"),
                msg(MessageRole.ASSISTANT, "world")
        );

        CompactionChain chain = new CompactionChain();
        List<ConversationMessage> result = chain.compact(messages);

        assertEquals(2, result.size());
        assertEquals("hello", result.get(0).getTextContent());
        assertEquals(MessageRole.USER, result.get(0).getRole());
        assertEquals("world", result.get(1).getTextContent());
        assertEquals(MessageRole.ASSISTANT, result.get(1).getRole());
    }

    @Test
    @DisplayName("单阶段链正确委托给内部压缩器")
    void singleStageChainDelegatesCorrectly() {
        String largeContent = "x".repeat(2000);
        List<ConversationMessage> messages = new ArrayList<>(List.of(
                msg(MessageRole.USER, "question"),
                toolMsg(largeContent),
                msg(MessageRole.ASSISTANT, "answer")
        ));

        CompactionChain chain = new CompactionChain(new MicroCompactor(500));
        List<ConversationMessage> result = chain.compact(messages);

        assertEquals(3, result.size());
        assertEquals("question", result.get(0).getTextContent());
        assertTrue(result.get(1).getTextContent().contains("[...snipped"));
        assertTrue(result.get(1).getTextContent().length() < 2000);
        assertEquals("answer", result.get(2).getTextContent());
    }

    @Test
    @DisplayName("多阶段链按顺序执行：前一阶段输出作为后一阶段输入")
    void multiStageChainAppliesInOrder() {
        String largeContent = "a".repeat(3000);
        List<ConversationMessage> messages = new ArrayList<>(List.of(
                msg(MessageRole.USER, "q1"),
                msg(MessageRole.ASSISTANT, "a1"),
                toolMsg("old tool result"),
                msg(MessageRole.USER, "q2"),
                toolMsg(largeContent),
                msg(MessageRole.ASSISTANT, "a2"),
                msg(MessageRole.USER, "q3"),
                msg(MessageRole.ASSISTANT, "a3")
        ));

        CompactionChain chain = new CompactionChain(
                new SnipCompactor(2),
                new MicroCompactor(500)
        );
        List<ConversationMessage> result = chain.compact(messages);

        long toolCount = result.stream().filter(m -> m.getRole() == MessageRole.TOOL).count();
        assertEquals(1, toolCount, "SnipCompactor should remove old TOOL, keep recent one");

        ConversationMessage remainingTool = result.stream()
                .filter(m -> m.getRole() == MessageRole.TOOL)
                .findFirst().orElseThrow();
        assertTrue(remainingTool.getTextContent().contains("[...snipped"),
                "MicroCompactor should truncate the surviving large TOOL message");

        long userCount = result.stream().filter(m -> m.getRole() == MessageRole.USER).count();
        assertEquals(3, userCount, "USER messages must survive all stages");

        long assistantCount = result.stream().filter(m -> m.getRole() == MessageRole.ASSISTANT).count();
        assertEquals(3, assistantCount, "ASSISTANT messages must survive all stages");
    }

    @Test
    @DisplayName("链实际变换内容：输入和输出内容不同")
    void chainActuallyTransformsPayload() {
        String large = "Z".repeat(5000);
        List<ConversationMessage> messages = new ArrayList<>(List.of(
                msg(MessageRole.USER, "q1"),
                toolMsg("will be snipped"),
                msg(MessageRole.USER, "q2"),
                toolMsg(large),
                msg(MessageRole.USER, "q3")
        ));

        CompactionChain chain = new CompactionChain(
                new SnipCompactor(1),
                new MicroCompactor(400)
        );
        List<ConversationMessage> result = chain.compact(messages);

        boolean oldToolPresent = result.stream()
                .anyMatch(m -> "will be snipped".equals(m.getTextContent()));
        assertFalse(oldToolPresent, "Old TOOL message content should not appear in result");

        boolean rawLargePresent = result.stream()
                .anyMatch(m -> large.equals(m.getTextContent()));
        assertFalse(rawLargePresent, "Raw large TOOL content should be transformed by MicroCompactor");
    }

    @Test
    @DisplayName("Snip 先删除旧 TOOL 后 Micro 不再需要截断：链式效率验证")
    void snipReducesWorkForMicro() {
        String largeContent = "B".repeat(3000);
        List<ConversationMessage> messages = new ArrayList<>(List.of(
                msg(MessageRole.USER, "q1"),
                toolMsg(largeContent),
                msg(MessageRole.ASSISTANT, "a1"),
                msg(MessageRole.USER, "q2"),
                toolMsg("small tool"),
                msg(MessageRole.ASSISTANT, "a2"),
                msg(MessageRole.USER, "q3"),
                toolMsg(largeContent),
                msg(MessageRole.ASSISTANT, "a3")
        ));

        CompactionChain chain = new CompactionChain(
                new SnipCompactor(2),
                new MicroCompactor(500)
        );
        List<ConversationMessage> result = chain.compact(new ArrayList<>(messages));

        long toolCount = result.stream()
                .filter(m -> m.getRole() == MessageRole.TOOL)
                .count();
        assertEquals(2, toolCount, "SnipCompactor(2) keeps recent 2 rounds' TOOLs");

        boolean hasRawLarge = result.stream()
                .filter(m -> m.getRole() == MessageRole.TOOL)
                .anyMatch(m -> m.getTextContent().length() > 500);
        assertFalse(hasRawLarge, "MicroCompactor should truncate surviving large TOOL");

        boolean hasSmall = result.stream()
                .anyMatch(m -> "small tool".equals(m.getTextContent()));
        assertTrue(hasSmall, "Small TOOL should survive both stages unchanged");
    }
}
