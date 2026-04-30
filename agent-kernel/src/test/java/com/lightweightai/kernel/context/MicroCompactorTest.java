package com.lightweightai.kernel.context;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MicroCompactor — TOOL 消息截断（边界用例）")
class MicroCompactorTest {

    private ConversationMessage msg(MessageRole role, String text) {
        return ConversationMessage.builder().role(role).textContent(text).build();
    }

    private ConversationMessage toolMsg(String text) {
        return msg(MessageRole.TOOL, text);
    }

    @Test
    @DisplayName("刚好等于阈值的 TOOL 消息不截断")
    void exactThresholdNotTruncated() {
        String content = "x".repeat(500);
        MicroCompactor micro = new MicroCompactor(500);
        List<ConversationMessage> result = micro.compact(List.of(toolMsg(content)));

        assertEquals(content, result.get(0).getTextContent());
    }

    @Test
    @DisplayName("超过阈值 1 字符也要截断")
    void oneOverThresholdIsTruncated() {
        String content = "x".repeat(501);
        MicroCompactor micro = new MicroCompactor(500);
        List<ConversationMessage> result = micro.compact(List.of(toolMsg(content)));

        assertTrue(result.get(0).getTextContent().length() < 501);
        assertTrue(result.get(0).getTextContent().contains("[...snipped"));
    }

    @Test
    @DisplayName("截断后保留首尾各 keepChars 字符")
    void preservesHeadAndTail() {
        String head = "HEAD_";
        String tail = "_TAIL";
        String middle = "m".repeat(1000);
        String content = head + middle + tail;

        MicroCompactor micro = new MicroCompactor(100, 5);
        List<ConversationMessage> result = micro.compact(List.of(toolMsg(content)));

        String truncated = result.get(0).getTextContent();
        assertTrue(truncated.startsWith("HEAD_"), "Should preserve head");
        assertTrue(truncated.endsWith("_TAIL"), "Should preserve tail");
        assertTrue(truncated.contains("[...snipped"), "Should have snip marker");
    }

    @Test
    @DisplayName("snip marker 中包含正确的被删除字符数")
    void snipMarkerShowsCorrectCount() {
        String content = "x".repeat(1000);
        int keepChars = 100;
        MicroCompactor micro = new MicroCompactor(500, keepChars);
        List<ConversationMessage> result = micro.compact(List.of(toolMsg(content)));

        int expectedSnipped = 1000 - (keepChars * 2);
        assertTrue(result.get(0).getTextContent().contains("snipped " + expectedSnipped + " chars"));
    }

    @Test
    @DisplayName("非 TOOL 消息（USER/ASSISTANT/SYSTEM）即使超长也不截断")
    void nonToolMessagesNeverTruncated() {
        String longText = "y".repeat(5000);
        List<ConversationMessage> messages = List.of(
                msg(MessageRole.USER, longText),
                msg(MessageRole.ASSISTANT, longText),
                msg(MessageRole.SYSTEM, longText)
        );

        MicroCompactor micro = new MicroCompactor(100);
        List<ConversationMessage> result = micro.compact(messages);

        for (ConversationMessage m : result) {
            assertEquals(5000, m.getTextContent().length(),
                    m.getRole() + " message should not be truncated");
        }
    }

    @Test
    @DisplayName("TOOL 消息 textContent 为 null 时不报错")
    void nullToolContentHandledGracefully() {
        ConversationMessage nullTool = ConversationMessage.builder()
                .role(MessageRole.TOOL)
                .textContent(null)
                .build();

        MicroCompactor micro = new MicroCompactor(100);
        List<ConversationMessage> result = micro.compact(List.of(nullTool));

        assertEquals(1, result.size());
        assertNull(result.get(0).getTextContent());
    }

    @Test
    @DisplayName("空消息列表不报错")
    void emptyListReturnsEmpty() {
        MicroCompactor micro = new MicroCompactor(100);
        List<ConversationMessage> result = micro.compact(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("默认 keepChars 是 min(200, maxChars/4)")
    void defaultKeepCharsCalculation() {
        String content = "x".repeat(2000);

        MicroCompactor micro = new MicroCompactor(400);
        List<ConversationMessage> result = micro.compact(List.of(toolMsg(content)));

        String truncated = result.get(0).getTextContent();
        int expectedKeep = Math.min(200, 400 / 4); // = 100
        int expectedSnipped = 2000 - (expectedKeep * 2);
        assertTrue(truncated.contains("snipped " + expectedSnipped + " chars"));
    }
}
