package com.lightweightai.kernel.memory;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ModelCapability;

import java.util.List;

/**
 * Manages conversation context and message history.
 * Handles context optimization for different models.
 */
public interface ConversationContext {

    /**
     * Get the conversation ID
     *
     * @return Conversation identifier
     */
    String getId();

    /**
     * Add a message to the conversation
     *
     * @param message Message to add
     */
    void addMessage(ConversationMessage message);

    /**
     * Get all messages in the conversation
     *
     * @return List of messages
     */
    List<ConversationMessage> getMessages();

    /**
     * Prepare messages for a specific model, applying optimization strategies
     *
     * @param model Model capability to optimize for
     * @return Optimized message list
     */
    List<ConversationMessage> prepareForModel(ModelCapability model);

    /**
     * Estimate token count for current context
     *
     * @param model Model to count tokens for
     * @return Estimated token count
     */
    int estimateTokens(ModelCapability model);

    /**
     * Clear all messages
     */
    void clear();

    /**
     * Create a branch from a specific message
     *
     * @param fromMessageId Message ID to branch from
     * @return New conversation context branch
     */
    ConversationContext branch(String fromMessageId);

    /**
     * Create a snapshot of current state
     *
     * @return Snapshot that can be restored later
     */
    ConversationSnapshot snapshot();

    /**
     * Restore from a snapshot
     *
     * @param snapshot Snapshot to restore
     */
    void restore(ConversationSnapshot snapshot);
}
