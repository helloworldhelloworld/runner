package com.lightweightai.kernel.context;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MicroCompactor — truncates large TOOL messages")
class MicroCompactorTest {

    private ConversationMessage tool(String text) {
        return ConversationMessage.builder().role(MessageRole.TOOL).textContent(text)
            .metadata(Map.of("tool_use_id", "id1", "is_error", false)).build();
    }

    private ConversationMessage user(String text) {
        return ConversationMessage.builder().role(MessageRole.USER).textContent(text).build();
    }

    private ConversationMessage assistant(String text) {
        return ConversationMessage.builder().role(MessageRole.ASSISTANT).textContent(text).build();
    }

    @Test
    @DisplayName("TOOL message under maxChars → unchanged")
    void underThreshold_unchanged() {
        MicroCompactor compactor = new MicroCompactor(1000);
        ConversationMessage toolMsg = tool("short content");
        List<ConversationMessage> result = compactor.compact(List.of(toolMsg));

        assertEquals(1, result.size());
        assertEquals("short content", result.get(0).getTextContent());
    }

    @Test
    @DisplayName("TOOL message over maxChars → truncated with snip marker")
    void overThreshold_truncated() {
        MicroCompactor compactor = new MicroCompactor(100, 20);
        String longText = "A".repeat(200);
        ConversationMessage toolMsg = tool(longText);

        List<ConversationMessage> result = compactor.compact(List.of(toolMsg));

        assertEquals(1, result.size());
        String truncated = result.get(0).getTextContent();
        assertTrue(truncated.contains("[...snipped"), "Should contain snip marker");
        assertTrue(truncated.startsWith("A".repeat(20)), "Should keep first keepChars");
        assertTrue(truncated.endsWith("A".repeat(20)), "Should keep last keepChars");
        assertTrue(truncated.length() < longText.length(), "Truncated should be shorter");
    }

    @Test
    @DisplayName("non-TOOL messages are never modified")
    void nonToolMessages_unchanged() {
        MicroCompactor compactor = new MicroCompactor(10, 3);
        String longText = "B".repeat(100);
        ConversationMessage userMsg = user(longText);
        ConversationMessage assistantMsg = assistant(longText);

        List<ConversationMessage> result = compactor.compact(List.of(userMsg, assistantMsg));

        assertEquals(2, result.size());
        assertEquals(longText, result.get(0).getTextContent());
        assertEquals(longText, result.get(1).getTextContent());
    }

    @Test
    @DisplayName("metadata preserved on truncated TOOL messages")
    void metadataPreserved() {
        MicroCompactor compactor = new MicroCompactor(50, 10);
        ConversationMessage toolMsg = tool("C".repeat(100));

        List<ConversationMessage> result = compactor.compact(List.of(toolMsg));

        assertEquals(MessageRole.TOOL, result.get(0).getRole());
        assertEquals("id1", result.get(0).getMetadata().get("tool_use_id"));
    }

    @Test
    @DisplayName("default keepChars is min(200, maxChars/4)")
    void defaultKeepChars() {
        MicroCompactor compactor = new MicroCompactor(100);
        String longText = "D".repeat(200);
        ConversationMessage toolMsg = tool(longText);

        List<ConversationMessage> result = compactor.compact(List.of(toolMsg));
        String truncated = result.get(0).getTextContent();
        // keepChars = min(200, 100/4) = 25
        assertTrue(truncated.contains("[...snipped"));
    }

    @Test
    @DisplayName("empty list → returns empty")
    void emptyList() {
        MicroCompactor compactor = new MicroCompactor(100);
        assertTrue(compactor.compact(List.of()).isEmpty());
    }

    @Test
    @DisplayName("snip marker reports correct snipped char count")
    void snipMarkerReportsCorrectCount() {
        MicroCompactor compactor = new MicroCompactor(100, 10);
        String longText = "E".repeat(200);
        ConversationMessage toolMsg = tool(longText);

        List<ConversationMessage> result = compactor.compact(List.of(toolMsg));
        String truncated = result.get(0).getTextContent();
        // snipped = 200 - (10 * 2) = 180
        assertTrue(truncated.contains("180 chars"), "Snip marker should report 180 chars snipped");
    }
}
