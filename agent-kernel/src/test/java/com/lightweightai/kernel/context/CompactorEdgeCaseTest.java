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

@DisplayName("Compactor 边界情况")
class CompactorEdgeCaseTest {

    private ConversationMessage msg(MessageRole role, String text) {
        return ConversationMessage.builder().role(role).textContent(text).build();
    }

    private ConversationMessage toolMsg(String text) {
        return ConversationMessage.builder().role(MessageRole.TOOL).textContent(text).build();
    }

    private ConversationMessage toolMsgWithMeta(String text, Map<String, Object> metadata) {
        return ConversationMessage.builder()
                .role(MessageRole.TOOL)
                .textContent(text)
                .metadata(metadata)
                .build();
    }

    @Nested
    @DisplayName("MicroCompactor 边界")
    class MicroEdgeCases {

        @Test
        @DisplayName("刚好等于阈值的 TOOL 消息不被截断")
        void exactThresholdShouldNotTruncate() {
            String exactContent = "a".repeat(500);
            List<ConversationMessage> messages = List.of(toolMsg(exactContent));

            MicroCompactor micro = new MicroCompactor(500);
            List<ConversationMessage> result = micro.compact(messages);

            assertEquals(exactContent, result.get(0).getTextContent());
        }

        @Test
        @DisplayName("超过阈值一个字符的 TOOL 消息被截断")
        void oneOverThresholdShouldTruncate() {
            String content = "b".repeat(501);
            List<ConversationMessage> messages = List.of(toolMsg(content));

            MicroCompactor micro = new MicroCompactor(500);
            List<ConversationMessage> result = micro.compact(messages);

            assertTrue(result.get(0).getTextContent().contains("[...snipped"));
        }

        @Test
        @DisplayName("非 TOOL 角色的大消息不被截断")
        void userMessageShouldNotBeTruncated() {
            String large = "c".repeat(5000);
            List<ConversationMessage> messages = List.of(
                    msg(MessageRole.USER, large),
                    msg(MessageRole.ASSISTANT, large),
                    msg(MessageRole.SYSTEM, large)
            );

            MicroCompactor micro = new MicroCompactor(500);
            List<ConversationMessage> result = micro.compact(messages);

            assertEquals(3, result.size());
            for (ConversationMessage m : result) {
                assertEquals(5000, m.getTextContent().length());
            }
        }

        @Test
        @DisplayName("TOOL 消息 textContent 为空字符串时跳过截断")
        void emptyTextContentShouldBeSkipped() {
            ConversationMessage emptyToolMsg = ConversationMessage.builder()
                    .role(MessageRole.TOOL)
                    .textContent("")
                    .build();
            List<ConversationMessage> messages = List.of(emptyToolMsg);

            MicroCompactor micro = new MicroCompactor(100);
            List<ConversationMessage> result = micro.compact(messages);

            assertEquals(1, result.size());
            assertEquals("", result.get(0).getTextContent());
        }

        @Test
        @DisplayName("截断后保留首尾各 keepChars 字符")
        void truncateShouldPreserveHeadAndTail() {
            String content = "HEAD" + "x".repeat(2000) + "TAIL";
            List<ConversationMessage> messages = List.of(toolMsg(content));

            MicroCompactor micro = new MicroCompactor(100, 4);
            List<ConversationMessage> result = micro.compact(messages);

            String truncated = result.get(0).getTextContent();
            assertTrue(truncated.startsWith("HEAD"));
            assertTrue(truncated.endsWith("TAIL"));
            assertTrue(truncated.contains("[...snipped"));
        }

        @Test
        @DisplayName("snipped 标记中的字符数正确")
        void snippedCharCountShouldBeAccurate() {
            int totalLen = 1000;
            int keepChars = 100;
            String content = "z".repeat(totalLen);

            MicroCompactor micro = new MicroCompactor(500, keepChars);
            List<ConversationMessage> result = micro.compact(List.of(toolMsg(content)));

            String truncated = result.get(0).getTextContent();
            int expectedSnipped = totalLen - (keepChars * 2);
            assertTrue(truncated.contains("[...snipped " + expectedSnipped + " chars...]"),
                    "Expected snipped count " + expectedSnipped + " in: " + truncated);
        }

