package com.lightweightai.kernel.context;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MicroCompactor - 边界条件与元数据保留")
class MicroCompactorEdgeCaseTest {

    private ConversationMessage toolMsg(String text) {
        return ConversationMessage.builder().role(MessageRole.TOOL).textContent(text).build();
    }

    private ConversationMessage toolMsgWithMeta(String text, Map<String, Object> meta) {
        return ConversationMessage.builder().role(MessageRole.TOOL).textContent(text).metadata(meta).build();
    }

    @Test
    @DisplayName("长度恰好等于阈值的消息不截断")
    void exactThresholdNotTruncated() {
        String exact = "a".repeat(500);
        MicroCompactor micro = new MicroCompactor(500);

        List<ConversationMessage> result = micro.compact(List.of(toolMsg(exact)));
        assertEquals(exact, result.get(0).getTextContent());
    }

    @Test
    @DisplayName("长度为阈值+1 的消息被截断")
    void oneOverThresholdIsTruncated() {
        String over = "b".repeat(501);
        MicroCompactor micro = new MicroCompactor(500);

        List<ConversationMessage> result = micro.compact(List.of(toolMsg(over)));
        assertTrue(result.get(0).getTextContent().contains("[...snipped"));
    }

    @Test
    @DisplayName("非 TOOL 消息不受影响")
    void nonToolMessagesUnaffected() {
        String large = "c".repeat(5000);
        ConversationMessage user = ConversationMessage.builder()
                .role(MessageRole.USER).textContent(large).build();
        ConversationMessage assistant = ConversationMessage.builder()
                .role(MessageRole.ASSISTANT).textContent(large).build();
        ConversationMessage system = ConversationMessage.builder()
                .role(MessageRole.SYSTEM).textContent(large).build();

        MicroCompactor micro = new MicroCompactor(500);
        List<ConversationMessage> result = micro.compact(List.of(user, assistant, system));

        assertEquals(large, result.get(0).getTextContent());
        assertEquals(large, result.get(1).getTextContent());
        assertEquals(large, result.get(2).getTextContent());
    }

    @Test
    @DisplayName("metadata 在截断后保留")
    void metadataPreservedAfterTruncation() {
        String large = "d".repeat(3000);
        Map<String, Object> meta = Map.of("toolCallId", "tc-123", "source", "mcp");

        MicroCompactor micro = new MicroCompactor(500);
        List<ConversationMessage> result = micro.compact(
                List.of(toolMsgWithMeta(large, meta)));

        Map<String, Object> resultMeta = result.get(0).getMetadata();
        assertEquals("tc-123", resultMeta.get("toolCallId"));
        assertEquals("mcp", resultMeta.get("source"));
    }

    @Test
    @DisplayName("snip marker 包含实际删减字符数")
    void snipMarkerContainsCorrectCharCount() {
        String content = "x".repeat(1000);
        MicroCompactor micro = new MicroCompactor(500, 100);

        List<ConversationMessage> result = micro.compact(List.of(toolMsg(content)));
        String truncated = result.get(0).getTextContent();

        assertTrue(truncated.contains("[...snipped 800 chars...]"));
    }

    @Test
    @DisplayName("自定义 keepChars 参数生效")
    void customKeepCharsRespected() {
        String content = "abcdefghij".repeat(200); // 2000 chars
        MicroCompactor micro = new MicroCompactor(500, 50);

        List<ConversationMessage> result = micro.compact(List.of(toolMsg(content)));
        String truncated = result.get(0).getTextContent();

        assertTrue(truncated.startsWith(content.substring(0, 50)));
        assertTrue(truncated.endsWith(content.substring(content.length() - 50)));
    }

    @Test
    @DisplayName("textContent 为 null 的 TOOL 消息不截断")
    void nullTextContentNotTruncated() {
        ConversationMessage msg = ConversationMessage.builder()
                .role(MessageRole.TOOL)
                .addContent(new com.lightweightai.kernel.llm.ImageContent("base64", "image/png"))
                .build();

        MicroCompactor micro = new MicroCompactor(500);
        List<ConversationMessage> result = micro.compact(List.of(msg));
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("默认 keepChars = min(200, maxChars/4)")
    void defaultKeepCharsCalculation() {
        // maxChars=100 → keepChars = min(200, 25) = 25
        // Content = 200 chars, keepChars = 25
        String content = "z".repeat(200);
        MicroCompactor micro = new MicroCompactor(100);

        List<ConversationMessage> result = micro.compact(List.of(toolMsg(content)));
        String truncated = result.get(0).getTextContent();

        assertTrue(truncated.contains("[...snipped 150 chars...]"));
    }
}
