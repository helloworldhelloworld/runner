package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TokenCounter - default countTokens(List) method")
class TokenCounterDefaultMethodTest {

    @Test
    @DisplayName("default list method sums per-message counts")
    void sumsPerMessageCounts() {
        TokenCounter counter = new TokenCounter() {
            @Override
            public int countTokens(ConversationMessage message) {
                return message.getTextContent().split("\\s+").length;
            }

            @Override
            public int countTokens(String text) {
                return text.split("\\s+").length;
            }
        };

        ConversationMessage msg1 = ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.USER)
            .textContent("hello world")
            .build();
        ConversationMessage msg2 = ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.ASSISTANT)
            .textContent("how are you today")
            .build();

        int total = counter.countTokens(List.of(msg1, msg2));
        assertEquals(6, total);
    }

    @Test
    @DisplayName("empty message list returns 0")
    void emptyListReturnsZero() {
        TokenCounter counter = new TokenCounter() {
            @Override
            public int countTokens(ConversationMessage message) {
                return 10;
            }

            @Override
            public int countTokens(String text) {
                return text.length();
            }
        };

        assertEquals(0, counter.countTokens(List.of()));
    }

    @Test
    @DisplayName("single message list returns that message's count")
    void singleMessageList() {
        TokenCounter counter = new TokenCounter() {
            @Override
            public int countTokens(ConversationMessage message) {
                return 42;
            }

            @Override
            public int countTokens(String text) {
                return 0;
            }
        };

        ConversationMessage msg = ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.USER)
            .textContent("test")
            .build();

        assertEquals(42, counter.countTokens(List.of(msg)));
    }
}
