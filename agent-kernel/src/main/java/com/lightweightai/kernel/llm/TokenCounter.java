package com.lightweightai.kernel.llm;

import java.util.List;

/**
 * Token counting interface - different models have different tokenization.
 * Implementations may use exact tokenizers or approximations.
 */
public interface TokenCounter {

    /**
     * Count tokens in a single message
     *
     * @param message The message to count
     * @return Estimated token count
     */
    int countTokens(ConversationMessage message);

    /**
     * Count tokens in plain text
     *
     * @param text The text to count
     * @return Estimated token count
     */
    int countTokens(String text);

    /**
     * Count tokens in a list of messages
     *
     * @param messages Messages to count
     * @return Total estimated token count
     */
    default int countTokens(List<ConversationMessage> messages) {
        return messages.stream()
                .mapToInt(this::countTokens)
                .sum();
    }
}