        @Test
        @DisplayName("metadata 在截断后保留")
        void metadataShouldBePreservedAfterTruncation() {
            Map<String, Object> meta = Map.of("toolUseId", "call_123", "source", "mcp");
            String largeContent = "d".repeat(2000);
            List<ConversationMessage> messages = List.of(toolMsgWithMeta(largeContent, meta));

            MicroCompactor micro = new MicroCompactor(500);
            List<ConversationMessage> result = micro.compact(messages);

            Map<String, Object> resultMeta = result.get(0).getMetadata();
            assertNotNull(resultMeta);
            assertEquals("call_123", resultMeta.get("toolUseId"));
            assertEquals("mcp", resultMeta.get("source"));
        }

        @Test
        @DisplayName("空列表输入返回空列表")
        void emptyInputShouldReturnEmpty() {
            MicroCompactor micro = new MicroCompactor(500);
            List<ConversationMessage> result = micro.compact(List.of());

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("SnipCompactor 边界")
    class SnipEdgeCases {

        @Test
        @DisplayName("全部为 USER 消息（无 TOOL）时不删除任何消息")
        void noToolMessagesShouldPassThrough() {
            List<ConversationMessage> messages = new ArrayList<>(List.of(
                    msg(MessageRole.USER, "q1"),
                    msg(MessageRole.ASSISTANT, "a1"),
                    msg(MessageRole.USER, "q2"),
                    msg(MessageRole.ASSISTANT, "a2"),
                    msg(MessageRole.USER, "q3"),
                    msg(MessageRole.ASSISTANT, "a3")
            ));

            SnipCompactor snip = new SnipCompactor(1);
            List<ConversationMessage> result = snip.compact(messages);

            assertEquals(6, result.size());
        }

        @Test
        @DisplayName("SYSTEM 消息永远保留")
        void systemMessageShouldAlwaysBeKept() {
            List<ConversationMessage> messages = new ArrayList<>(List.of(
                    msg(MessageRole.SYSTEM, "you are an AI"),
                    msg(MessageRole.USER, "q1"),
                    toolMsg("old tool"),
                    msg(MessageRole.ASSISTANT, "a1"),
                    msg(MessageRole.USER, "q2"),
                    msg(MessageRole.ASSISTANT, "a2"),
                    msg(MessageRole.USER, "q3"),
                    msg(MessageRole.ASSISTANT, "a3")
            ));

            SnipCompactor snip = new SnipCompactor(1);
            List<ConversationMessage> result = snip.compact(messages);

            assertTrue(result.stream()
                    .anyMatch(m -> m.getRole() == MessageRole.SYSTEM
                            && "you are an AI".equals(m.getTextContent())));
        }

        @Test
        @DisplayName("keepRecentRounds 大于总轮数时保留所有 TOOL 消息")
        void keepMoreThanTotalRoundsShouldRetainAll() {
            List<ConversationMessage> messages = new ArrayList<>(List.of(
                    msg(MessageRole.USER, "q1"),
                    toolMsg("tool1"),
                    msg(MessageRole.ASSISTANT, "a1"),
                    msg(MessageRole.USER, "q2"),
                    toolMsg("tool2"),
                    msg(MessageRole.ASSISTANT, "a2")
            ));

            SnipCompactor snip = new SnipCompactor(10);
            List<ConversationMessage> result = snip.compact(messages);

            assertEquals(6, result.size());
            long toolCount = result.stream()
                    .filter(m -> m.getRole() == MessageRole.TOOL)
                    .count();
            assertEquals(2, toolCount);
        }

        @Test
        @DisplayName("空列表输入返回空列表")
        void emptyInputShouldReturnEmpty() {
            SnipCompactor snip = new SnipCompactor(3);
            List<ConversationMessage> result = snip.compact(List.of());

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("单轮对话 + keepRecentRounds=1 → 保留所有")
        void singleRoundWithKeepOneShouldRetainAll() {
            List<ConversationMessage> messages = new ArrayList<>(List.of(
                    msg(MessageRole.USER, "q"),
                    toolMsg("result"),
                    msg(MessageRole.ASSISTANT, "a")
            ));

            SnipCompactor snip = new SnipCompactor(1);
            List<ConversationMessage> result = snip.compact(messages);

            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("连续多个 TOOL 消息在保护区内全部保留")
        void multipleToolsInRecentRoundShouldAllBeKept() {
            List<ConversationMessage> messages = new ArrayList<>(List.of(
                    msg(MessageRole.USER, "q1"),
                    toolMsg("old"),
                    msg(MessageRole.ASSISTANT, "a1"),
                    msg(MessageRole.USER, "q2"),
                    toolMsg("keep1"),
                    toolMsg("keep2"),
                    toolMsg("keep3"),
                    msg(MessageRole.ASSISTANT, "a2")
            ));

            SnipCompactor snip = new SnipCompactor(1);
            List<ConversationMessage> result = snip.compact(messages);

            long toolCount = result.stream()
                    .filter(m -> m.getRole() == MessageRole.TOOL)
                    .count();
            assertEquals(3, toolCount, "All 3 TOOL messages in recent round should be kept");
        }
    }

    @Nested
    @DisplayName("CompactionChain 边界")
    class ChainEdgeCases {

        @Test
        @DisplayName("空 stages 列表时原样返回")
        void emptyChainShouldPassThrough() {
            List<ConversationMessage> messages = List.of(
                    msg(MessageRole.USER, "hello"),
                    toolMsg("big tool result " + "x".repeat(1000))
            );

            CompactionChain chain = new CompactionChain();
            List<ConversationMessage> result = chain.compact(messages);

            assertEquals(2, result.size());
            assertEquals(messages.get(1).getTextContent(), result.get(1).getTextContent());
        }

        @Test
        @DisplayName("单个 stage 等同直接调用该 compactor")
        void singleStageShouldBehaveLikeDirect() {
            String large = "e".repeat(2000);
            List<ConversationMessage> messages = List.of(toolMsg(large));

            MicroCompactor micro = new MicroCompactor(500);
            CompactionChain chain = new CompactionChain(micro);

            List<ConversationMessage> directResult = micro.compact(messages);
            List<ConversationMessage> chainResult = chain.compact(messages);

            assertEquals(directResult.get(0).getTextContent(), chainResult.get(0).getTextContent());
        }

        @Test
        @DisplayName("stages 按顺序执行：先 snip 再 micro")
        void stagesShouldExecuteInOrder() {
            // 如果先 micro 再 snip: micro 截断后 snip 再删，结果不同
            // 如果先 snip 再 micro: snip 先删，剩下的 micro 截断
            String large = "f".repeat(3000);
            List<ConversationMessage> messages = new ArrayList<>(List.of(
                    msg(MessageRole.USER, "q1"),
                    toolMsg(large),          // 旧 TOOL（应被 snip 删除）
                    msg(MessageRole.ASSISTANT, "a1"),
                    msg(MessageRole.USER, "q2"),
                    toolMsg(large),          // 新 TOOL（保留但应被 micro 截断）
                    msg(MessageRole.ASSISTANT, "a2"),
                    msg(MessageRole.USER, "q3")
            ));

            CompactionChain snipFirst = new CompactionChain(
                    new SnipCompactor(2),
                    new MicroCompactor(500)
            );

            List<ConversationMessage> result = snipFirst.compact(messages);

            // 旧 TOOL (q1 轮) 被 snip 删除
            // 新 TOOL (q2 轮) 被 micro 截断
            long toolCount = result.stream()
                    .filter(m -> m.getRole() == MessageRole.TOOL)
                    .count();
            assertTrue(toolCount <= 1, "Old TOOL should be snipped");

            result.stream()
                    .filter(m -> m.getRole() == MessageRole.TOOL)
                    .forEach(m -> {
                        assertTrue(m.getTextContent().length() < 3000);
                        assertTrue(m.getTextContent().contains("[...snipped"));
                    });
        }
    }
}
