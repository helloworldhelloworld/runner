package com.lightweightai.kernel.context;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MicroCompactor - truncates oversized TOOL messages")
class MicroCompactorTest {

    @Test
    @DisplayName("short TOOL messages pass through unchanged")
    void testShortMessageUnchanged() {
        MicroCompactor compactor = new MicroCompactor(100);
        ConversationMessage tool = toolMsg("short result");

        List<ConversationMessage> result = compactor.compact(List.of(tool));

        assertEquals(1, result.size());
        assertEquals("short result", result.get(0).getTextContent());
    }

    @Test
    @DisplayName("TOOL message exactly at maxChars passes through")
    void testExactLengthUnchanged() {
        String exact = "a".repeat(100);
        MicroCompactor compactor = new MicroCompactor(100);

        List<ConversationMessage> result = compactor.compact(List.of(toolMsg(exact)));

        assertEquals(exact, result.get(0).getTextContent());
    }

    @Test
    @DisplayName("TOOL message exceeding maxChars is truncated with snip marker")
    void testLongMessageTruncated() {
        String longContent = "a".repeat(500);
        MicroCompactor compactor = new MicroCompactor(100);

        List<ConversationMessage> result = compactor.compact(List.of(toolMsg(longContent)));

        String truncated = result.get(0).getTextContent();
        assertTrue(truncated.contains("[...snipped"));
        assertTrue(truncated.length() < longContent.length());
    }

    @Test
    @DisplayName("truncated content preserves head and tail of original")
    void testPreservesHeadAndTail() {
        String head = "HEAD_CONTENT_";
        String tail = "_TAIL_CONTENT";
        String middle = "m".repeat(500);
        String longContent = head + middle + tail;

        MicroCompactor compactor = new MicroCompactor(100, 20);

        List<ConversationMessage> result = compactor.compact(List.of(toolMsg(longContent)));

        String truncated = result.get(0).getTextContent();
        assertTrue(truncated.startsWith(head), "Should preserve head");
        assertTrue(truncated.endsWith(tail), "Should preserve tail");
        assertTrue(truncated.contains("[...snipped"));
    }

    @Test
    @DisplayName("snip marker shows correct character count")
    void testSnipCountAccurate() {
        String content = "a".repeat(300);
        int keepChars = 50;
        MicroCompactor compactor = new MicroCompactor(100, keepChars);

        List<ConversationMessage> result = compactor.compact(List.of(toolMsg(content)));

        String truncated = result.get(0).getTextContent();
        int expectedSnipped = content.length() - (keepChars * 2);
        assertTrue(truncated.contains("[...snipped " + expectedSnipped + " chars...]"),
                "Snip count should be " + expectedSnipped + ", got: " + truncated);
    }

    @Test
    @DisplayName("USER messages are never truncated")
    void testUserMessageUntouched() {
        String longContent = "u".repeat(500);
        MicroCompactor compactor = new MicroCompactor(100);

        List<ConversationMessage> result = compactor.compact(List.of(userMsg(longContent)));

        assertEquals(longContent, result.get(0).getTextContent());
    }

    @Test
    @DisplayName("ASSISTANT messages are never truncated")
    void testAssistantMessageUntouched() {
        String longContent = "a".repeat(500);
        MicroCompactor compactor = new MicroCompactor(100);

        List<ConversationMessage> result = compactor.compact(List.of(assistantMsg(longContent)));

        assertEquals(longContent, result.get(0).getTextContent());
    }

    @Test
    @DisplayName("SYSTEM messages are never truncated")
    void testSystemMessageUntouched() {
        String longContent = "s".repeat(500);
        MicroCompactor compactor = new MicroCompactor(100);

        ConversationMessage sys = ConversationMessage.builder()
                .role(MessageRole.SYSTEM).textContent(longContent).build();

        List<ConversationMessage> result = compactor.compact(List.of(sys));

        assertEquals(longContent, result.get(0).getTextContent());
    }

    @Test
    @DisplayName("mixed messages: only TOOL messages above threshold are truncated")
    void testMixedMessages() {
        String longTool = "t".repeat(500);
        MicroCompactor compactor = new MicroCompactor(100);

        List<ConversationMessage> messages = List.of(
                userMsg("question"),
                toolMsg("short"),
                toolMsg(longTool),
                assistantMsg("answer")
        );

        List<ConversationMessage> result = compactor.compact(messages);

        assertEquals(4, result.size());
        assertEquals("question", result.get(0).getTextContent());
        assertEquals("short", result.get(1).getTextContent());
        assertTrue(result.get(2).getTextContent().contains("[...snipped"));
        assertEquals("answer", result.get(3).getTextContent());
    }

    @Test
    @DisplayName("truncated TOOL preserves original metadata")
    void testPreservesMetadata() {
        ConversationMessage tool = ConversationMessage.builder()
                .role(MessageRole.TOOL)
                .textContent("x".repeat(500))
                .addMetadata("toolId", "calc-123")
                .build();

        MicroCompactor compactor = new MicroCompactor(100);
        List<ConversationMessage> result = compactor.compact(List.of(tool));

        assertEquals("calc-123", result.get(0).getMetadata().get("toolId"));
    }

    @Test
    @DisplayName("default keepChars is min(200, maxChars/4)")
    void testDefaultKeepChars() {
        MicroCompactor small = new MicroCompactor(400);
        MicroCompactor large = new MicroCompactor(2000);

        String content = "x".repeat(3000);
        List<ConversationMessage> result1 = small.compact(List.of(toolMsg(content)));
        List<ConversationMessage> result2 = large.compact(List.of(toolMsg(content)));

        assertTrue(result1.get(0).getTextContent().contains("[...snipped"));
        assertTrue(result2.get(0).getTextContent().contains("[...snipped"));
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
