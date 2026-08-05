package com.lightweightai.kernel.context;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CompactionChain — chained context compactors")
class CompactionChainTest {

    private ConversationMessage user(String text) {
        return ConversationMessage.builder().role(MessageRole.USER).textContent(text).build();
    }

    private ConversationMessage assistant(String text) {
        return ConversationMessage.builder().role(MessageRole.ASSISTANT).textContent(text).build();
    }

    private ConversationMessage tool(String text) {
        return ConversationMessage.builder().role(MessageRole.TOOL).textContent(text)
            .metadata(Map.of("tool_use_id", "id1", "is_error", false)).build();
    }

    @Test
    @DisplayName("stages execute in order: Snip removes old TOOL, then Micro truncates remaining large TOOL")
    void stagesExecuteInOrder() {
        SnipCompactor snip = new SnipCompactor(1);
        MicroCompactor micro = new MicroCompactor(50, 10);
        CompactionChain chain = new CompactionChain(snip, micro);

        String longToolText = "R".repeat(200);
        List<ConversationMessage> messages = List.of(
            user("q1"), tool("old-tool"), assistant("a1"),     // round 1 (old)
            user("q2"), tool(longToolText), assistant("a2")    // round 2 (recent, but large)
        );

        List<ConversationMessage> result = chain.compact(messages);

        // Snip should remove old TOOL
        boolean hasOldTool = result.stream()
            .anyMatch(m -> m.getRole() == MessageRole.TOOL
                && "old-tool".equals(m.getTextContent()));
        assertFalse(hasOldTool, "Old TOOL should be removed by SnipCompactor");

        // Micro should truncate the remaining large TOOL
        ConversationMessage recentTool = result.stream()
            .filter(m -> m.getRole() == MessageRole.TOOL)
            .findFirst()
            .orElseThrow();
        assertTrue(recentTool.getTextContent().contains("[...snipped"),
            "Large TOOL should be truncated by MicroCompactor");
    }

    @Test
    @DisplayName("empty chain returns messages unchanged")
    void emptyChain() {
        CompactionChain chain = new CompactionChain();
        List<ConversationMessage> messages = List.of(user("hello"), assistant("world"));

        List<ConversationMessage> result = chain.compact(messages);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("single-stage chain delegates to that stage")
    void singleStageChain() {
        SnipCompactor snip = new SnipCompactor(1);
        CompactionChain chain = new CompactionChain(snip);

        List<ConversationMessage> messages = List.of(
            user("q1"), tool("t1"), assistant("a1"),
            user("q2"), tool("t2"), assistant("a2")
        );

        List<ConversationMessage> result = chain.compact(messages);
        // Only 1 TOOL should remain (the recent one)
        long toolCount = result.stream().filter(m -> m.getRole() == MessageRole.TOOL).count();
        assertEquals(1, toolCount);
    }

    @Test
    @DisplayName("chain preserves message order after compaction")
    void chainPreservesMessageOrder() {
        SnipCompactor snip = new SnipCompactor(1);
        MicroCompactor micro = new MicroCompactor(50, 10);
        CompactionChain chain = new CompactionChain(snip, micro);

        List<ConversationMessage> messages = List.of(
            user("q1"), tool("old-tool"), assistant("a1"),
            user("q2"), assistant("a2")
        );

        List<ConversationMessage> result = chain.compact(messages);

        // Verify user messages stay in order
        List<String> userTexts = result.stream()
            .filter(m -> m.getRole() == MessageRole.USER)
            .map(ConversationMessage::getTextContent)
            .toList();
        assertEquals(List.of("q1", "q2"), userTexts, "User messages should maintain order");
    }

    @Test
    @DisplayName("chain with three stages applies all in sequence")
    void threeStageChain() {
        SnipCompactor snip = new SnipCompactor(1);
        MicroCompactor micro1 = new MicroCompactor(100, 20);
        MicroCompactor micro2 = new MicroCompactor(50, 10);
        CompactionChain chain = new CompactionChain(snip, micro1, micro2);

        String longText = "R".repeat(200);
        List<ConversationMessage> messages = List.of(
            user("q1"), tool("old"), assistant("a1"),
            user("q2"), tool(longText), assistant("a2")
        );

        List<ConversationMessage> result = chain.compact(messages);

        // Old tool removed by snip, long tool truncated by micro stages
        boolean hasOldTool = result.stream()
            .anyMatch(m -> m.getRole() == MessageRole.TOOL && "old".equals(m.getTextContent()));
        assertFalse(hasOldTool, "Old tool should be removed");

        // Remaining tool should be truncated by the second micro
        ConversationMessage remainingTool = result.stream()
            .filter(m -> m.getRole() == MessageRole.TOOL)
            .findFirst()
            .orElseThrow();
        assertTrue(remainingTool.getTextContent().length() < 200,
            "Tool content should be truncated by micro compactors");
    }

    @Test
    @DisplayName("chain handles messages with no TOOL messages")
    void chainWithNoToolMessages() {
        SnipCompactor snip = new SnipCompactor(1);
        MicroCompactor micro = new MicroCompactor(50, 10);
        CompactionChain chain = new CompactionChain(snip, micro);

        List<ConversationMessage> messages = List.of(
            user("q1"), assistant("a1"),
            user("q2"), assistant("a2")
        );

        List<ConversationMessage> result = chain.compact(messages);
        assertEquals(4, result.size(), "All messages should be preserved when no TOOL messages");
    }
}
