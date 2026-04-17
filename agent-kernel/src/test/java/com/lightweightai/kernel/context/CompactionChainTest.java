package com.lightweightai.kernel.context;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompactionChainTest {

    private static ConversationMessage msg(MessageRole role, String text) {
        return ConversationMessage.builder().role(role).textContent(text).build();
    }

    @Test
    void emptyChainReturnsOriginal() {
        CompactionChain chain = new CompactionChain();
        List<ConversationMessage> msgs = List.of(msg(MessageRole.USER, "hi"));
        assertSame(msgs, chain.compact(msgs));
    }

    @Test
    void singleStageApplied() {
        ContextCompactor dropTools = messages -> {
            List<ConversationMessage> out = new ArrayList<>();
            for (ConversationMessage m : messages) {
                if (m.getRole() != MessageRole.TOOL) out.add(m);
            }
            return out;
        };

        CompactionChain chain = new CompactionChain(dropTools);
        List<ConversationMessage> input = List.of(
                msg(MessageRole.USER, "q"),
                msg(MessageRole.TOOL, "result"),
                msg(MessageRole.ASSISTANT, "a")
        );

        List<ConversationMessage> result = chain.compact(input);
        assertEquals(2, result.size());
        assertEquals(MessageRole.USER, result.get(0).getRole());
        assertEquals(MessageRole.ASSISTANT, result.get(1).getRole());
    }

    @Test
    void multipleStagesAppliedInOrder() {
        ContextCompactor addMarker = messages -> {
            List<ConversationMessage> out = new ArrayList<>(messages);
            out.add(msg(MessageRole.SYSTEM, "marker-1"));
            return out;
        };
        ContextCompactor countMessages = messages -> {
            List<ConversationMessage> out = new ArrayList<>(messages);
            out.add(msg(MessageRole.SYSTEM, "count=" + messages.size()));
            return out;
        };

        CompactionChain chain = new CompactionChain(addMarker, countMessages);
        List<ConversationMessage> input = List.of(msg(MessageRole.USER, "hi"));

        List<ConversationMessage> result = chain.compact(input);
        assertEquals(3, result.size());
        assertEquals("marker-1", result.get(1).getTextContent());
        assertEquals("count=2", result.get(2).getTextContent());
    }

    @Test
    void stageOrderMatters() {
        ContextCompactor dropFirst = messages -> messages.subList(1, messages.size());
        ContextCompactor addSuffix = messages -> {
            List<ConversationMessage> out = new ArrayList<>(messages);
            out.add(msg(MessageRole.SYSTEM, "end"));
            return out;
        };

        List<ConversationMessage> input = List.of(
                msg(MessageRole.USER, "a"),
                msg(MessageRole.USER, "b")
        );

        CompactionChain orderA = new CompactionChain(dropFirst, addSuffix);
        assertEquals(2, orderA.compact(input).size());
        assertEquals("b", orderA.compact(input).get(0).getTextContent());

        CompactionChain orderB = new CompactionChain(addSuffix, dropFirst);
        assertEquals(2, orderB.compact(input).size());
        assertEquals("b", orderB.compact(input).get(0).getTextContent());
    }

    @Test
    void emptyMessageListPassedThrough() {
        ContextCompactor identity = messages -> messages;
        CompactionChain chain = new CompactionChain(identity);
        List<ConversationMessage> result = chain.compact(List.of());
        assertTrue(result.isEmpty());
    }
}
