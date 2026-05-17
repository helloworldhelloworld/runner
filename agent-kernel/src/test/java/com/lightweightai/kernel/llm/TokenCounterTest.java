package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TokenCounter — counting contract and default methods")
class TokenCounterTest {

    @Test
    @DisplayName("countTokens(List) defaults to sum of individual message counts")
    void listCountIsSumOfIndividual() {
        TokenCounter counter = new SimpleTokenCounter();

        ConversationMessage msg1 = ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("hello world")
                .build();
        ConversationMessage msg2 = ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent("hi there friend")
                .build();

        int individual = counter.countTokens(msg1) + counter.countTokens(msg2);
        int listCount = counter.countTokens(List.of(msg1, msg2));

        assertEquals(individual, listCount);
    }

    @Test
    @DisplayName("countTokens(String) returns consistent token estimate")
    void stringCountReturnsEstimate() {
        TokenCounter counter = new SimpleTokenCounter();

        int count = counter.countTokens("this is a test sentence");
        assertTrue(count > 0);
        assertEquals(5, count); // word-based approximation
    }

    @Test
    @DisplayName("empty string returns zero tokens")
    void emptyStringReturnsZero() {
        TokenCounter counter = new SimpleTokenCounter();
        assertEquals(0, counter.countTokens(""));
    }

    @Test
    @DisplayName("empty message list returns zero tokens")
    void emptyListReturnsZero() {
        TokenCounter counter = new SimpleTokenCounter();
        assertEquals(0, counter.countTokens(List.of()));
    }

    @Test
    @DisplayName("countTokens(message) counts the text content")
    void messageCountMatchesTextCount() {
        TokenCounter counter = new SimpleTokenCounter();

        ConversationMessage msg = ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("one two three")
                .build();

        int msgCount = counter.countTokens(msg);
        int textCount = counter.countTokens("one two three");
        assertEquals(textCount, msgCount);
    }

    private static class SimpleTokenCounter implements TokenCounter {
        @Override
        public int countTokens(ConversationMessage message) {
            return countTokens(message.getTextContent());
        }

        @Override
        public int countTokens(String text) {
            if (text == null || text.isEmpty()) return 0;
            return text.split("\\s+").length;
        }
    }
}
