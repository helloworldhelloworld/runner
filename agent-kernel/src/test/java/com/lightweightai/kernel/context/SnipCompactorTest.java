package com.lightweightai.kernel.context;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SnipCompactor - removes old TOOL messages beyond keepRecentRounds")
class SnipCompactorTest {

    @Test
    @DisplayName("fewer rounds than keepRecentRounds: nothing removed")
    void testFewerRoundsThanThreshold() {
        SnipCompactor compactor = new SnipCompactor(3);

        List<ConversationMessage> messages = List.of(
                userMsg("q1"), toolMsg("t1"), assistantMsg("a1"),
                userMsg("q2"), toolMsg("t2"), assistantMsg("a2")
        );

        List<ConversationMessage> result = compactor.compact(messages);
        assertEquals(messages.size(), result.size());
    }

    @Test
    @DisplayName("exact keepRecentRounds: nothing removed")
    void testExactRounds() {
        SnipCompactor compactor = new SnipCompactor(2);

        List<ConversationMessage> messages = List.of(
                userMsg("q1"), toolMsg("t1"), assistantMsg("a1"),
                userMsg("q2"), toolMsg("t2"), assistantMsg("a2")
        );

        List<ConversationMessage> result = compactor.compact(messages);
        assertEquals(messages.size(), result.size());
    }

    @Test
    @DisplayName("exceeds keepRecentRounds: old TOOL messages removed")
    void testExceedsThreshold() {
        SnipCompactor compactor = new SnipCompactor(1);

        List<ConversationMessage> messages = new ArrayList<>(List.of(
                userMsg("old q"), toolMsg("old tool"), assistantMsg("old a"),
                userMsg("new q"), toolMsg("new tool"), assistantMsg("new a")
        ));

        List<ConversationMessage> result = compactor.compact(messages);

        boolean oldToolPresent = result.stream()
                .anyMatch(m -> m.getRole() == MessageRole.TOOL && "old tool".equals(m.getTextContent()));
        assertFalse(oldToolPresent, "Old TOOL message should be removed");

        boolean newToolPresent = result.stream()
                .anyMatch(m -> m.getRole() == MessageRole.TOOL && "new tool".equals(m.getTextContent()));
        assertTrue(newToolPresent, "Recent TOOL message should be kept");
    }

    @Test
    @DisplayName("USER/ASSISTANT/SYSTEM messages are always preserved")
    void testNonToolPreserved() {
        SnipCompactor compactor = new SnipCompactor(1);

        List<ConversationMessage> messages = new ArrayList<>(List.of(
                ConversationMessage.builder().role(MessageRole.SYSTEM).textContent("sys").build(),
                userMsg("old q"), toolMsg("old tool"), assistantMsg("old a"),
                userMsg("new q"), assistantMsg("new a")
        ));

        List<ConversationMessage> result = compactor.compact(messages);

        assertTrue(result.stream().anyMatch(m -> m.getRole() == MessageRole.SYSTEM));
        assertEquals(2, result.stream().filter(m -> m.getRole() == MessageRole.USER).count());
        assertEquals(2, result.stream().filter(m -> m.getRole() == MessageRole.ASSISTANT).count());
    }

    @Test
    @DisplayName("keepRecentRounds=0 removes all TOOL messages")
    void testZeroKeepRemovesAll() {
        SnipCompactor compactor = new SnipCompactor(0);

        List<ConversationMessage> messages = new ArrayList<>(List.of(
                userMsg("q1"), toolMsg("t1"), assistantMsg("a1"),
                userMsg("q2"), toolMsg("t2"), assistantMsg("a2")
        ));

        List<ConversationMessage> result = compactor.compact(messages);

        long toolCount = result.stream().filter(m -> m.getRole() == MessageRole.TOOL).count();
        assertEquals(0, toolCount, "All TOOL messages should be removed with keepRecentRounds=0");
    }

    @Test
    @DisplayName("multiple TOOL messages per round: old ones removed, recent ones kept")
    void testMultipleToolsPerRound() {
        SnipCompactor compactor = new SnipCompactor(1);

        List<ConversationMessage> messages = new ArrayList<>(List.of(
                userMsg("old q"),
                toolMsg("old tool 1"),
                toolMsg("old tool 2"),
                assistantMsg("old a"),
                userMsg("new q"),
                toolMsg("new tool 1"),
                toolMsg("new tool 2"),
                assistantMsg("new a")
        ));

        List<ConversationMessage> result = compactor.compact(messages);

        long oldToolCount = result.stream()
                .filter(m -> m.getRole() == MessageRole.TOOL && m.getTextContent().startsWith("old"))
                .count();
        assertEquals(0, oldToolCount);

        long newToolCount = result.stream()
                .filter(m -> m.getRole() == MessageRole.TOOL && m.getTextContent().startsWith("new"))
                .count();
        assertEquals(2, newToolCount);
    }

    @Test
    @DisplayName("conversation without TOOL messages is unchanged")
    void testNoToolMessages() {
        SnipCompactor compactor = new SnipCompactor(1);

        List<ConversationMessage> messages = List.of(
                userMsg("q1"), assistantMsg("a1"),
                userMsg("q2"), assistantMsg("a2"),
                userMsg("q3"), assistantMsg("a3")
        );

        List<ConversationMessage> result = compactor.compact(messages);
        assertEquals(messages.size(), result.size());
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
}
