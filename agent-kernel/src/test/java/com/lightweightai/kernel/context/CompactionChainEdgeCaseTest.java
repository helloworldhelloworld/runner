package com.lightweightai.kernel.context;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CompactionChain - 边界与链式场景")
class CompactionChainEdgeCaseTest {

    private ConversationMessage msg(MessageRole role, String text) {
        return ConversationMessage.builder().role(role).textContent(text).build();
    }

    @Test
    @DisplayName("空 chain 原样返回消息")
    void emptyChainReturnsOriginal() {
        CompactionChain chain = new CompactionChain();
        List<ConversationMessage> messages = List.of(
                msg(MessageRole.USER, "hello"),
                msg(MessageRole.ASSISTANT, "hi"));

        List<ConversationMessage> result = chain.compact(messages);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("单 stage chain 等同直接调用该 stage")
    void singleStageChain() {
        String large = "x".repeat(3000);
        List<ConversationMessage> messages = List.of(
                msg(MessageRole.USER, "q"),
                ConversationMessage.builder().role(MessageRole.TOOL).textContent(large).build());

        MicroCompactor micro = new MicroCompactor(500);
        CompactionChain chain = new CompactionChain(micro);

        List<ConversationMessage> directResult = micro.compact(messages);
        List<ConversationMessage> chainResult = chain.compact(messages);

        assertEquals(directResult.size(), chainResult.size());
        assertEquals(
                directResult.get(1).getTextContent(),
                chainResult.get(1).getTextContent());
    }

    @Test
    @DisplayName("空消息列表不报错")
    void emptyMessageListDoesNotThrow() {
        CompactionChain chain = new CompactionChain(new SnipCompactor(3), new MicroCompactor(500));
        List<ConversationMessage> result = chain.compact(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("三个 stage 按序执行")
    void threeStagesExecuteInOrder() {
        var counter = new java.util.concurrent.atomic.AtomicInteger();
        ContextCompactor stage1 = msgs -> { counter.incrementAndGet(); return msgs; };
        ContextCompactor stage2 = msgs -> { counter.incrementAndGet(); return msgs; };
        ContextCompactor stage3 = msgs -> { counter.incrementAndGet(); return msgs; };

        CompactionChain chain = new CompactionChain(stage1, stage2, stage3);
        chain.compact(List.of(msg(MessageRole.USER, "test")));

        assertEquals(3, counter.get());
    }
}
