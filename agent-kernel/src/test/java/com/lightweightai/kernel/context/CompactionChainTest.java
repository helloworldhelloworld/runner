package com.lightweightai.kernel.context;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CompactionChain - chained context compactors")
class CompactionChainTest {

    @Test
    @DisplayName("empty chain returns messages unchanged")
    void testEmptyChain() {
        CompactionChain chain = new CompactionChain();
        List<ConversationMessage> messages = List.of(userMsg("hello"));

        List<ConversationMessage> result = chain.compact(messages);

        assertEquals(1, result.size());
        assertEquals("hello", result.get(0).getTextContent());
    }

    @Test
    @DisplayName("single stage applies its compaction")
    void testSingleStage() {
        CompactionChain chain = new CompactionChain(new SnipCompactor(1));

        List<ConversationMessage> messages = new ArrayList<>();
        messages.add(userMsg("old question"));
        messages.add(toolMsg("old tool result"));
        messages.add(assistantMsg("old answer"));
        messages.add(userMsg("new question"));
        messages.add(toolMsg("new tool result"));
        messages.add(assistantMsg("new answer"));

        List<ConversationMessage> result = chain.compact(messages);

        long toolCount = result.stream().filter(m -> m.getRole() == MessageRole.TOOL).count();
        assertEquals(1, toolCount, "Old TOOL message should be removed by SnipCompactor(1)");
    }

    @Test
    @DisplayName("multiple stages execute in order: Snip first, then Micro")
    void testMultiStagesOrdered() {
        CompactionChain chain = new CompactionChain(
                new SnipCompactor(1),
                new MicroCompactor(50)
        );

        String longToolContent = "x".repeat(200);
        List<ConversationMessage> messages = new ArrayList<>();
        messages.add(userMsg("q1"));
        messages.add(toolMsg("old tool"));
        messages.add(assistantMsg("a1"));
        messages.add(userMsg("q2"));
        messages.add(toolMsg(longToolContent));
        messages.add(assistantMsg("a2"));

        List<ConversationMessage> result = chain.compact(messages);

        boolean oldToolRemoved = result.stream()
                .noneMatch(m -> m.getRole() == MessageRole.TOOL && "old tool".equals(m.getTextContent()));
        assertTrue(oldToolRemoved, "SnipCompactor should remove old TOOL message");

        ConversationMessage remainingTool = result.stream()
                .filter(m -> m.getRole() == MessageRole.TOOL)
                .findFirst()
                .orElseThrow();
        assertTrue(remainingTool.getTextContent().contains("[...snipped"),
                "MicroCompactor should truncate long TOOL content");
        assertTrue(remainingTool.getTextContent().length() < longToolContent.length());
    }

    @Test
    @DisplayName("chain preserves USER/ASSISTANT/SYSTEM messages")
    void testPreservesNonToolMessages() {
        CompactionChain chain = new CompactionChain(new SnipCompactor(0));

        List<ConversationMessage> messages = new ArrayList<>();
        messages.add(systemMsg("system prompt"));
        messages.add(userMsg("q1"));
        messages.add(toolMsg("tool result"));
        messages.add(assistantMsg("a1"));

        List<ConversationMessage> result = chain.compact(messages);

        assertTrue(result.stream().anyMatch(m -> m.getRole() == MessageRole.SYSTEM));
        assertTrue(result.stream().anyMatch(m -> m.getRole() == MessageRole.USER));
        assertTrue(result.stream().anyMatch(m -> m.getRole() == MessageRole.ASSISTANT));
    }

    private static ConversationMessage userMsg(String text) {
        return ConversationMessage.builder().role(MessageRole.USER).textContent(text).build();
    }

    private static ConversationMessage assistantMsg(String text) {
        return ConversationMessage.builder().role(MessageRole.ASSISTANT).textContent(text).build();
    }

    private static ConversationMessage toolMsg(String text) {
        return ConversationMessage.builder().role(MessageRole.TOOL).textContent(text).build();
    }

    private static ConversationMessage systemMsg(String text) {
        return ConversationMessage.builder().role(MessageRole.SYSTEM).textContent(text).build();
    }
}
