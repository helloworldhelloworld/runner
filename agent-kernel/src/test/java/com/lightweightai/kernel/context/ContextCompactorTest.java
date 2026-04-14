package com.lightweightai.kernel.context;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ContextCompactor - 上下文压缩")
class ContextCompactorTest {

    private ConversationMessage msg(MessageRole role, String text) {
        return ConversationMessage.builder().role(role).textContent(text).build();
    }

    private ConversationMessage toolMsg(String text) {
        return ConversationMessage.builder().role(MessageRole.TOOL).textContent(text).build();
    }

    @Nested
    @DisplayName("SnipCompactor - 删除旧工具结果")
    class SnipTests {

        @Test
        @DisplayName("保留最近 N 轮的 TOOL 消息，删除更早的")
        void snipOldToolMessages() {
            List<ConversationMessage> messages = new ArrayList<>(List.of(
                    msg(MessageRole.USER, "问题1"),
                    msg(MessageRole.ASSISTANT, "回答1"),
                    toolMsg("旧工具结果1"),  // 应被删除
                    toolMsg("旧工具结果2"),  // 应被删除
                    msg(MessageRole.USER, "问题2"),
                    msg(MessageRole.ASSISTANT, "回答2"),
                    toolMsg("新工具结果"),    // 应保留（最近 1 轮内）
                    msg(MessageRole.USER, "问题3"),
                    msg(MessageRole.ASSISTANT, "回答3")
            ));

            SnipCompactor snip = new SnipCompactor(2); // 保留最近 2 轮（问题2+问题3 的 TOOL 保留）
            List<ConversationMessage> result = snip.compact(messages);

            // 旧的 TOOL 消息应被删除
            long toolCount = result.stream().filter(m -> m.getRole() == MessageRole.TOOL).count();
            assertEquals(1, toolCount, "Should keep only 1 recent TOOL message");

            // USER 和 ASSISTANT 消息不受影响
            long userCount = result.stream().filter(m -> m.getRole() == MessageRole.USER).count();
            assertEquals(3, userCount);
        }

        @Test
        @DisplayName("不足 N 轮时不删除任何消息")
        void noSnipWhenFewMessages() {
            List<ConversationMessage> messages = new ArrayList<>(List.of(
                    msg(MessageRole.USER, "问题"),
                    toolMsg("结果"),
                    msg(MessageRole.ASSISTANT, "回答")
            ));

            SnipCompactor snip = new SnipCompactor(5);
            List<ConversationMessage> result = snip.compact(messages);
            assertEquals(3, result.size());
        }
    }

    @Nested
    @DisplayName("MicroCompactor - 压缩大 tool_result")
    class MicroTests {

        @Test
        @DisplayName("超过阈值的 TOOL 消息被截断")
        void truncateLargeToolResult() {
            String largeContent = "x".repeat(5000);
            List<ConversationMessage> messages = new ArrayList<>(List.of(
                    msg(MessageRole.USER, "问题"),
                    toolMsg(largeContent),
                    msg(MessageRole.ASSISTANT, "回答")
            ));

            MicroCompactor micro = new MicroCompactor(500); // 阈值 500 字符
            List<ConversationMessage> result = micro.compact(messages);

            // TOOL 消息应被压缩
            String toolContent = result.stream()
                    .filter(m -> m.getRole() == MessageRole.TOOL)
                    .findFirst().get().getTextContent();
            assertTrue(toolContent.length() < 5000, "Should be compressed");
            assertTrue(toolContent.contains("[...snipped"), "Should contain snip marker");
        }

        @Test
        @DisplayName("小于阈值的 TOOL 消息不变")
        void noTruncateSmallToolResult() {
            List<ConversationMessage> messages = new ArrayList<>(List.of(
                    msg(MessageRole.USER, "问题"),
                    toolMsg("小结果"),
                    msg(MessageRole.ASSISTANT, "回答")
            ));

            MicroCompactor micro = new MicroCompactor(500);
            List<ConversationMessage> result = micro.compact(messages);
            assertEquals("小结果", result.get(1).getTextContent());
        }
    }

    @Nested
    @DisplayName("CompactionChain - 链式压缩")
    class ChainTests {

        @Test
        @DisplayName("Snip + Micro 链式执行")
        void chainSnipThenMicro() {
            String largeContent = "y".repeat(3000);
            List<ConversationMessage> messages = new ArrayList<>(List.of(
                    msg(MessageRole.SYSTEM, "你是助手"),
                    msg(MessageRole.USER, "问题1"),
                    msg(MessageRole.ASSISTANT, "回答1"),
                    toolMsg("旧工具结果"),     // Snip 应删除
                    msg(MessageRole.USER, "问题2"),
                    toolMsg(largeContent),      // Micro 应压缩
                    msg(MessageRole.ASSISTANT, "回答2"),
                    msg(MessageRole.USER, "问题3"),
                    msg(MessageRole.ASSISTANT, "回答3")
            ));

            CompactionChain chain = new CompactionChain(
                    new SnipCompactor(2),
                    new MicroCompactor(500)
            );
            List<ConversationMessage> result = chain.compact(messages);

            // 旧工具结果被 snip
            long toolCount = result.stream().filter(m -> m.getRole() == MessageRole.TOOL).count();
            assertTrue(toolCount <= 1, "Old TOOL should be snipped");

            // 大工具结果被 micro 压缩
            result.stream()
                    .filter(m -> m.getRole() == MessageRole.TOOL)
                    .forEach(m -> assertTrue(m.getTextContent().length() < 3000,
                            "Large TOOL should be micro-compressed"));

            // SYSTEM 消息始终保留
            assertTrue(result.stream().anyMatch(m -> m.getRole() == MessageRole.SYSTEM));
        }
    }
}
