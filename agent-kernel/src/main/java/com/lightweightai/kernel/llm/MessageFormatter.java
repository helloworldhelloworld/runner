package com.lightweightai.kernel.llm;

import java.util.List;

/**
 * Converts between unified message format and model-specific API formats.
 * Different LLM providers have different message structures.
 */
public interface MessageFormatter {

    /**
     * Format messages for the specific LLM provider's API
     *
     * @param messages Unified conversation messages
     * @return API-specific format (typically a List or Map structure)
     */
    Object formatForAPI(List<ConversationMessage> messages);

    /**
     * Parse API response back to unified message format
     *
     * @param apiResponse API-specific response object
     * @return Unified conversation message
     */
    ConversationMessage parseFromAPI(Object apiResponse);
}
