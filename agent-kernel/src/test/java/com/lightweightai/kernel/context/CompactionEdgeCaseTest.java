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

@DisplayName("ContextCompactor 边界条件与链路完整性测试")
class CompactionEdgeCaseTest {

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

    // ==================== MicroCompactor 边界 ====================

    @Nested
    @DisplayName("MicroCompactor 边界条件")
    class MicroBoundaryTests {

        @Test
        @DisplayName("恰好等于 maxChars 不截断")
        void exactlyAtBoundaryNotTruncated() {
            String exact = "x".repeat(500);
            List<ConversationMessage> messages = List.of(toolMsg(exact));

            MicroCompactor micro = new MicroCompactor(500);
            List<ConversationMessage> result = micro.compact(new ArrayList<>(messages));

            assertEquals(exact, result.get(0).getTextContent());
        }

        @Test
        @DisplayName("超过 maxChars 一个字符即截断")
        void oneCharOverBoundaryTruncated() {
            String overByOne = "x".repeat(501);
            List<ConversationMessage> messages = List.of(toolMsg(overByOne));

            MicroCompactor micro = new MicroCompactor(500);
            List<ConversationMessage> result = micro.compact(new ArrayList<>(messages));

            assertTrue(result.get(0).getTextContent().contains("[...snipped"));
        }

        @Test
        @DisplayName("截断后保留首尾各 keepChars 字符")
        void truncationPreservesHeadAndTail() {
            String content = "HEAD" + "x".repeat(992) + "TAIL";
            List<ConversationMessage> messages = List.of(toolMsg(content));

            MicroCompactor micro = new MicroCompactor(100, 4);
            List<ConversationMessage> result = micro.compact(new ArrayList<>(messages));

            String truncated = result.get(0).getTextContent();
            assertTrue(truncated.startsWith("HEAD"), "Should preserve HEAD");
            assertTrue(truncated.endsWith("TAIL"), "Should preserve TAIL");
            assertTrue(truncated.contains("[...snipped"));
        }

        @Test
        @DisplayName("metadata 在截断后保留")
        void metadataPreservedAfterTruncation() {
            String largeContent = "x".repeat(2000);
            Map<String, Object> meta = Map.of("toolCallId", "tc-123", "source", "mcp");
            List<ConversationMessage> messages = List.of(toolMsgWithMeta(largeContent, meta));

            MicroCompactor micro = new MicroCompactor(500);
            List<ConversationMessage> result = micro.compact(new ArrayList<>(messages));

            Map<String, Object> resultMeta = result.get(0).getMetadata();
            assertEquals("tc-123", resultMeta.get("toolCallId"));
            assertEquals("mcp", resultMeta.get("source"));
        }

        @Test
        @DisplayName("非 TOOL 消息即使超大也不截断")
        void nonToolMessagesUnaffected() {
            String largeContent = "y".repeat(5000);
            List<ConversationMessage> messages = List.of(
                    msg(MessageRole.USER, largeContent),
                    msg(MessageRole.ASSISTANT, largeContent),
                    msg(MessageRole.SYSTEM, largeContent)
            );

            MicroCompactor micro = new MicroCompactor(500);
            List<ConversationMessage> result = micro.compact(new ArrayList<>(messages));

            assertEquals(3, result.size());
            assertEquals(5000, result.get(0).getTextContent().length());
            assertEquals(5000, result.get(1).getTextContent().length());
            assertEquals(5000, result.get(2).getTextContent().length());
        }

        @Test
        @DisplayName("空消息列表返回空列表")
        void emptyMessagesReturnsEmpty() {
            MicroCompactor micro = new MicroCompactor(500);
            List<ConversationMessage> result = micro.compact(new ArrayList<>());
            assertTrue(result.isEmpty());
        }
    }

    // ==================== SnipCompactor 边界 ====================

    @Nested
    @DisplayName("SnipCompactor 边界条件")
    class SnipBoundaryTests {

        @Test
        @DisplayName("SYSTEM 消息永远保留")
        void systemMessagesAlwaysKept() {
            List<ConversationMessage> messages = new ArrayList<>(List.of(
                    msg(MessageRole.SYSTEM, "你是助手"),
                    msg(MessageRole.USER, "q1"),
                    toolMsg("旧结果"),
                    msg(MessageRole.ASSISTANT, "a1"),
                    msg(MessageRole.USER, "q2"),
                    msg(MessageRole.ASSISTANT, "a2"),
                    msg(MessageRole.USER, "q3"),
                    msg(MessageRole.ASSISTANT, "a3")
            ));

            SnipCompactor snip = new SnipCompactor(1);
            List<ConversationMessage> result = snip.compact(messages);

            assertTrue(result.stream().anyMatch(
                    m -> m.getRole() == MessageRole.SYSTEM && m.getTextContent().equals("你是助手")));
        }

