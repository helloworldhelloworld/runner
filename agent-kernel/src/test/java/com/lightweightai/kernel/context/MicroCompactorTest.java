package com.lightweightai.kernel.context;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MicroCompactor - TOOL 消息截断压缩")
class MicroCompactorTest {

    private ConversationMessage msg(MessageRole role, String text) {
        return ConversationMessage.builder().role(role).textContent(text).build();
    }

    private ConversationMessage toolMsg(String text) {
        return msg(MessageRole.TOOL, text);
    }

    private ConversationMessage toolMsgWithMeta(String text, String key, Object value) {
        return ConversationMessage.builder()
                .role(MessageRole.TOOL)
                .textContent(text)
                .addMetadata(key, value)
                .build();
    }

    @Nested
    @DisplayName("截断行为")
    class TruncationTests {

        @Test
        @DisplayName("超过 maxChars 的 TOOL 消息被截断，结果包含首尾 keepChars 和 snip 标记")
        void truncatesLargeToolMessage() {
            MicroCompactor micro = new MicroCompactor(100, 20);
            String largeText = "A".repeat(200);

            List<ConversationMessage> result = micro.compact(List.of(toolMsg(largeText)));

            assertEquals(1, result.size());
            String content = result.get(0).getTextContent();
            assertTrue(content.startsWith("A".repeat(20)));
            assertTrue(content.endsWith("A".repeat(20)));
            assertTrue(content.contains("[...snipped 160 chars...]"));
        }

        @Test
        @DisplayName("snip 标记中的字符数 = 原文长度 - 2*keepChars")
        void snippedCountIsAccurate() {
            MicroCompactor micro = new MicroCompactor(50, 10);
            String text = "x".repeat(100);

            List<ConversationMessage> result = micro.compact(List.of(toolMsg(text)));

            String content = result.get(0).getTextContent();
            assertTrue(content.contains("[...snipped 80 chars...]"),
                    "Expected snipped count 80, got: " + content);
        }

        @Test
        @DisplayName("默认 keepChars = min(200, maxChars/4)")
        void defaultKeepCharsCalculation() {
            MicroCompactor small = new MicroCompactor(400);
            String text = "z".repeat(1000);

            List<ConversationMessage> result = small.compact(List.of(toolMsg(text)));
            String content = result.get(0).getTextContent();
            // keepChars = min(200, 400/4) = 100, snipped = 1000 - 200 = 800
            assertTrue(content.contains("[...snipped 800 chars...]"),
                    "Default keepChars should be 100 for maxChars=400, got: " + content);
        }

        @Test
        @DisplayName("默认 keepChars 对大 maxChars 限制为 200")
        void defaultKeepCharsCappedAt200() {
            MicroCompactor large = new MicroCompactor(2000);
            String text = "w".repeat(3000);

            List<ConversationMessage> result = large.compact(List.of(toolMsg(text)));
            String content = result.get(0).getTextContent();
            // keepChars = min(200, 2000/4) = 200, snipped = 3000 - 400 = 2600
            assertTrue(content.contains("[...snipped 2600 chars...]"),
                    "Default keepChars should be capped at 200, got: " + content);
        }
    }

    @Nested
    @DisplayName("过滤逻辑")
    class FilteringTests {

        @Test
        @DisplayName("小于阈值的 TOOL 消息原样保留")
        void keepsSmallToolMessages() {
            MicroCompactor micro = new MicroCompactor(500);
            ConversationMessage small = toolMsg("short result");

            List<ConversationMessage> result = micro.compact(List.of(small));

            assertEquals(1, result.size());
            assertEquals("short result", result.get(0).getTextContent());
        }

        @Test
        @DisplayName("恰好等于 maxChars 的 TOOL 消息不截断")
        void doesNotTruncateAtExactBoundary() {
            MicroCompactor micro = new MicroCompactor(100);
            String exact = "a".repeat(100);

            List<ConversationMessage> result = micro.compact(List.of(toolMsg(exact)));

            assertEquals(exact, result.get(0).getTextContent());
        }

        @Test
        @DisplayName("非 TOOL 角色的消息永远不受影响")
        void nonToolMessagesUnchanged() {
            MicroCompactor micro = new MicroCompactor(10);
            String largeText = "B".repeat(1000);

            List<ConversationMessage> messages = List.of(
                    msg(MessageRole.USER, largeText),
                    msg(MessageRole.ASSISTANT, largeText),
                    msg(MessageRole.SYSTEM, largeText)
            );

            List<ConversationMessage> result = micro.compact(messages);

            assertEquals(3, result.size());
            for (ConversationMessage m : result) {
                assertEquals(largeText, m.getTextContent());
            }
        }

        @Test
        @DisplayName("null textContent 的 TOOL 消息不截断")
        void nullTextContentToolMessage() {
            MicroCompactor micro = new MicroCompactor(10);
            // textContent returns empty string when there's no TextContent block
            // Build a TOOL message with empty text
            ConversationMessage emptyTool = toolMsg("");

            List<ConversationMessage> result = micro.compact(List.of(emptyTool));
            assertEquals(1, result.size());
        }
    }

    @Nested
    @DisplayName("元数据保留")
    class MetadataPreservationTests {

        @Test
        @DisplayName("截断后保留原始 metadata")
        void preservesMetadataAfterTruncation() {
            MicroCompactor micro = new MicroCompactor(50, 10);
            ConversationMessage toolWithMeta = toolMsgWithMeta(
                    "x".repeat(200), "toolCallId", "tc-123");

            List<ConversationMessage> result = micro.compact(List.of(toolWithMeta));

            assertEquals(1, result.size());
            assertEquals("tc-123", result.get(0).getMetadata().get("toolCallId"));
            assertTrue(result.get(0).getTextContent().contains("[...snipped"));
        }
    }

    @Nested
    @DisplayName("混合消息场景")
    class MixedMessageTests {

        @Test
        @DisplayName("多条消息中只截断超标的 TOOL 消息，其余不动")
        void onlyTruncatesOversizedToolMessages() {
            MicroCompactor micro = new MicroCompactor(100, 10);

            List<ConversationMessage> messages = List.of(
                    msg(MessageRole.USER, "question"),
                    toolMsg("small"),                   // 不截断
                    toolMsg("x".repeat(200)),           // 截断
                    msg(MessageRole.ASSISTANT, "answer"),
                    toolMsg("y".repeat(300))            // 截断
            );

            List<ConversationMessage> result = micro.compact(messages);

            assertEquals(5, result.size());
            assertEquals("question", result.get(0).getTextContent());
            assertEquals("small", result.get(1).getTextContent());
            assertTrue(result.get(2).getTextContent().contains("[...snipped"));
            assertEquals("answer", result.get(3).getTextContent());
            assertTrue(result.get(4).getTextContent().contains("[...snipped"));
        }

        @Test
        @DisplayName("空列表输入返回空列表")
        void emptyInputReturnsEmpty() {
            MicroCompactor micro = new MicroCompactor(100);
            List<ConversationMessage> result = micro.compact(List.of());
            assertTrue(result.isEmpty());
        }
    }
}
