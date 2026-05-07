package com.lightweightai.kernel.context;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CompactionChain - 链式压缩独立测试")
class CompactionChainTest {

    private ConversationMessage msg(MessageRole role, String text) {
        return ConversationMessage.builder().role(role).textContent(text).build();
    }

    @Test
    @DisplayName("空 chain → 消息不变")
    void emptyChainPassthrough() {
        CompactionChain chain = new CompactionChain();
        List<ConversationMessage> messages = List.of(
                msg(MessageRole.USER, "hello"),
                msg(MessageRole.ASSISTANT, "hi")
        );

        List<ConversationMessage> result = chain.compact(new ArrayList<>(messages));
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("单 stage → 等同于直接调用该 compactor")
    void singleStage() {
        CompactionChain chain = new CompactionChain(new MicroCompactor(10));
        List<ConversationMessage> messages = new ArrayList<>(List.of(
                msg(MessageRole.TOOL, "x".repeat(100))
        ));

        List<ConversationMessage> result = chain.compact(messages);
        assertTrue(result.get(0).getTextContent().length() < 100);
    }

    @Test
    @DisplayName("多 stage → 按序执行，前一 stage 输出作为后一 stage 输入")
    void stagesExecuteInOrder() {
        AtomicInteger order = new AtomicInteger(0);

        ContextCompactor stage1 = msgs -> {
            assertEquals(0, order.getAndIncrement());
            return msgs;
        };
        ContextCompactor stage2 = msgs -> {
            assertEquals(1, order.getAndIncrement());
            return msgs;
        };
        ContextCompactor stage3 = msgs -> {
            assertEquals(2, order.getAndIncrement());
            return msgs;
        };

        CompactionChain chain = new CompactionChain(stage1, stage2, stage3);
        chain.compact(List.of(msg(MessageRole.USER, "test")));

        assertEquals(3, order.get());
    }

    @Test
    @DisplayName("stage 可以增减消息数量")
    void stageCanFilterMessages() {
        ContextCompactor filterTool = msgs -> {
            List<ConversationMessage> filtered = new ArrayList<>();
            for (ConversationMessage m : msgs) {
                if (m.getRole() != MessageRole.TOOL) {
                    filtered.add(m);
                }
            }
            return filtered;
        };

        CompactionChain chain = new CompactionChain(filterTool);
        List<ConversationMessage> messages = new ArrayList<>(List.of(
                msg(MessageRole.USER, "q"),
                msg(MessageRole.TOOL, "tool result"),
                msg(MessageRole.ASSISTANT, "a")
        ));

        List<ConversationMessage> result = chain.compact(messages);
        assertEquals(2, result.size());
        assertTrue(result.stream().noneMatch(m -> m.getRole() == MessageRole.TOOL));
    }

    @Test
    @DisplayName("Snip + Micro 经典组合 — Snip 先删旧 TOOL，Micro 再压缩大 TOOL")
    void snipThenMicroCombo() {
        CompactionChain chain = new CompactionChain(
                new SnipCompactor(1),
                new MicroCompactor(50)
        );

        List<ConversationMessage> messages = new ArrayList<>(List.of(
                msg(MessageRole.USER, "old question"),
                msg(MessageRole.ASSISTANT, "old answer"),
                msg(MessageRole.TOOL, "old tool result"),    // should be snipped
                msg(MessageRole.USER, "new question"),
                msg(MessageRole.TOOL, "y".repeat(200)),       // should be micro-compressed
                msg(MessageRole.ASSISTANT, "new answer")
        ));

        List<ConversationMessage> result = chain.compact(messages);

        long toolCount = result.stream().filter(m -> m.getRole() == MessageRole.TOOL).count();
        assertTrue(toolCount <= 1, "Old tool should be snipped");

        result.stream()
                .filter(m -> m.getRole() == MessageRole.TOOL)
                .forEach(m -> assertTrue(m.getTextContent().length() < 200,
                        "Remaining tool should be micro-compressed"));
    }

    @Test
    @DisplayName("实现 ContextCompactor 接口")
    void implementsInterface() {
        CompactionChain chain = new CompactionChain();
        assertTrue(chain instanceof ContextCompactor);
    }
}
