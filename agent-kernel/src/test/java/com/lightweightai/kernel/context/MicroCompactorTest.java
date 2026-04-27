package com.lightweightai.kernel.context;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MicroCompactor - 单元测试：截断逻辑与不变量")
class MicroCompactorTest {

    private ConversationMessage toolMsg(String text) {
        return ConversationMessage.builder().role(MessageRole.TOOL).textContent(text).build();
    }

    private ConversationMessage toolMsgWithMeta(String text, Map<String, Object> metadata) {
        return ConversationMessage.builder().role(MessageRole.TOOL).textContent(text).metadata(metadata).build();
    }

    private ConversationMessage userMsg(String text) {
        return ConversationMessage.builder().role(MessageRole.USER).textContent(text).build();
    }

    @Test
    @DisplayName("空消息列表返回空列表")
    void emptyListReturnsEmpty() {
        MicroCompactor micro = new MicroCompactor(100);
        assertTrue(micro.compact(List.of()).isEmpty());
    }

    @Test
    @DisplayName("TOOL 消息恰好等于 maxChars 时不截断")
    void exactMaxCharsNotTruncated() {
        String exact = "x".repeat(500);
        List<ConversationMessage> result = new MicroCompactor(500).compact(List.of(toolMsg(exact)));
        assertEquals(exact, result.get(0).getTextContent());
    }

    @Test
    @DisplayName("TOOL 消息超过 maxChars 时被截断，包含 snip marker")
    void overMaxCharsTruncated() {
        String large = "a".repeat(1000);
        List<ConversationMessage> result = new MicroCompactor(500).compact(List.of(toolMsg(large)));

        String truncated = result.get(0).getTextContent();
        assertTrue(truncated.length() < 1000);
        assertTrue(truncated.contains("[...snipped"));
        assertTrue(truncated.startsWith("a")); // head preserved
        assertTrue(truncated.endsWith("a")); // tail preserved
    }

    @Test
    @DisplayName("截断后保留首尾各 keepChars 字符")
    void preservesHeadAndTail() {
        String text = "HEAD" + "x".repeat(2000) + "TAIL";
        MicroCompactor micro = new MicroCompactor(100, 4); // keepChars=4

        List<ConversationMessage> result = micro.compact(List.of(toolMsg(text)));
        String truncated = result.get(0).getTextContent();

        assertTrue(truncated.startsWith("HEAD"), "Head should be preserved");
        assertTrue(truncated.endsWith("TAIL"), "Tail should be preserved");
    }

    @Test
    @DisplayName("snip marker 包含正确的被截断字符数")
    void snipMarkerShowsCorrectCount() {
        String text = "x".repeat(1000);
        MicroCompactor micro = new MicroCompactor(500, 100); // keepChars=100

        List<ConversationMessage> result = micro.compact(List.of(toolMsg(text)));
        String truncated = result.get(0).getTextContent();

        // 1000 - (100*2) = 800 chars snipped
        assertTrue(truncated.contains("[...snipped 800 chars...]"));
    }

    @Test
    @DisplayName("非 TOOL 消息不受截断影响")
    void nonToolMessagesUnaffected() {
        String largeUserText = "u".repeat(5000);
        List<ConversationMessage> result = new MicroCompactor(100).compact(
                List.of(userMsg(largeUserText)));

        assertEquals(largeUserText, result.get(0).getTextContent());
    }

    @Test
    @DisplayName("TOOL 消息的 metadata 在截断后保留")
    void metadataPreservedAfterTruncation() {
        Map<String, Object> meta = Map.of("tool_use_id", "call_123", "is_error", false);
        String large = "x".repeat(2000);
        List<ConversationMessage> result = new MicroCompactor(500).compact(
                List.of(toolMsgWithMeta(large, meta)));

        Map<String, Object> resultMeta = result.get(0).getMetadata();
        assertEquals("call_123", resultMeta.get("tool_use_id"));
        assertEquals(false, resultMeta.get("is_error"));
    }

    @Test
    @DisplayName("TOOL 消息 textContent 为空字符串时不截断")
    void emptyTextContentNotTruncated() {
        ConversationMessage emptyContent = ConversationMessage.builder()
                .role(MessageRole.TOOL)
                .textContent("")
                .build();

        List<ConversationMessage> result = new MicroCompactor(100).compact(List.of(emptyContent));
        assertEquals(1, result.size());
        assertEquals("", result.get(0).getTextContent());
    }

    @Test
    @DisplayName("默认 keepChars 为 min(200, maxChars/4)")
    void defaultKeepCharsCalculation() {
        // maxChars=1000 → keepChars = min(200, 250) = 200
        String text = "x".repeat(2000);
        MicroCompactor micro = new MicroCompactor(1000);
        List<ConversationMessage> result = micro.compact(List.of(toolMsg(text)));
        String truncated = result.get(0).getTextContent();

        // snipped count = 2000 - (200*2) = 1600
        assertTrue(truncated.contains("[...snipped 1600 chars...]"));
    }

    @Test
    @DisplayName("多个 TOOL 消息各自独立截断")
    void multipleToolMessagesEachTruncatedIndependently() {
        List<ConversationMessage> msgs = List.of(
                toolMsg("a".repeat(1000)),
                toolMsg("short"),
                toolMsg("b".repeat(1000))
        );

        List<ConversationMessage> result = new MicroCompactor(500).compact(msgs);

        assertTrue(result.get(0).getTextContent().contains("[...snipped"));
        assertEquals("short", result.get(1).getTextContent());
        assertTrue(result.get(2).getTextContent().contains("[...snipped"));
    }
}
