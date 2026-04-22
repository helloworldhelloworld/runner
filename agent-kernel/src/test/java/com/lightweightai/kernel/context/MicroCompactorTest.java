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

@DisplayName("MicroCompactor - TOOL 消息截断压缩器")
class MicroCompactorTest {

    private ConversationMessage msg(MessageRole role, String text) {
        return ConversationMessage.builder().role(role).textContent(text).build();
    }

    private ConversationMessage toolMsg(String text) {
        return ConversationMessage.builder().role(MessageRole.TOOL).textContent(text).build();
    }

    private ConversationMessage toolMsgWithMetadata(String text, Map<String, Object> metadata) {
        return ConversationMessage.builder().role(MessageRole.TOOL).textContent(text).metadata(metadata).build();
    }

    @Nested
    @DisplayName("截断阈值判断")
    class ThresholdTests {

        @Test
        @DisplayName("TOOL 消息内容长度低于 maxChars 时原样保留")
        void toolMessageUnderMaxCharsIsUntouched() {
            List<ConversationMessage> messages = List.of(
                    msg(MessageRole.USER, "q"),
                    toolMsg("short result"),
                    msg(MessageRole.ASSISTANT, "a")
            );

            MicroCompactor micro = new MicroCompactor(500);
            List<ConversationMessage> result = micro.compact(messages);

            assertEquals(3, result.size());
            assertSame(messages.get(1), result.get(1), "Under-threshold TOOL should be the same object");
            assertEquals("short result", result.get(1).getTextContent());
        }

        @Test
        @DisplayName("TOOL 消息恰好等于 maxChars 时不截断")
        void toolMessageExactlyAtMaxCharsNotTruncated() {
            String exactContent = "x".repeat(500);
            List<ConversationMessage> messages = List.of(toolMsg(exactContent));

            MicroCompactor micro = new MicroCompactor(500);
            List<ConversationMessage> result = micro.compact(messages);

            assertEquals(exactContent, result.get(0).getTextContent());
        }

        @Test
        @DisplayName("TOOL 消息超过 maxChars 时被截断并包含 snip 标记")
        void toolMessageOverMaxCharsIsTruncated() {
            String largeContent = "abcdefghij".repeat(500);
            List<ConversationMessage> messages = List.of(toolMsg(largeContent));

            MicroCompactor micro = new MicroCompactor(1000, 100);
            List<ConversationMessage> result = micro.compact(messages);

            String truncated = result.get(0).getTextContent();
            assertTrue(truncated.length() < largeContent.length(), "Truncated content should be shorter");
            assertTrue(truncated.contains("[...snipped"), "Should contain snip marker");
            assertTrue(truncated.contains("chars...]"), "Should contain chars count suffix");
            assertEquals(largeContent.substring(0, 100), truncated.substring(0, 100),
                    "Head should be preserved from original");
            assertEquals(largeContent.substring(largeContent.length() - 100),
                    truncated.substring(truncated.length() - 100),
                    "Tail should be preserved from original");
        }
    }

    @Nested
    @DisplayName("非 TOOL 消息保护")
    class NonToolProtection {

        @Test
        @DisplayName("USER 消息即使超长也不被截断")
        void userMessageNeverTruncated() {
            String longContent = "u".repeat(5000);
            List<ConversationMessage> messages = List.of(msg(MessageRole.USER, longContent));

            MicroCompactor micro = new MicroCompactor(100);
            List<ConversationMessage> result = micro.compact(messages);

            assertEquals(longContent, result.get(0).getTextContent());
            assertEquals(MessageRole.USER, result.get(0).getRole());
        }

        @Test
        @DisplayName("ASSISTANT 消息即使超长也不被截断")
        void assistantMessageNeverTruncated() {
            String longContent = "a".repeat(5000);
            List<ConversationMessage> messages = List.of(msg(MessageRole.ASSISTANT, longContent));

            MicroCompactor micro = new MicroCompactor(100);
            List<ConversationMessage> result = micro.compact(messages);

            assertEquals(longContent, result.get(0).getTextContent());
            assertEquals(MessageRole.ASSISTANT, result.get(0).getRole());
        }

        @Test
        @DisplayName("SYSTEM 消息即使超长也不被截断")
        void systemMessageNeverTruncated() {
            String longContent = "s".repeat(5000);
            List<ConversationMessage> messages = List.of(msg(MessageRole.SYSTEM, longContent));

            MicroCompactor micro = new MicroCompactor(100);
            List<ConversationMessage> result = micro.compact(messages);

            assertEquals(longContent, result.get(0).getTextContent());
            assertEquals(MessageRole.SYSTEM, result.get(0).getRole());
        }
    }

    @Nested
    @DisplayName("边界情况")
    class EdgeCases {

        @Test
        @DisplayName("TOOL 消息 textContent 为短文本时直接通过")
        void toolMessageWithShortContentPassesThrough() {
            ConversationMessage tool = toolMsg("ok");
            List<ConversationMessage> messages = List.of(tool);

            MicroCompactor micro = new MicroCompactor(100);
            List<ConversationMessage> result = micro.compact(messages);

            assertEquals(1, result.size());
            assertSame(tool, result.get(0));
        }