        @Test
        @DisplayName("keepRecentRounds=0 时删除所有 TOOL 消息")
        void zeroKeepRoundsRemovesAllTools() {
            List<ConversationMessage> messages = new ArrayList<>(List.of(
                    msg(MessageRole.USER, "q1"),
                    toolMsg("tool-1"),
                    msg(MessageRole.ASSISTANT, "a1"),
                    msg(MessageRole.USER, "q2"),
                    toolMsg("tool-2"),
                    msg(MessageRole.ASSISTANT, "a2")
            ));

            SnipCompactor snip = new SnipCompactor(0);
            List<ConversationMessage> result = snip.compact(messages);

            long toolCount = result.stream().filter(m -> m.getRole() == MessageRole.TOOL).count();
            assertEquals(0, toolCount);
            long userCount = result.stream().filter(m -> m.getRole() == MessageRole.USER).count();
            assertEquals(2, userCount);
        }

        @Test
        @DisplayName("只有一轮对话时不删除")
        void singleRoundNoSnip() {
            List<ConversationMessage> messages = new ArrayList<>(List.of(
                    msg(MessageRole.USER, "hello"),
                    toolMsg("result"),
                    msg(MessageRole.ASSISTANT, "done")
            ));

            SnipCompactor snip = new SnipCompactor(1);
            List<ConversationMessage> result = snip.compact(messages);

            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("无 TOOL 消息时返回所有消息")
        void noToolMessagesReturnsAll() {
            List<ConversationMessage> messages = new ArrayList<>(List.of(
                    msg(MessageRole.USER, "hello"),
                    msg(MessageRole.ASSISTANT, "hi"),
                    msg(MessageRole.USER, "how are you"),
                    msg(MessageRole.ASSISTANT, "fine")
            ));

            SnipCompactor snip = new SnipCompactor(1);
            List<ConversationMessage> result = snip.compact(messages);

            assertEquals(4, result.size());
        }
    }

    // ==================== CompactionChain 边界 ====================

    @Nested
    @DisplayName("CompactionChain 边界条件")
    class ChainBoundaryTests {

        @Test
        @DisplayName("空 chain（无阶段）原样返回消息")
        void emptyChainReturnsMessagesUnchanged() {
            List<ConversationMessage> messages = new ArrayList<>(List.of(
                    msg(MessageRole.USER, "hello"),
                    toolMsg("x".repeat(5000))
            ));

            CompactionChain chain = new CompactionChain();
            List<ConversationMessage> result = chain.compact(messages);

            assertEquals(2, result.size());
            assertEquals(5000, result.get(1).getTextContent().length());
        }

        @Test
        @DisplayName("单阶段 chain 等价于直接调用该阶段")
        void singleStageChain() {
            String large = "x".repeat(2000);
            List<ConversationMessage> messages = new ArrayList<>(List.of(toolMsg(large)));

            MicroCompactor micro = new MicroCompactor(500);
            CompactionChain chain = new CompactionChain(micro);

            List<ConversationMessage> directResult = micro.compact(new ArrayList<>(messages));
            List<ConversationMessage> chainResult = chain.compact(new ArrayList<>(messages));

            assertEquals(directResult.get(0).getTextContent(), chainResult.get(0).getTextContent());
        }

        @Test
        @DisplayName("阶段执行顺序：Snip 先删除，Micro 再截断存活的 TOOL")
        void stageOrderMatters() {
            String largeContent = "y".repeat(3000);
            List<ConversationMessage> messages = new ArrayList<>(List.of(
                    msg(MessageRole.USER, "q1"),
                    toolMsg("旧小结果"),          // 旧的小 TOOL - Snip 应删除
                    msg(MessageRole.ASSISTANT, "a1"),
                    msg(MessageRole.USER, "q2"),
                    toolMsg(largeContent),        // 新的大 TOOL - Snip 保留, Micro 截断
                    msg(MessageRole.ASSISTANT, "a2"),
                    msg(MessageRole.USER, "q3"),
                    msg(MessageRole.ASSISTANT, "a3")
            ));

            CompactionChain chain = new CompactionChain(
                    new SnipCompactor(2),
                    new MicroCompactor(500)
            );
            List<ConversationMessage> result = chain.compact(messages);

            long toolCount = result.stream().filter(m -> m.getRole() == MessageRole.TOOL).count();
            assertEquals(1, toolCount, "Snip should remove old TOOL, keep recent one");

            String survivingTool = result.stream()
                    .filter(m -> m.getRole() == MessageRole.TOOL)
                    .findFirst().get().getTextContent();
            assertTrue(survivingTool.length() < 3000, "Micro should truncate the surviving TOOL");
            assertTrue(survivingTool.contains("[...snipped"), "Should contain snip marker");
        }
    }
}
