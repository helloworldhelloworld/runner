package com.lightweightai.kernel.context;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SnipCompactor — removes old TOOL messages")
class SnipCompactorTest {

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

    private ConversationMessage system(String text) {
        return ConversationMessage.builder().role(MessageRole.SYSTEM).textContent(text).build();
    }

    @Test
    @DisplayName("fewer rounds than keepRecentRounds → no messages removed")
    void fewerRoundsThanThreshold_noChange() {
        SnipCompactor compactor = new SnipCompactor(3);
        List<ConversationMessage> messages = List.of(
            user("q1"), tool("t1"), assistant("a1"),
            user("q2"), tool("t2"), assistant("a2")
        );

        List<ConversationMessage> result = compactor.compact(messages);

        assertEquals(messages.size(), result.size());
    }

    @Test
    @DisplayName("old TOOL messages removed, recent TOOL messages kept")
    void oldToolMessagesRemoved() {
        SnipCompactor compactor = new SnipCompactor(2);
        List<ConversationMessage> messages = List.of(
            user("q1"), tool("old-tool-1"), assistant("a1"),      // round 1 (old)
            user("q2"), tool("old-tool-2"), assistant("a2"),      // round 2 (old)
            user("q3"), tool("recent-tool-3"), assistant("a3"),   // round 3 (recent)
            user("q4"), tool("recent-tool-4"), assistant("a4")    // round 4 (recent)
        );

        List<ConversationMessage> result = compactor.compact(messages);

        // Old TOOL messages (round 1, 2) should be removed
        boolean hasOldTool = result.stream()
            .anyMatch(m -> m.getRole() == MessageRole.TOOL
                && m.getTextContent().startsWith("old-tool"));
        assertFalse(hasOldTool, "Old TOOL messages should be removed");

        // Recent TOOL messages (round 3, 4) should be kept
        long recentToolCount = result.stream()
            .filter(m -> m.getRole() == MessageRole.TOOL
                && m.getTextContent().startsWith("recent-tool"))
            .count();
        assertEquals(2, recentToolCount, "Recent TOOL messages should be preserved");

        // USER and ASSISTANT messages should all be preserved
        long userCount = result.stream().filter(m -> m.getRole() == MessageRole.USER).count();
        long assistantCount = result.stream().filter(m -> m.getRole() == MessageRole.ASSISTANT).count();
        assertEquals(4, userCount);
        assertEquals(4, assistantCount);
    }

    @Test
    @DisplayName("SYSTEM messages are never removed")
    void systemMessagesPreserved() {
        SnipCompactor compactor = new SnipCompactor(1);
        List<ConversationMessage> messages = List.of(
            system("system prompt"),
            user("q1"), tool("t1"), assistant("a1"),
            user("q2"), tool("t2"), assistant("a2")
        );

        List<ConversationMessage> result = compactor.compact(messages);

        assertTrue(result.stream().anyMatch(m -> m.getRole() == MessageRole.SYSTEM));
    }

    @Test
    @DisplayName("empty message list → returns empty")
    void emptyList() {
        SnipCompactor compactor = new SnipCompactor(3);
        List<ConversationMessage> result = compactor.compact(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("exactly keepRecentRounds user messages → no removal")
    void exactlyAtThreshold() {
        SnipCompactor compactor = new SnipCompactor(2);
        List<ConversationMessage> messages = List.of(
            user("q1"), tool("t1"), assistant("a1"),
            user("q2"), tool("t2"), assistant("a2")
        );

        List<ConversationMessage> result = compactor.compact(messages);
        assertEquals(messages.size(), result.size());
    }
}
