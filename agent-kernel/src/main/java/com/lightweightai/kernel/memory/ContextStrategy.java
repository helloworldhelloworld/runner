package com.lightweightai.kernel.memory;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ModelCapability;

import java.util.List;

/**
 * Strategy for optimizing conversation context to fit model limitations.
 * Different strategies: sliding window, summarization, semantic retrieval, etc.
 */
public interface ContextStrategy {

    /**
     * Optimize message list for a given model
     *
     * @param messages            Original messages
     * @param model               Target model capability
     * @param reservedTokens      Tokens to reserve for response
     * @return Optimized message list
     */
    List<ConversationMessage> optimize(
            List<ConversationMessage> messages,
            ModelCapability model,
            int reservedTokens
    );

    /**
     * Get strategy name
     *
     * @return Strategy identifier (e.g., "sliding", "summarization")
     */
    String getName();
}