        @Test
        @DisplayName("TOOL 消息 textContent 为空字符串时直接通过")
        void toolMessageWithEmptyContentPassesThrough() {
            ConversationMessage toolWithEmpty = ConversationMessage.builder()
                    .role(MessageRole.TOOL).textContent("").build();
            List<ConversationMessage> messages = List.of(toolWithEmpty);

            MicroCompactor micro = new MicroCompactor(100);
            List<ConversationMessage> result = micro.compact(messages);

            assertEquals(1, result.size());
            assertEquals("", result.get(0).getTextContent());
        }

        @Test
        @DisplayName("空消息列表返回空列表")
        void emptyMessageListReturnsEmpty() {
            MicroCompactor micro = new MicroCompactor(100);
            List<ConversationMessage> result = micro.compact(List.of());
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("多 TOOL 消息混合处理")
    class MixedToolMessages {

        @Test
        @DisplayName("部分 TOOL 被截断，部分保留原样")
        void someTruncatedSomeNot() {
            String small = "small";
            String large = "L".repeat(2000);
            String medium = "m".repeat(300);
            List<ConversationMessage> messages = new ArrayList<>(List.of(
                    msg(MessageRole.USER, "q1"),
                    toolMsg(small),
                    toolMsg(large),
                    toolMsg(medium),
                    msg(MessageRole.ASSISTANT, "a1")
            ));

            MicroCompactor micro = new MicroCompactor(500);
            List<ConversationMessage> result = micro.compact(messages);

            assertEquals(5, result.size());
            assertEquals(small, result.get(1).getTextContent(), "Small TOOL should be unchanged");
            assertTrue(result.get(2).getTextContent().contains("[...snipped"),
                    "Large TOOL should be truncated");
            assertEquals(medium, result.get(3).getTextContent(), "Medium TOOL (under threshold) should be unchanged");
        }
    }

    @Nested
    @DisplayName("keepChars 默认值计算")
    class KeepCharsDefaults {

        @Test
        @DisplayName("单参数构造器：keepChars = min(200, maxChars/4)")
        void defaultKeepCharsCalculation() {
            String content = "C".repeat(10000);

            MicroCompactor micro800 = new MicroCompactor(800);
            List<ConversationMessage> result800 = micro800.compact(List.of(toolMsg(content)));
            String truncated800 = result800.get(0).getTextContent();
            assertEquals(content.substring(0, 200), truncated800.substring(0, 200),
                    "keepChars should be min(200, 800/4=200) = 200");
            assertEquals(content.substring(content.length() - 200),
                    truncated800.substring(truncated800.length() - 200));
            int expectedSnipped800 = 10000 - (200 * 2);
            assertTrue(truncated800.contains("[...snipped " + expectedSnipped800 + " chars...]"));

            MicroCompactor micro100 = new MicroCompactor(100);
            List<ConversationMessage> result100 = micro100.compact(List.of(toolMsg(content)));
            String truncated100 = result100.get(0).getTextContent();
            assertEquals(content.substring(0, 25), truncated100.substring(0, 25),
                    "keepChars should be min(200, 100/4=25) = 25");
            assertEquals(content.substring(content.length() - 25),
                    truncated100.substring(truncated100.length() - 25));
            int expectedSnipped100 = 10000 - (25 * 2);
            assertTrue(truncated100.contains("[...snipped " + expectedSnipped100 + " chars...]"));
        }
    }

    @Nested
    @DisplayName("元数据保留")
    class MetadataPreservation {

        @Test
        @DisplayName("截断后的 TOOL 消息保留原始 metadata")
        void metadataPreservedOnTruncatedMessage() {
            Map<String, Object> meta = Map.of("toolCallId", "tc_123", "name", "bash");
            String largeContent = "D".repeat(5000);
            List<ConversationMessage> messages = List.of(toolMsgWithMetadata(largeContent, meta));

            MicroCompactor micro = new MicroCompactor(500, 100);
            List<ConversationMessage> result = micro.compact(messages);

            assertTrue(result.get(0).getTextContent().contains("[...snipped"),
                    "Message should be truncated");
            Map<String, Object> resultMeta = result.get(0).getMetadata();
            assertEquals("tc_123", resultMeta.get("toolCallId"), "toolCallId metadata should be preserved");
            assertEquals("bash", resultMeta.get("name"), "name metadata should be preserved");
        }
    }

    @Nested
    @DisplayName("截断内容验证")
    class TruncationContentVerification {

        @Test
        @DisplayName("snipped 标记包含正确的字符数")
        void snippedMarkerContainsCorrectCharCount() {
            String content = "X".repeat(1000);
            int keepChars = 50;

            MicroCompactor micro = new MicroCompactor(500, keepChars);
            List<ConversationMessage> result = micro.compact(List.of(toolMsg(content)));

            String truncated = result.get(0).getTextContent();
            int expectedSnipped = 1000 - (keepChars * 2);
            assertTrue(truncated.contains("[...snipped " + expectedSnipped + " chars...]"),
                    "Should report exactly " + expectedSnipped + " snipped chars");
        }
    }
}
