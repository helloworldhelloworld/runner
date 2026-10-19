package com.lightweightai.kernel.context;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MicroCompactor 边界条件测试。
 *
 * 已有 MicroCompactorTest 覆盖基本截断。
 * 此测试补充：非 TOOL 不受影响、恰好等于阈值、自定义 keepChars。
 */
@DisplayName("MicroCompactor - 边界条件")
class MicroCompactorEdgeCaseTest {

    private ConversationMessage msg(MessageRole role, String text) {
        return ConversationMessage.builder().role(role).textContent(text).build();
    }

    @Test
    @DisplayName("USER 消息不被截断，即使超长")
    void userMessageNotTruncated() {
        MicroCompactor compactor = new MicroCompactor(50);
        String longText = "a".repeat(1000);
        List<ConversationMessage> msgs = new ArrayList<>(List.of(
                msg(MessageRole.USER, longText)
        ));

        List<ConversationMessage> result = compactor.compact(msgs);
        assertEquals(1, result.size());
        assertEquals(1000, result.get(0).getTextContent().length(),
                "USER message must not be truncated");
    }

    @Test
    @DisplayName("ASSISTANT 消息不被截断，即使超长")
    void assistantMessageNotTruncated() {
        MicroCompactor compactor = new MicroCompactor(50);
        String longText = "b".repeat(500);
        List<ConversationMessage> msgs = new ArrayList<>(List.of(
                msg(MessageRole.ASSISTANT, longText)
        ));

        List<ConversationMessage> result = compactor.compact(msgs);
        assertEquals(500, result.get(0).getTextContent().length());
    }

    @Test
    @DisplayName("TOOL 消息恰好等于 maxChars 时不截断")
    void toolMessageExactlyAtThresholdNotTruncated() {
        MicroCompactor compactor = new MicroCompactor(100);
        String text = "x".repeat(100);
        List<ConversationMessage> msgs = new ArrayList<>(List.of(
                msg(MessageRole.TOOL, text)
        ));

        List<ConversationMessage> result = compactor.compact(msgs);
        assertEquals(100, result.get(0).getTextContent().length(),
                "Exactly at threshold should not be truncated");
    }

    @Test
    @DisplayName("TOOL 消息超 maxChars 时被截断，含 snipped 标记")
    void toolMessageOverThresholdTruncated() {
        MicroCompactor compactor = new MicroCompactor(100, 20);
        String text = "x".repeat(200);
        List<ConversationMessage> msgs = new ArrayList<>(List.of(
                msg(MessageRole.TOOL, text)
        ));

        List<ConversationMessage> result = compactor.compact(msgs);
        String truncated = result.get(0).getTextContent();
        assertTrue(truncated.length() < 200);
        assertTrue(truncated.contains("[...snipped"));
        assertTrue(truncated.contains("chars...]"));
        assertTrue(truncated.startsWith("x".repeat(20)),
                "Should start with first keepChars characters");
        assertTrue(truncated.endsWith("x".repeat(20)),
                "Should end with last keepChars characters");
    }

    @Test
    @DisplayName("metadata 在截断后保留")
    void metadataPreservedAfterTruncation() {
        MicroCompactor compactor = new MicroCompactor(50, 10);
        ConversationMessage toolMsg = ConversationMessage.builder()
                .role(MessageRole.TOOL)
                .textContent("x".repeat(200))
                .addMetadata("tool_use_id", "use-1")
                .addMetadata("is_error", false)
                .build();

        List<ConversationMessage> result = compactor.compact(new ArrayList<>(List.of(toolMsg)));
        assertEquals("use-1", result.get(0).getMetadata().get("tool_use_id"),
                "Metadata must be preserved through truncation");
    }

    @Test
    @DisplayName("空列表返回空列表")
    void emptyListReturnsEmpty() {
        MicroCompactor compactor = new MicroCompactor(100);
        List<ConversationMessage> result = compactor.compact(new ArrayList<>());
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("默认 keepChars 为 min(200, maxChars/4)")
    void defaultKeepCharsCalculation() {
        // maxChars=100 → keepChars=min(200, 25) = 25
        MicroCompactor compactor = new MicroCompactor(100);
        String text = "a".repeat(200);
        List<ConversationMessage> msgs = new ArrayList<>(List.of(
                msg(MessageRole.TOOL, text)
        ));

        List<ConversationMessage> result = compactor.compact(msgs);
        String truncated = result.get(0).getTextContent();
        // First 25 chars + snipped marker + last 25 chars
        assertTrue(truncated.startsWith("a".repeat(25)));
        assertTrue(truncated.endsWith("a".repeat(25)));
    }
}
