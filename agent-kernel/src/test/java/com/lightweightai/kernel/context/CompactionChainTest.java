package com.lightweightai.kernel.context;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CompactionChain - 链式压缩")
class CompactionChainTest {

    private ConversationMessage msg(MessageRole role, String text) {
        return ConversationMessage.builder().role(role).textContent(text).build();
    }

    private ConversationMessage toolMsg(String text) {
        return msg(MessageRole.TOOL, text);
    }

    @Nested
    @DisplayName("链式执行顺序")
    class ChainingOrder {

        @Test
        @DisplayName("stages 按声明顺序执行，前一个的输出是后一个的输入")
        void stagesExecuteInOrder() {
            AtomicInteger callOrder = new AtomicInteger(0);

            ContextCompactor first = messages -> {
                assertEquals(0, callOrder.getAndIncrement(), "First stage should be called first");
                List<ConversationMessage> result = new ArrayList<>(messages);
                result.add(msg(MessageRole.SYSTEM, "added-by-first"));
                return result;
            };

            ContextCompactor second = messages -> {
                assertEquals(1, callOrder.getAndIncrement(), "Second stage should be called second");
                assertTrue(messages.stream().anyMatch(
                        m -> "added-by-first".equals(m.getTextContent())),
                        "Second stage should see output of first stage");
                return messages;
            };

            CompactionChain chain = new CompactionChain(first, second);
            chain.compact(List.of(msg(MessageRole.USER, "hello")));

            assertEquals(2, callOrder.get(), "Both stages should have been called");
        }

        @Test
        @DisplayName("Snip 先删旧 TOOL，Micro 再截断剩余大 TOOL")
        void snipThenMicroEndToEnd() {
            List<ConversationMessage> messages = List.of(
                    msg(MessageRole.USER, "q1"),
                    toolMsg("old-tool-content"),                // old round - snipped
                    msg(MessageRole.ASSISTANT, "a1"),
                    msg(MessageRole.USER, "q2"),
                    toolMsg("x".repeat(2000)),                  // recent round - micro-truncated
                    msg(MessageRole.ASSISTANT, "a2"),
                    msg(MessageRole.USER, "q3"),
                    msg(MessageRole.ASSISTANT, "a3")
            );

            CompactionChain chain = new CompactionChain(
                    new SnipCompactor(2),
                    new MicroCompactor(500)
            );

            List<ConversationMessage> result = chain.compact(messages);

            List<ConversationMessage> tools = result.stream()
                    .filter(m -> m.getRole() == MessageRole.TOOL)
                    .toList();

            assertEquals(1, tools.size(), "Only the recent TOOL should remain");
            assertTrue(tools.get(0).getTextContent().contains("[...snipped"),
                    "Remaining TOOL should be micro-truncated");
            assertTrue(tools.get(0).getTextContent().length() < 2000,
                    "Truncated content should be shorter than original");
        }
    }

    @Nested
    @DisplayName("边界情况")
    class EdgeCases {

        @Test
        @DisplayName("空 chain（无 stages）原样返回消息")
        void emptyChainPassesThrough() {
            CompactionChain chain = new CompactionChain();
            List<ConversationMessage> messages = List.of(
                    msg(MessageRole.USER, "hello"),
                    toolMsg("tool result")
            );

            List<ConversationMessage> result = chain.compact(messages);

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("单 stage chain 等价于直接调用该 stage")
        void singleStageChain() {
            MicroCompactor micro = new MicroCompactor(100, 10);
            CompactionChain chain = new CompactionChain(micro);

            String largeText = "y".repeat(500);
            List<ConversationMessage> messages = List.of(toolMsg(largeText));

            List<ConversationMessage> chainResult = chain.compact(messages);
            List<ConversationMessage> directResult = micro.compact(messages);

            assertEquals(directResult.get(0).getTextContent(),
                    chainResult.get(0).getTextContent());
        }

        @Test
        @DisplayName("空消息列表通过所有 stages 不抛异常")
        void emptyMessagesPassThrough() {
            CompactionChain chain = new CompactionChain(
                    new SnipCompactor(2),
                    new MicroCompactor(500)
            );

            List<ConversationMessage> result = chain.compact(List.of());
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("三级链式压缩")
    class ThreeStageChain {

        @Test
        @DisplayName("三个 stage 依次执行，每个看到前一个的输出")
        void threeStagesChained() {
            List<Integer> order = new ArrayList<>();

            ContextCompactor s1 = messages -> { order.add(1); return messages; };
            ContextCompactor s2 = messages -> { order.add(2); return messages; };
            ContextCompactor s3 = messages -> { order.add(3); return messages; };

            CompactionChain chain = new CompactionChain(s1, s2, s3);
            chain.compact(List.of(msg(MessageRole.USER, "test")));

            assertEquals(List.of(1, 2, 3), order);
        }
    }
}
