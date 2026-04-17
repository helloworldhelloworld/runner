package com.lightweightai.kernel.context;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MicroCompactorTest {

    private static ConversationMessage msg(MessageRole role, String text) {
        return ConversationMessage.builder().role(role).textContent(text).build();
    }

    private static ConversationMessage toolMsg(String text) {
        return ConversationMessage.builder().role(MessageRole.TOOL).textContent(text).build();
    }

    private static ConversationMessage toolMsgWithMeta(String text, Map<String, Object> meta) {
        return ConversationMessage.builder().role(MessageRole.TOOL).textContent(text).metadata(meta).build();
    }

    @Test
    void toolMessageBelowThresholdUnchanged() {
        MicroCompactor mc = new MicroCompactor(100);
        ConversationMessage tool = toolMsg("short");
        List<ConversationMessage> result = mc.compact(List.of(tool));
        assertEquals(1, result.size());
        assertEquals("short", result.get(0).getTextContent());
    }

    @Test
    void toolMessageExactlyAtThresholdUnchanged() {
        String text = "x".repeat(100);
        MicroCompactor mc = new MicroCompactor(100);
        List<ConversationMessage> result = mc.compact(List.of(toolMsg(text)));
        assertEquals(text, result.get(0).getTextContent());
    }

    @Test
    void toolMessageOverThresholdTruncated() {
        String text = "A".repeat(50) + "B".repeat(200) + "C".repeat(50);
        MicroCompactor mc = new MicroCompactor(100, 25);
        List<ConversationMessage> result = mc.compact(List.of(toolMsg(text)));

        String truncated = result.get(0).getTextContent();
        assertTrue(truncated.startsWith("A".repeat(25)));
        assertTrue(truncated.endsWith("C".repeat(25)));
        assertTrue(truncated.contains("[...snipped"));
        int snippedCount = text.length() - 50;
        assertTrue(truncated.contains("snipped " + snippedCount + " chars"));
    }

    @Test
    void nonToolMessagesNeverTruncated() {
        String longText = "x".repeat(500);
        MicroCompactor mc = new MicroCompactor(100);

        List<ConversationMessage> input = List.of(
                msg(MessageRole.USER, longText),
                msg(MessageRole.ASSISTANT, longText),
                msg(MessageRole.SYSTEM, longText)
        );

        List<ConversationMessage> result = mc.compact(input);
        assertEquals(3, result.size());
        for (ConversationMessage m : result) {
            assertEquals(longText, m.getTextContent());
        }
    }

    @Test
    void metadataPreservedOnTruncation() {
        String text = "x".repeat(500);
        Map<String, Object> meta = Map.of("toolCallId", "tc-123");
        MicroCompactor mc = new MicroCompactor(100, 20);

        List<ConversationMessage> result = mc.compact(List.of(toolMsgWithMeta(text, meta)));
        assertEquals("tc-123", result.get(0).getMetadata().get("toolCallId"));
        assertTrue(result.get(0).getTextContent().contains("[...snipped"));
    }

    @Test
    void multipleToolMessagesProcessedIndependently() {
        MicroCompactor mc = new MicroCompactor(100, 10);
        ConversationMessage short1 = toolMsg("short");
        ConversationMessage long1 = toolMsg("x".repeat(200));
        ConversationMessage long2 = toolMsg("y".repeat(300));

        List<ConversationMessage> result = mc.compact(List.of(short1, long1, long2));
        assertEquals(3, result.size());
        assertEquals("short", result.get(0).getTextContent());
        assertTrue(result.get(1).getTextContent().contains("[...snipped"));
        assertTrue(result.get(2).getTextContent().contains("[...snipped"));
    }

    @Test
    void defaultKeepCharsCalculation() {
        MicroCompactor mc = new MicroCompactor(1000);
        String text = "A".repeat(500) + "B".repeat(1000) + "C".repeat(500);
        List<ConversationMessage> result = mc.compact(List.of(toolMsg(text)));
        String truncated = result.get(0).getTextContent();
        assertTrue(truncated.startsWith("A".repeat(200)));
        assertTrue(truncated.endsWith("C".repeat(200)));
    }

    @Test
    void defaultKeepCharsCappedAt200() {
        MicroCompactor mc = new MicroCompactor(10000);
        String text = "x".repeat(20000);
        List<ConversationMessage> result = mc.compact(List.of(toolMsg(text)));
        String truncated = result.get(0).getTextContent();
        assertTrue(truncated.contains("[...snipped"));
        String head = truncated.substring(0, 200);
        assertEquals("x".repeat(200), head);
    }

    @Test
    void mixedMessageTypes() {
        MicroCompactor mc = new MicroCompactor(50, 10);
        List<ConversationMessage> input = List.of(
                msg(MessageRole.USER, "hi"),
                toolMsg("x".repeat(100)),
                msg(MessageRole.ASSISTANT, "reply"),
                toolMsg("short"),
                msg(MessageRole.SYSTEM, "sys")
        );

        List<ConversationMessage> result = mc.compact(input);
        assertEquals(5, result.size());
        assertEquals("hi", result.get(0).getTextContent());
        assertTrue(result.get(1).getTextContent().contains("[...snipped"));
        assertEquals("reply", result.get(2).getTextContent());
        assertEquals("short", result.get(3).getTextContent());
        assertEquals("sys", result.get(4).getTextContent());
    }
}
